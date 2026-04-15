package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `client` 相关的逻辑。

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	core "github.com/yttydcs/myflowhub-core"
	"github.com/yttydcs/myflowhub-core/header"
	protoauth "github.com/yttydcs/myflowhub-proto/protocol/auth"
	sdkawait "github.com/yttydcs/myflowhub-sdk/await"
	"github.com/yttydcs/myflowhub-sdk/session"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultClientTimeout = 8 * time.Second

type authSnapshot struct {
	DeviceID string   `json:"device_id,omitempty"`
	NodeID   uint32   `json:"node_id,omitempty"`
	HubID    uint32   `json:"hub_id,omitempty"`
	Role     string   `json:"role,omitempty"`
	Perms    []string `json:"perms,omitempty"`

	LoggedIn     bool   `json:"logged_in"`
	LastAction   string `json:"last_action,omitempty"`
	LastMessage  string `json:"last_message,omitempty"`
	LastUnixTime int64  `json:"last_unix_time,omitempty"`
}

var (
	clientMu   sync.Mutex
	client     *sdkawait.Client
	clientAddr string

	clientConnected atomic.Bool

	authMu    sync.Mutex
	authState authSnapshot
)

// ensureClient 懒初始化全局 await client，并挂上未匹配帧/观测帧回调。
func ensureClient() *sdkawait.Client {
	clientMu.Lock()
	defer clientMu.Unlock()
	if client != nil {
		return client
	}
	c := sdkawait.NewClient(context.Background(), onUnmatchedFrame, onClientError)
	c.SetOnFrame(onObservedFrame)
	client = c
	return c
}

// onUnmatchedFrame 只做轻量日志和 UI 事件采集，避免在未匹配流量上做重解码。
func onUnmatchedFrame(hdr core.IHeader, payload []byte) {
	// Keep it light: avoid expensive decoding; show header metadata and a short payload preview.
	if hdr == nil {
		return
	}
	if len(payload) == 0 {
		return
	}
	preview := payload
	truncated := false
	if len(preview) > 256 {
		preview = preview[:256]
		truncated = true
	}
	logInfo("rx unmatched",
		"major", hdr.Major(),
		"sub", hdr.SubProto(),
		"src", hdr.SourceID(),
		"tgt", hdr.TargetID(),
		"len", len(payload),
		"preview", string(preview),
		"truncated", truncated,
	)

	// Capture lightweight notify events for UI auto-update (VarStore only).
	captureVarStoreUnmatchedFrame(hdr, payload)
	captureTopicBusUnmatchedFrame(hdr, payload)
}

// onClientError 把底层会话错误收敛到 bridge 全局状态，供 UI 查询。
func onClientError(err error) {
	if err == nil {
		return
	}
	clientConnected.Store(false)
	storeLastError(err)
	logWarn("client session error", "err", err.Error())
}

// Connect 建立到目标 Hub 的 await client 连接，并处理地址切换时的旧连接回收。
func Connect(addr string) error {
	addr = strings.TrimSpace(addr)
	if addr == "" {
		return errors.New("addr is required")
	}

	clientMu.Lock()
	prevAddr := clientAddr
	clientMu.Unlock()
	if prevAddr != "" && prevAddr != addr {
		Close()
	}

	c := ensureClient()
	err := c.Connect(addr)
	if err != nil {
		if errors.Is(err, session.ErrAlreadyConnected) {
			clientConnected.Store(true)
			clientMu.Lock()
			clientAddr = addr
			clientMu.Unlock()
			return nil
		}
		storeLastError(err)
		logWarn("client connect failed", "addr", addr, "err", err.Error())
		return err
	}

	clientConnected.Store(true)
	clientMu.Lock()
	clientAddr = addr
	clientMu.Unlock()
	logInfo("client connected", "addr", addr)
	return nil
}

