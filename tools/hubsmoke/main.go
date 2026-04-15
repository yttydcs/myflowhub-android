package main

// 本文件实现 Android `hubmobile` 冒烟工具中与 `main` 相关的逻辑。

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/binary"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	headerSize = 32

	magicV2   uint16 = 0x4D48 // "MH"
	versionV2 uint8  = 2

	defaultHopLimit uint8 = 16

	majorOKResp  uint8 = 0
	majorErrResp uint8 = 1
	majorMsg     uint8 = 2
	majorCmd     uint8 = 3

	subprotoManagement uint8 = 1
	subprotoAuth       uint8 = 2

	actionRegister           = "register"
	actionRegisterResp       = "register_resp"
	actionAssistRegisterResp = "assist_register_resp"

	actionNodeEcho     = "node_echo"
	actionNodeEchoResp = "node_echo_resp"

	actionListNodes     = "list_nodes"
	actionListNodesResp = "list_nodes_resp"
)

const maxPayloadLen = 16 * 1024 * 1024

type header struct {
	Magic      uint16
	Ver        uint8
	HdrLen     uint8
	TypeFmt    uint8
	Flags      uint8
	HopLimit   uint8
	RouteFlags uint8
	MsgID      uint32
	Source     uint32
	Target     uint32
	TraceID    uint32
	Timestamp  uint32
	PayloadLen uint32
}

func (h header) major() uint8    { return h.TypeFmt & 0x03 }
func (h header) subProto() uint8 { return (h.TypeFmt >> 2) & 0x3F }

func newHeader(major, sub uint8) header {
	return header{
		Magic:    magicV2,
		Ver:      versionV2,
		HdrLen:   headerSize,
		TypeFmt:  (major & 0x03) | ((sub & 0x3F) << 2),
		HopLimit: defaultHopLimit,
	}
}

func encodeFrame(h header, payload []byte) ([]byte, error) {
	if len(payload) > int(^uint32(0)) {
		return nil, errors.New("payload too large")
	}
	h.PayloadLen = uint32(len(payload))
	if h.HopLimit == 0 {
		h.HopLimit = defaultHopLimit
	}
	if h.Magic == 0 {
		h.Magic = magicV2
	}
	if h.Ver == 0 {
		h.Ver = versionV2
	}
	if h.HdrLen == 0 {
		h.HdrLen = headerSize
	}

	buf := make([]byte, headerSize+len(payload))
	binary.BigEndian.PutUint16(buf[0:2], h.Magic)
	buf[2] = h.Ver
	buf[3] = h.HdrLen
	buf[4] = h.TypeFmt
	buf[5] = h.Flags
	buf[6] = h.HopLimit
	buf[7] = h.RouteFlags
	binary.BigEndian.PutUint32(buf[8:12], h.MsgID)
	binary.BigEndian.PutUint32(buf[12:16], h.Source)
	binary.BigEndian.PutUint32(buf[16:20], h.Target)
	binary.BigEndian.PutUint32(buf[20:24], h.TraceID)
	binary.BigEndian.PutUint32(buf[24:28], h.Timestamp)
	binary.BigEndian.PutUint32(buf[28:32], h.PayloadLen)
	copy(buf[headerSize:], payload)
	return buf, nil
}

