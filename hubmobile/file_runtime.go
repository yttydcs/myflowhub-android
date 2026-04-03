package hubmobile

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	core "github.com/yttydcs/myflowhub-core"
	coreconfig "github.com/yttydcs/myflowhub-core/config"
	"github.com/yttydcs/myflowhub-core/connmgr"
	"github.com/yttydcs/myflowhub-core/eventbus"
	"github.com/yttydcs/myflowhub-core/header"
	protocolfile "github.com/yttydcs/myflowhub-proto/protocol/file"
	filehandler "github.com/yttydcs/myflowhub-subproto/file"
)

const (
	fileRuntimeQueueSize = 8
	fileRuntimeParentID  = "hubmobile-file-parent"
)

var (
	fileRuntimeMu sync.Mutex
	fileRuntime   *mobileFileRuntime
)

type observedFileFrame struct {
	hdr     core.IHeader
	payload []byte
}

type mobileFileRuntime struct {
	cfg     *coreconfig.MapConfig
	cm      *connmgr.Manager
	server  *mobileFileServer
	parent  *mobileFileConn
	handler *filehandler.Handler

	send func(core.IHeader, []byte) error
	log  *slog.Logger

	ctx    context.Context
	cancel context.CancelFunc
	frames chan observedFileFrame
}

type mobileFileServer struct {
	rt *mobileFileRuntime

	mu     sync.RWMutex
	nodeID uint32
}

type mobileFileConn struct {
	id   string
	meta map[string]any
	mu   sync.RWMutex
}

func ensureFileRuntime() *mobileFileRuntime {
	fileRuntimeMu.Lock()
	defer fileRuntimeMu.Unlock()
	if fileRuntime != nil {
		return fileRuntime
	}
	fileRuntime = newMobileFileRuntime(sendCurrentClientFrame, globalLogger)
	return fileRuntime
}

func onObservedFrame(hdr core.IHeader, payload []byte) {
	fileRuntimeMu.Lock()
	rt := fileRuntime
	fileRuntimeMu.Unlock()
	if rt == nil {
		return
	}
	rt.observe(hdr, payload)
}

func newMobileFileRuntime(send func(core.IHeader, []byte) error, log *slog.Logger) *mobileFileRuntime {
	if log == nil {
		log = globalLogger
	}
	if send == nil {
		send = func(core.IHeader, []byte) error { return errors.New("file runtime sender unavailable") }
	}

	cfg := coreconfig.NewMap(nil)
	cm := connmgr.New()
	parent := newMobileFileConn(fileRuntimeParentID)
	parent.SetMeta(core.MetaRoleKey, core.RoleParent)
	_ = cm.Add(parent)

	ctx, cancel := context.WithCancel(context.Background())
	rt := &mobileFileRuntime{
		cfg:    cfg,
		cm:     cm,
		send:   send,
		log:    log,
		ctx:    ctx,
		cancel: cancel,
		frames: make(chan observedFileFrame, fileRuntimeQueueSize),
		parent: parent,
	}
	rt.server = &mobileFileServer{rt: rt}
	rt.handler = filehandler.NewHandlerWithConfig(cfg, log)
	go rt.loop()
	return rt
}

func (rt *mobileFileRuntime) configure(localNodeID, hubNodeID uint32, baseDir string) (string, error) {
	if rt == nil {
		return "", errors.New("file runtime unavailable")
	}
	if localNodeID == 0 {
		return "", errors.New("source_id is required")
	}
	if hubNodeID == 0 {
		return "", errors.New("hub_id is required")
	}
	resolvedBaseDir, err := resolveLocalBaseDir(baseDir)
	if err != nil {
		return "", err
	}

	rt.server.UpdateNodeID(localNodeID)
	rt.parent.SetMeta("nodeID", hubNodeID)
	rt.cm.UpdateNodeIndex(hubNodeID, rt.parent)
	rt.cfg.Set("file.base_dir", resolvedBaseDir)
	return resolvedBaseDir, nil
}

func (rt *mobileFileRuntime) observe(hdr core.IHeader, payload []byte) {
	if rt == nil || !shouldHandleObservedFileFrame(hdr, payload) {
		return
	}

	frame := observedFileFrame{
		hdr:     hdr.Clone(),
		payload: append([]byte(nil), payload...),
	}

	select {
	case <-rt.ctx.Done():
		return
	case rt.frames <- frame:
	}
}

func (rt *mobileFileRuntime) loop() {
	for {
		select {
		case <-rt.ctx.Done():
			return
		case frame := <-rt.frames:
			ctx := core.WithServerContext(context.Background(), rt.server)
			rt.handler.OnReceive(ctx, rt.parent, frame.hdr, frame.payload)
		}
	}
}

func shouldHandleObservedFileFrame(hdr core.IHeader, payload []byte) bool {
	if hdr == nil || hdr.SubProto() != protocolfile.SubProtoFile || len(payload) == 0 {
		return false
	}
	switch payload[0] {
	case protocolfile.KindData, protocolfile.KindAck:
		return true
	case protocolfile.KindCtrl:
		if len(payload) < 2 {
			return false
		}
		var msg protocolfile.Message
		if err := json.Unmarshal(payload[1:], &msg); err != nil {
			return false
		}
		action := strings.ToLower(strings.TrimSpace(msg.Action))
		return action == protocolfile.ActionReadResp || action == protocolfile.ActionWriteResp
	default:
		return false
	}
}

func sendCurrentClientFrame(hdr core.IHeader, payload []byte) error {
	if hdr == nil {
		return errors.New("header is required")
	}
	if len(payload) == 0 {
		return errors.New("payload is required")
	}

	clientMu.Lock()
	c := client
	clientMu.Unlock()
	if c == nil {
		return errors.New("client not initialized")
	}
	if err := c.Send(hdr, payload); err != nil {
		logWarn("file runtime send failed", "err", err.Error())
		return err
	}
	return nil
}