// Close 主动关闭当前 client 会话，并清空连接地址缓存。
func Close() {
	clientMu.Lock()
	c := client
	client = nil
	clientAddr = ""
	clientMu.Unlock()

	if c != nil {
		c.Close()
	}
	clientConnected.Store(false)
	logInfo("client closed")
}

// IsConnected 返回 UI 是否还能继续发起协议请求。
func IsConnected() bool {
	return clientConnected.Load()
}

// LastAddr 返回最近一次成功连接的地址，便于页面回显。
func LastAddr() string {
	clientMu.Lock()
	addr := clientAddr
	clientMu.Unlock()
	return addr
}

// AuthState 以 JSON 形式导出当前认证快照，供 Kotlin 侧直接展示。
func AuthState() string {
	authMu.Lock()
	st := authState
	authMu.Unlock()
	raw, _ := json.Marshal(st)
	return string(raw)
}

// ClearAuth 清空桥接层保存的认证结果，不影响远端真实会话。
func ClearAuth() {
	authMu.Lock()
	authState = authSnapshot{}
	authMu.Unlock()
	logInfo("auth state cleared")
}

// GetSelfNodeID 返回当前认证快照里的 node_id，供其他子协议页面复用。
func GetSelfNodeID() string {
	authMu.Lock()
	id := authState.NodeID
	authMu.Unlock()
	return fmt.Sprintf("%d", id)
}

// Register 走 auth/register，并把成功结果落到 auth 快照中。
func Register(deviceID string) (string, error) {
	deviceID = strings.TrimSpace(deviceID)
	if deviceID == "" {
		return "", errors.New("device_id is required")
	}
	if !IsConnected() {
		return "", errors.New("not connected")
	}

	pub, err := EnsureKeys()
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protoauth.ActionRegister, protoauth.RegisterData{
		DeviceID: deviceID,
		PubKey:   pub,
		NodePub:  pub,
	})
	if err != nil {
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultClientTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protoauth.SubProtoAuth, 0, 0, payload, protoauth.ActionRegisterResp)
	if err != nil {
		storeLastError(err)
		setAuthResult(false, protoauth.ActionRegisterResp, err.Error())
		return "", err
	}

	var data protoauth.RespData
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		setAuthResult(false, protoauth.ActionRegisterResp, err.Error())
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("auth register failed (code=%d)", data.Code)
		}
		storeLastError(errors.New(msg))
		setAuthResult(false, protoauth.ActionRegisterResp, msg)
		return "", errors.New(msg)
	}

	setAuthSnapshot(protoauth.ActionRegisterResp, deviceID, data)
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

// Login 走 auth/login，并在成功后刷新当前设备的 node/hub/role 快照。
func Login(deviceID, nodeID string) (string, error) {
	deviceID = strings.TrimSpace(deviceID)
	if deviceID == "" {
		return "", errors.New("device_id is required")
	}
	n, err := parseUint32("node_id", nodeID)
	if err != nil {
		return "", err
	}
	if n == 0 {
		return "", errors.New("node_id is required")
	}
	if !IsConnected() {
		return "", errors.New("not connected")
	}

	priv, err := getCachedPrivKey()
	if err != nil {
		storeLastError(err)
		return "", err
	}
	ts := nowUnix()
	nonce := generateNonce(12)
	sig, err := signLogin(priv, deviceID, n, ts, nonce)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protoauth.ActionLogin, protoauth.LoginData{
		DeviceID: deviceID,
		NodeID:   n,
		TS:       ts,
		Nonce:    nonce,
		Sig:      sig,
		Alg:      "ES256",
	})
	if err != nil {
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultClientTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protoauth.SubProtoAuth, 0, 0, payload, protoauth.ActionLoginResp)
	if err != nil {
		storeLastError(err)
		setAuthResult(false, protoauth.ActionLoginResp, err.Error())
		return "", err
	}

	var data protoauth.RespData
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		setAuthResult(false, protoauth.ActionLoginResp, err.Error())
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("auth login failed (code=%d)", data.Code)
		}
		storeLastError(errors.New(msg))
		setAuthResult(false, protoauth.ActionLoginResp, msg)
		return "", errors.New(msg)
	}

	setAuthSnapshot(protoauth.ActionLoginResp, deviceID, data)
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