func decodeFrame(r io.Reader) (header, []byte, error) {
	var prefix [4]byte
	if _, err := io.ReadFull(r, prefix[:]); err != nil {
		return header{}, nil, err
	}
	magic := binary.BigEndian.Uint16(prefix[0:2])
	ver := prefix[2]
	hdrLen := prefix[3]
	if magic != magicV2 {
		return header{}, nil, fmt.Errorf("invalid magic: 0x%X", magic)
	}
	if ver != versionV2 {
		return header{}, nil, fmt.Errorf("invalid version: %d", ver)
	}
	if hdrLen < headerSize {
		return header{}, nil, fmt.Errorf("invalid header length: %d", hdrLen)
	}
	if hdrLen > 255 {
		return header{}, nil, fmt.Errorf("header too large: %d", hdrLen)
	}

	hdr := make([]byte, hdrLen)
	copy(hdr[:4], prefix[:])
	if _, err := io.ReadFull(r, hdr[4:]); err != nil {
		return header{}, nil, err
	}
	h := header{
		Magic:      magic,
		Ver:        ver,
		HdrLen:     hdrLen,
		TypeFmt:    hdr[4],
		Flags:      hdr[5],
		HopLimit:   hdr[6],
		RouteFlags: hdr[7],
		MsgID:      binary.BigEndian.Uint32(hdr[8:12]),
		Source:     binary.BigEndian.Uint32(hdr[12:16]),
		Target:     binary.BigEndian.Uint32(hdr[16:20]),
		TraceID:    binary.BigEndian.Uint32(hdr[20:24]),
		Timestamp:  binary.BigEndian.Uint32(hdr[24:28]),
		PayloadLen: binary.BigEndian.Uint32(hdr[28:32]),
	}
	if h.HopLimit == 0 {
		h.HopLimit = defaultHopLimit
	}
	if h.PayloadLen == 0 {
		return h, nil, nil
	}
	if h.PayloadLen > maxPayloadLen {
		return header{}, nil, fmt.Errorf("payload too large: %d", h.PayloadLen)
	}
	payload := make([]byte, h.PayloadLen)
	if _, err := io.ReadFull(r, payload); err != nil {
		return header{}, nil, err
	}
	return h, payload, nil
}

type message struct {
	Action string          `json:"action"`
	Data   json.RawMessage `json:"data,omitempty"`
}

func encodeMessage(action string, data any) ([]byte, error) {
	action = strings.TrimSpace(action)
	if action == "" {
		return nil, errors.New("action required")
	}
	wire := struct {
		Action string `json:"action"`
		Data   any    `json:"data,omitempty"`
	}{
		Action: action,
		Data:   data,
	}
	return json.Marshal(wire)
}

func decodeMessage(payload []byte) (message, error) {
	var m message
	if err := json.Unmarshal(payload, &m); err != nil {
		return message{}, err
	}
	m.Action = strings.TrimSpace(m.Action)
	if m.Action == "" {
		return message{}, errors.New("empty action in payload")
	}
	return m, nil
}

func nextUint32NonZero() uint32 {
	var b [4]byte
	if _, err := rand.Read(b[:]); err != nil {
		v := uint32(time.Now().UnixNano())
		if v == 0 {
			v = 1
		}
		return v
	}
	v := binary.BigEndian.Uint32(b[:])
	if v == 0 {
		return 1
	}
	return v
}

func defaultDeviceID() string {
	host, _ := os.Hostname()
	host = strings.TrimSpace(host)
	if host == "" {
		host = "pc"
	}
	host = strings.Map(func(r rune) rune {
		switch {
		case r >= 'a' && r <= 'z':
			return r
		case r >= 'A' && r <= 'Z':
			return r
		case r >= '0' && r <= '9':
			return r
		case r == '-' || r == '_' || r == '.':
			return r
		default:
			return '-'
		}
	}, host)
	return "hubsmoke-" + host
}

func dial(ctx context.Context, addr string, timeout time.Duration) (net.Conn, *bufio.Reader, error) {
	addr = strings.TrimSpace(addr)
	if addr == "" {
		return nil, nil, errors.New("addr required, e.g. 192.168.1.50:9000")
	}
	dialer := net.Dialer{Timeout: timeout}
	conn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return nil, nil, err
	}
	return conn, bufio.NewReader(conn), nil
}

func withDeadline(conn net.Conn, ctx context.Context, fallback time.Duration) {
	if conn == nil {
		return
	}
	if dl, ok := ctx.Deadline(); ok {
		_ = conn.SetDeadline(dl)
		return
	}
	_ = conn.SetDeadline(time.Now().Add(fallback))
}