func resolveLocalBaseDir(baseDir string) (string, error) {
	baseDir = strings.TrimSpace(baseDir)
	if baseDir == "" {
		return "", errors.New("local_base_dir is required")
	}
	abs, err := filepath.Abs(baseDir)
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(abs, 0o755); err != nil {
		return "", err
	}
	return abs, nil
}

func resolveLocalDownloadPath(baseDir, dir, name string) (string, error) {
	baseDir, err := resolveLocalBaseDir(baseDir)
	if err != nil {
		return "", err
	}
	name, err = sanitizeRemoteName(name)
	if err != nil {
		return "", err
	}
	dir, err = sanitizeRemoteDir(dir)
	if err != nil {
		return "", err
	}

	absTarget, err := filepath.Abs(filepath.Join(baseDir, filepath.FromSlash(dir), name))
	if err != nil {
		return "", err
	}
	rel, err := filepath.Rel(baseDir, absTarget)
	if err != nil {
		return "", err
	}
	if rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
		return "", errors.New("remote path escapes local_base_dir")
	}
	return absTarget, nil
}

func sanitizeRemoteDir(dir string) (string, error) {
	dir = strings.TrimSpace(dir)
	if dir == "" || dir == "." {
		return "", nil
	}
	if strings.ContainsRune(dir, 0) {
		return "", errors.New("dir invalid")
	}
	if strings.HasPrefix(dir, "/") || strings.HasPrefix(dir, "\\") {
		return "", errors.New("dir invalid")
	}
	if len(dir) >= 2 && isASCIIAlpha(dir[0]) && dir[1] == ':' {
		return "", errors.New("dir invalid")
	}
	if strings.Contains(dir, "\\") {
		return "", errors.New("dir invalid")
	}
	clean := path.Clean(dir)
	if clean == "." {
		return "", nil
	}
	if clean == ".." || strings.HasPrefix(clean, "../") {
		return "", errors.New("dir invalid")
	}
	return clean, nil
}

func sanitizeRemoteName(name string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" || name == "." || name == ".." {
		return "", errors.New("name is required")
	}
	if strings.ContainsAny(name, "/\\") || strings.ContainsRune(name, 0) {
		return "", errors.New("name invalid")
	}
	return name, nil
}

func parseWantHash(raw string) (bool, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return true, nil
	}
	ok, err := strconv.ParseBool(raw)
	if err != nil {
		return false, errors.New("want_hash invalid")
	}
	return ok, nil
}

func isASCIIAlpha(b byte) bool {
	return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
}

func newMobileFileConn(id string) *mobileFileConn {
	return &mobileFileConn{
		id:   id,
		meta: make(map[string]any),
	}
}

func (c *mobileFileConn) ID() string { return c.id }

func (c *mobileFileConn) Pipe() core.IPipe { return nil }

func (c *mobileFileConn) Close() error { return nil }

func (c *mobileFileConn) OnReceive(core.ReceiveHandler) {}

func (c *mobileFileConn) Send([]byte) error { return nil }

func (c *mobileFileConn) SendWithHeader(core.IHeader, []byte, core.IHeaderCodec) error {
	return nil
}

func (c *mobileFileConn) SetMeta(key string, val any) {
	c.mu.Lock()
	c.meta[key] = val
	c.mu.Unlock()
}

func (c *mobileFileConn) GetMeta(key string) (any, bool) {
	c.mu.RLock()
	v, ok := c.meta[key]
	c.mu.RUnlock()
	return v, ok
}

func (c *mobileFileConn) Metadata() map[string]any {
	c.mu.RLock()
	defer c.mu.RUnlock()
	cp := make(map[string]any, len(c.meta))
	for k, v := range c.meta {
		cp[k] = v
	}
	return cp
}

func (c *mobileFileConn) LocalAddr() net.Addr { return nil }

func (c *mobileFileConn) RemoteAddr() net.Addr { return nil }

func (c *mobileFileConn) Reader() core.IReader { return nil }

func (c *mobileFileConn) SetReader(core.IReader) {}

func (c *mobileFileConn) DispatchReceive(core.IHeader, []byte) {}

func (s *mobileFileServer) Start(context.Context) error { return nil }

func (s *mobileFileServer) Stop(context.Context) error { return nil }

func (s *mobileFileServer) Config() core.IConfig { return s.rt.cfg }

func (s *mobileFileServer) ConnManager() core.IConnectionManager { return s.rt.cm }

func (s *mobileFileServer) Process() core.IProcess { return nil }

func (s *mobileFileServer) HeaderCodec() core.IHeaderCodec { return header.HeaderTcpCodec{} }

func (s *mobileFileServer) NodeID() uint32 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.nodeID
}

func (s *mobileFileServer) UpdateNodeID(nodeID uint32) {
	s.mu.Lock()
	s.nodeID = nodeID
	s.mu.Unlock()
}

func (s *mobileFileServer) EventBus() eventbus.IBus { return nil }

func (s *mobileFileServer) Send(_ context.Context, connID string, hdr core.IHeader, payload []byte) error {
	if strings.TrimSpace(connID) == "" {
		return errors.New("conn_id is required")
	}
	if hdr == nil {
		return errors.New("header is required")
	}
	if len(payload) == 0 {
		return errors.New("payload is required")
	}
	if _, ok := s.rt.cm.Get(connID); !ok {
		return errors.New("conn not found")
	}
	return s.rt.send(hdr, payload)
}

var (
	_ core.IConnection = (*mobileFileConn)(nil)
	_ core.IServer     = (*mobileFileServer)(nil)
)