// SendAndAwait 暴露通用命令入口，供协议实验页直接按子协议发请求。
func SendAndAwait(subProto, sourceID, targetID, action, dataJSON, expectAction, timeoutMs string) (string, error) {
	sub, err := parseUint8("sub_proto", subProto)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if sub == 0 {
		return "", errors.New("sub_proto is required")
	}
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		return "", err
	}
	action = strings.TrimSpace(action)
	if action == "" {
		return "", errors.New("action is required")
	}
	expectAction = strings.TrimSpace(expectAction)
	if expectAction == "" {
		expectAction = action + "_resp"
	}

	if !IsConnected() {
		return "", errors.New("not connected")
	}

	dataJSON = strings.TrimSpace(dataJSON)
	if dataJSON == "" {
		dataJSON = "{}"
	}
	rawData := []byte(dataJSON)
	if !json.Valid(rawData) {
		err := errors.New("data_json is invalid json")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(action, json.RawMessage(rawData))
	if err != nil {
		storeLastError(err)
		return "", err
	}

	timeout := defaultClientTimeout
	if n, err := parseInt("timeout_ms", timeoutMs); err != nil {
		storeLastError(err)
		return "", err
	} else if n > 0 {
		timeout = time.Duration(n) * time.Millisecond
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	resp, err := sendAndAwait(ctx, sub, src, tgt, payload, expectAction)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(resp.Message)
	return string(raw), nil
}

// sendAndAwait 负责统一构造 HeaderTcp 命令头，并把 SDK 错误翻译成 UI 友好的文本。
func sendAndAwait(ctx context.Context, sub uint8, src, tgt uint32, payload []byte, expectAction string) (sdkawait.Response, error) {
	c := ensureClient()
	hdr := (&header.HeaderTcp{}).
		WithMajor(header.MajorCmd).
		WithSubProto(sub).
		WithSourceID(src).
		WithTargetID(tgt).
		WithTimestamp(uint32(time.Now().Unix()))

	resp, err := c.SendAndAwait(ctx, hdr, payload, expectAction)
	if err != nil {
		storeLastError(err)
		return sdkawait.Response{}, fmt.Errorf("%s: %w", expectAction, toUIError(err))
	}
	return resp, nil
}

// setAuthSnapshot 在认证成功后原子更新桥接层缓存，后续页面都从这里读取身份。
func setAuthSnapshot(action, deviceID string, data protoauth.RespData) {
	authMu.Lock()
	authState = authSnapshot{
		DeviceID:     deviceID,
		NodeID:       data.NodeID,
		HubID:        data.HubID,
		Role:         strings.TrimSpace(data.Role),
		Perms:        cloneStrings(data.Perms),
		LoggedIn:     true,
		LastAction:   action,
		LastMessage:  strings.TrimSpace(data.Msg),
		LastUnixTime: nowUnix(),
	}
	authMu.Unlock()
	logInfo("auth ok", "action", action, "device", deviceID, "node", data.NodeID, "hub", data.HubID, "role", strings.TrimSpace(data.Role))
}

// setAuthResult 在失败或登出场景记录最近一次认证动作的结果。
func setAuthResult(ok bool, action, msg string) {
	authMu.Lock()
	st := authState
	st.LoggedIn = ok
	st.LastAction = strings.TrimSpace(action)
	st.LastMessage = strings.TrimSpace(msg)
	st.LastUnixTime = nowUnix()
	authState = st
	authMu.Unlock()
}

// cloneStrings 复制权限切片，避免外部复用底层数组。
func cloneStrings(in []string) []string {
	if len(in) == 0 {
		return nil
	}
	out := make([]string, len(in))
	copy(out, in)
	return out
}