func sendAndAwait(ctx context.Context, conn net.Conn, reader *bufio.Reader, reqHdr header, payload []byte, wantActions map[string]bool, verbose bool) (header, message, error) {
	if conn == nil || reader == nil {
		return header{}, message{}, errors.New("conn not initialized")
	}
	if reqHdr.MsgID == 0 {
		return header{}, message{}, errors.New("msg_id required")
	}
	if len(payload) == 0 {
		return header{}, message{}, errors.New("payload empty")
	}
	withDeadline(conn, ctx, 8*time.Second)

	frame, err := encodeFrame(reqHdr, payload)
	if err != nil {
		return header{}, message{}, err
	}
	if _, err := conn.Write(frame); err != nil {
		return header{}, message{}, err
	}

	for {
		if ctx.Err() != nil {
			return header{}, message{}, ctx.Err()
		}
		rh, rp, err := decodeFrame(reader)
		if err != nil {
			return header{}, message{}, err
		}
		if verbose {
			fmt.Fprintf(os.Stderr, "recv: major=%d sub=%d msg_id=%d source=%d target=%d payload=%d\n", rh.major(), rh.subProto(), rh.MsgID, rh.Source, rh.Target, len(rp))
		}
		if rh.MsgID != reqHdr.MsgID {
			continue
		}
		msg, err := decodeMessage(rp)
		if err != nil {
			return header{}, message{}, err
		}
		if wantActions != nil && len(wantActions) > 0 && !wantActions[msg.Action] {
			continue
		}
		return rh, msg, nil
	}
}

type registerResp struct {
	Code     int      `json:"code"`
	Msg      string   `json:"msg,omitempty"`
	DeviceID string   `json:"device_id,omitempty"`
	NodeID   uint32   `json:"node_id,omitempty"`
	HubID    uint32   `json:"hub_id,omitempty"`
	Role     string   `json:"role,omitempty"`
	Perms    []string `json:"perms,omitempty"`
}

func doRegister(ctx context.Context, conn net.Conn, reader *bufio.Reader, deviceID string, verbose bool) (registerResp, error) {
	deviceID = strings.TrimSpace(deviceID)
	if deviceID == "" {
		deviceID = defaultDeviceID()
	}
	payload, _ := encodeMessage(actionRegister, map[string]any{
		"device_id": deviceID,
	})
	h := newHeader(majorCmd, subprotoAuth)
	h.MsgID = nextUint32NonZero()
	h.TraceID = nextUint32NonZero()
	h.Timestamp = uint32(time.Now().Unix())
	h.Source = 0
	h.Target = 0

	want := map[string]bool{
		actionRegisterResp:       true,
		actionAssistRegisterResp: true,
	}
	_, msg, err := sendAndAwait(ctx, conn, reader, h, payload, want, verbose)
	if err != nil {
		return registerResp{}, err
	}
	var resp registerResp
	if err := json.Unmarshal(msg.Data, &resp); err != nil {
		return registerResp{}, err
	}
	if resp.Code != 1 || resp.NodeID == 0 {
		return registerResp{}, fmt.Errorf("register failed: code=%d node_id=%d msg=%s", resp.Code, resp.NodeID, strings.TrimSpace(resp.Msg))
	}
	if resp.HubID == 0 {
		resp.HubID = resp.NodeID
	}
	return resp, nil
}

type listNodesResp struct {
	Code  int    `json:"code"`
	Msg   string `json:"msg,omitempty"`
	Nodes []struct {
		NodeID      uint32 `json:"node_id"`
		HasChildren bool   `json:"has_children,omitempty"`
	} `json:"nodes,omitempty"`
}

