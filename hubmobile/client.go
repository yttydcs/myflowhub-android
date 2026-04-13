package hubmobile

// Context: This file supports the Android app or gomobile host flow around client.

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

func onClientError(err error) {
	if err == nil {
		return
	}
	clientConnected.Store(false)
	storeLastError(err)
	logWarn("client session error", "err", err.Error())
}

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

func IsConnected() bool {
	return clientConnected.Load()
}

func LastAddr() string {
	clientMu.Lock()
	addr := clientAddr
	clientMu.Unlock()
	return addr
}

func AuthState() string {
	authMu.Lock()
	st := authState
	authMu.Unlock()
	raw, _ := json.Marshal(st)
	return string(raw)
}

func ClearAuth() {
	authMu.Lock()
	authState = authSnapshot{}
	authMu.Unlock()
	logInfo("auth state cleared")
}

func GetSelfNodeID() string {
	authMu.Lock()
	id := authState.NodeID
	authMu.Unlock()
	return fmt.Sprintf("%d", id)
}

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

func cloneStrings(in []string) []string {
	if len(in) == 0 {
		return nil
	}
	out := make([]string, len(in))
	copy(out, in)
	return out
}