func doListNodes(ctx context.Context, conn net.Conn, reader *bufio.Reader, srcNodeID, target uint32, verbose bool) (listNodesResp, error) {
	payload, _ := encodeMessage(actionListNodes, map[string]any{})
	h := newHeader(majorCmd, subprotoManagement)
	h.MsgID = nextUint32NonZero()
	h.TraceID = nextUint32NonZero()
	h.Timestamp = uint32(time.Now().Unix())
	h.Source = srcNodeID
	h.Target = target

	want := map[string]bool{actionListNodesResp: true}
	_, msg, err := sendAndAwait(ctx, conn, reader, h, payload, want, verbose)
	if err != nil {
		return listNodesResp{}, err
	}
	var resp listNodesResp
	if err := json.Unmarshal(msg.Data, &resp); err != nil {
		return listNodesResp{}, err
	}
	if resp.Code != 1 {
		return listNodesResp{}, fmt.Errorf("list_nodes failed: code=%d msg=%s", resp.Code, strings.TrimSpace(resp.Msg))
	}
	return resp, nil
}

type echoResp struct {
	Code int    `json:"code"`
	Msg  string `json:"msg,omitempty"`
	Echo string `json:"echo,omitempty"`
}

func doEcho(ctx context.Context, conn net.Conn, reader *bufio.Reader, srcNodeID, target uint32, messageText string, verbose bool) (echoResp, error) {
	messageText = strings.TrimSpace(messageText)
	if messageText == "" {
		return echoResp{}, errors.New("message required")
	}
	payload, _ := encodeMessage(actionNodeEcho, map[string]any{
		"message": messageText,
	})
	h := newHeader(majorCmd, subprotoManagement)
	h.MsgID = nextUint32NonZero()
	h.TraceID = nextUint32NonZero()
	h.Timestamp = uint32(time.Now().Unix())
	h.Source = srcNodeID
	h.Target = target

	want := map[string]bool{actionNodeEchoResp: true}
	_, msg, err := sendAndAwait(ctx, conn, reader, h, payload, want, verbose)
	if err != nil {
		return echoResp{}, err
	}
	var resp echoResp
	if err := json.Unmarshal(msg.Data, &resp); err != nil {
		return echoResp{}, err
	}
	if resp.Code != 1 {
		return echoResp{}, fmt.Errorf("echo failed: code=%d msg=%s", resp.Code, strings.TrimSpace(resp.Msg))
	}
	return resp, nil
}

func usage() {
	fmt.Fprintln(os.Stderr, "hubsmoke：MyFlowHub TCP 协议最小冒烟工具（register + list_nodes + node_echo）")
	fmt.Fprintln(os.Stderr, "")
	fmt.Fprintln(os.Stderr, "用法：")
	fmt.Fprintln(os.Stderr, "  hubsmoke register   -addr <ip:port> [-device-id <id>] [-timeout 8s] [-v]")
	fmt.Fprintln(os.Stderr, "  hubsmoke list-nodes -addr <ip:port> [-device-id <id>] [-timeout 8s] [-v]")
	fmt.Fprintln(os.Stderr, "  hubsmoke echo       -addr <ip:port> -message <text> [-target <node_id>] [-device-id <id>] [-timeout 8s] [-v]")
	fmt.Fprintln(os.Stderr, "")
	fmt.Fprintln(os.Stderr, "说明：")
	fmt.Fprintln(os.Stderr, "  - 每个命令都会先执行 auth/register 获取 node_id，再执行 management 命令。")
	fmt.Fprintln(os.Stderr, "  - echo 的 -target 若未提供或为 0：默认对当前连接的 hub_id 发起。")
}

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	cmd := strings.TrimSpace(os.Args[1])
	switch cmd {
	case "register":
		runRegister(os.Args[2:])
	case "list-nodes":
		runListNodes(os.Args[2:])
	case "echo":
		runEcho(os.Args[2:])
	case "-h", "--help", "help":
		usage()
	default:
		fmt.Fprintf(os.Stderr, "unknown command: %s\n\n", cmd)
		usage()
		os.Exit(2)
	}
}

func runRegister(args []string) {
	fs := flag.NewFlagSet("register", flag.ContinueOnError)
	addr := fs.String("addr", "", "hub address, e.g. 192.168.1.50:9000")
	deviceID := fs.String("device-id", "", "device id for auth register (optional)")
	timeout := fs.Duration("timeout", 8*time.Second, "timeout, e.g. 8s")
	verbose := fs.Bool("v", false, "verbose frames")
	_ = fs.Parse(args)
	if strings.TrimSpace(*addr) == "" {
		fmt.Fprintln(os.Stderr, "missing -addr")
		os.Exit(2)
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()
	conn, reader, err := dial(ctx, *addr, *timeout)
	if err != nil {
		fmt.Fprintf(os.Stderr, "dial failed: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	reg, err := doRegister(ctx, conn, reader, *deviceID, *verbose)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
	fmt.Printf("register ok: device_id=%s node_id=%d hub_id=%d role=%s perms=%s\n",
		strings.TrimSpace(reg.DeviceID),
		reg.NodeID,
		reg.HubID,
		strings.TrimSpace(reg.Role),
		strings.Join(reg.Perms, ","),
	)
}

func runListNodes(args []string) {
	fs := flag.NewFlagSet("list-nodes", flag.ContinueOnError)
	addr := fs.String("addr", "", "hub address, e.g. 192.168.1.50:9000")
	deviceID := fs.String("device-id", "", "device id for auth register (optional)")
	timeout := fs.Duration("timeout", 8*time.Second, "timeout, e.g. 8s")
	verbose := fs.Bool("v", false, "verbose frames")
	_ = fs.Parse(args)
	if strings.TrimSpace(*addr) == "" {
		fmt.Fprintln(os.Stderr, "missing -addr")
		os.Exit(2)
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()
	conn, reader, err := dial(ctx, *addr, *timeout)
	if err != nil {
		fmt.Fprintf(os.Stderr, "dial failed: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	reg, err := doRegister(ctx, conn, reader, *deviceID, *verbose)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
	resp, err := doListNodes(ctx, conn, reader, reg.NodeID, reg.HubID, *verbose)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
	fmt.Printf("list_nodes ok: hub_id=%d count=%d\n", reg.HubID, len(resp.Nodes))
	for _, n := range resp.Nodes {
		fmt.Printf("- node_id=%d has_children=%v\n", n.NodeID, n.HasChildren)
	}
}

func runEcho(args []string) {
	fs := flag.NewFlagSet("echo", flag.ContinueOnError)
	addr := fs.String("addr", "", "hub address, e.g. 192.168.1.50:9000")
	deviceID := fs.String("device-id", "", "device id for auth register (optional)")
	timeout := fs.Duration("timeout", 8*time.Second, "timeout, e.g. 8s")
	verbose := fs.Bool("v", false, "verbose frames")
	targetRaw := fs.String("target", "", "target node_id (optional, default hub_id)")
	messageText := fs.String("message", "", "message text, e.g. ping")
	_ = fs.Parse(args)
	if strings.TrimSpace(*addr) == "" {
		fmt.Fprintln(os.Stderr, "missing -addr")
		os.Exit(2)
	}
	if strings.TrimSpace(*messageText) == "" {
		fmt.Fprintln(os.Stderr, "missing -message")
		os.Exit(2)
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()
	conn, reader, err := dial(ctx, *addr, *timeout)
	if err != nil {
		fmt.Fprintf(os.Stderr, "dial failed: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	reg, err := doRegister(ctx, conn, reader, *deviceID, *verbose)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}

	target := reg.HubID
	if strings.TrimSpace(*targetRaw) != "" {
		n, err := strconv.ParseUint(strings.TrimSpace(*targetRaw), 10, 32)
		if err != nil {
			fmt.Fprintf(os.Stderr, "invalid -target: %v\n", err)
			os.Exit(2)
		}
		if n != 0 {
			target = uint32(n)
		}
	}

	resp, err := doEcho(ctx, conn, reader, reg.NodeID, target, *messageText, *verbose)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
	fmt.Printf("echo ok: target=%d echo=%s\n", target, strconv.Quote(resp.Echo))
}
