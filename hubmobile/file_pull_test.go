package hubmobile

// 本文件覆盖 Android `hubmobile` 桥接中与 `file_pull` 相关的行为。

import (
	"encoding/binary"
	"encoding/json"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	core "github.com/yttydcs/myflowhub-core"
	"github.com/yttydcs/myflowhub-core/header"
	protocolfile "github.com/yttydcs/myflowhub-proto/protocol/file"
)

func TestFilePull_ConfiguresRuntimeAndReturnsLocalPath(t *testing.T) {
	fileRuntimeMu.Lock()
	prevRuntime := fileRuntime
	fileRuntimeMu.Unlock()
	prevReadAwait := fileReadAwaitFn

	rt := newMobileFileRuntime(func(core.IHeader, []byte) error { return nil }, slog.New(slog.NewTextHandler(io.Discard, nil)))
	fileRuntimeMu.Lock()
	fileRuntime = rt
	fileRuntimeMu.Unlock()
	fileReadAwaitFn = func(sourceID, hubID string, req protocolfile.ReadReq) (string, error) {
		if sourceID != "11" || hubID != "22" {
			t.Fatalf("unexpected route %s/%s", sourceID, hubID)
		}
		if req.Op != protocolfile.OpPull {
			t.Fatalf("unexpected op %s", req.Op)
		}
		if req.Target != 33 {
			t.Fatalf("unexpected target %d", req.Target)
		}
		if req.WantHash == nil || !*req.WantHash {
			t.Fatalf("want_hash should default to true")
		}
		raw, _ := json.Marshal(protocolfile.ReadResp{
			Code:      1,
			Msg:       "ok",
			Op:        protocolfile.OpPull,
			SessionID: "00112233-4455-6677-8899-aabbccddeeff",
			Provider:  33,
			Consumer:  11,
			Dir:       "logs",
			Name:      "demo.txt",
			Size:      5,
			StartFrom: 0,
			Chunk:     1024,
		})
		return string(raw), nil
	}
	t.Cleanup(func() {
		fileReadAwaitFn = prevReadAwait
		fileRuntimeMu.Lock()
		fileRuntime = prevRuntime
		fileRuntimeMu.Unlock()
		rt.cancel()
	})

	baseDir := filepath.Join(t.TempDir(), "downloads")
	raw, err := FilePull("11", "22", "33", "logs", "demo.txt", "", baseDir)
	if err != nil {
		t.Fatalf("FilePull() error = %v", err)
	}

	var out filePullStart
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		t.Fatalf("unmarshal result: %v", err)
	}
	if out.LocalBaseDir == "" {
		t.Fatalf("LocalBaseDir should not be empty")
	}
	if out.LocalPath != filepath.Join(out.LocalBaseDir, "logs", "demo.txt") {
		t.Fatalf("LocalPath = %q", out.LocalPath)
	}
	if got := rt.server.NodeID(); got != 11 {
		t.Fatalf("runtime node_id = %d, want 11", got)
	}
	if parentNode, ok := rt.parent.GetMeta("nodeID"); !ok || parentNode != uint32(22) {
		t.Fatalf("parent nodeID = %#v ok=%v", parentNode, ok)
	}
}

func TestFileOffer_UsesStagedFileAndReturnsLocalPath(t *testing.T) {
	fileRuntimeMu.Lock()
	prevRuntime := fileRuntime
	fileRuntimeMu.Unlock()
	prevWriteAwait := fileWriteAwaitFn

	rt := newMobileFileRuntime(func(core.IHeader, []byte) error { return nil }, slog.New(slog.NewTextHandler(io.Discard, nil)))
	fileRuntimeMu.Lock()
	fileRuntime = rt
	fileRuntimeMu.Unlock()
	fileWriteAwaitFn = func(sourceID, hubID string, req protocolfile.WriteReq) (string, error) {
		if sourceID != "11" || hubID != "22" {
			t.Fatalf("unexpected route %s/%s", sourceID, hubID)
		}
		if req.Op != protocolfile.OpOffer {
			t.Fatalf("unexpected op %s", req.Op)
		}
		if req.Target != 33 {
			t.Fatalf("unexpected target %d", req.Target)
		}
		if strings.TrimSpace(req.SessionID) == "" {
			t.Fatalf("session_id should not be empty")
		}
		if req.Size != 5 {
			t.Fatalf("unexpected size %d", req.Size)
		}
		if strings.TrimSpace(req.Sha256) == "" {
			t.Fatalf("sha256 should not be empty when want_hash=true")
		}
		raw, _ := json.Marshal(protocolfile.WriteResp{
			Code:       1,
			Msg:        "ok",
			Op:         protocolfile.OpOffer,
			SessionID:  req.SessionID,
			Provider:   11,
			Consumer:   33,
			Dir:        "logs",
			Name:       "demo.txt",
			Size:       5,
			Sha256:     req.Sha256,
			Accept:     true,
			ResumeFrom: 0,
		})
		return string(raw), nil
	}
	t.Cleanup(func() {
		fileWriteAwaitFn = prevWriteAwait
		fileRuntimeMu.Lock()
		fileRuntime = prevRuntime
		fileRuntimeMu.Unlock()
		rt.cancel()
	})

	baseDir := filepath.Join(t.TempDir(), "uploads")
	stagePath := filepath.Join(baseDir, "logs", "demo.txt")
	if err := os.MkdirAll(filepath.Dir(stagePath), 0o755); err != nil {
		t.Fatalf("MkdirAll: %v", err)
	}
	if err := os.WriteFile(stagePath, []byte("hello"), 0o644); err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	raw, err := FileOffer("11", "22", "33", "logs", "demo.txt", "", baseDir)
	if err != nil {
		t.Fatalf("FileOffer() error = %v", err)
	}

	var out fileOfferStart
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		t.Fatalf("unmarshal result: %v", err)
	}
	if out.LocalBaseDir == "" {
		t.Fatalf("LocalBaseDir should not be empty")
	}
	if out.LocalPath != stagePath {
		t.Fatalf("LocalPath = %q, want %q", out.LocalPath, stagePath)
	}
	if got := rt.server.NodeID(); got != 11 {
		t.Fatalf("runtime node_id = %d, want 11", got)
	}
	if parentNode, ok := rt.parent.GetMeta("nodeID"); !ok || parentNode != uint32(22) {
		t.Fatalf("parent nodeID = %#v ok=%v", parentNode, ok)
	}
}

func TestMobileFileRuntime_PullResponseAndDataWriteFile(t *testing.T) {
	type sentFrame struct {
		hdr     core.IHeader
		payload []byte
	}

	var sent []sentFrame
	rt := newMobileFileRuntime(func(hdr core.IHeader, payload []byte) error {
		sent = append(sent, sentFrame{hdr: hdr.Clone(), payload: append([]byte(nil), payload...)})
		return nil
	}, slog.New(slog.NewTextHandler(io.Discard, nil)))
	defer rt.cancel()

	baseDir := filepath.Join(t.TempDir(), "downloads")
	if _, err := rt.configure(100, 200, baseDir); err != nil {
		t.Fatalf("configure: %v", err)
	}

	respPayload := wrapFileCtrlPayload(mustEncodeMessage(protocolfile.ActionReadResp, protocolfile.ReadResp{
		Code:      1,
		Msg:       "ok",
		Op:        protocolfile.OpPull,
		SessionID: "00112233-4455-6677-8899-aabbccddeeff",
		Provider:  300,
		Consumer:  100,
		Dir:       "logs",
		Name:      "demo.txt",
		Size:      5,
		StartFrom: 0,
	}))
	rt.observe((&header.HeaderTcp{}).
		WithMajor(header.MajorOKResp).
		WithSubProto(protocolfile.SubProtoFile).
		WithSourceID(300).
		WithTargetID(100), respPayload)

	rt.observe((&header.HeaderTcp{}).
		WithMajor(header.MajorMsg).
		WithSubProto(protocolfile.SubProtoFile).
		WithSourceID(300).
		WithTargetID(100), encodeTestFileBinFrame(protocolfile.KindData, "00112233-4455-6677-8899-aabbccddeeff", 0, true, []byte("hello")))

	targetPath := filepath.Join(baseDir, "logs", "demo.txt")
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		body, err := os.ReadFile(targetPath)
		if err == nil && string(body) == "hello" {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}

	body, err := os.ReadFile(targetPath)
	if err != nil {
		t.Fatalf("ReadFile(%s): %v", targetPath, err)
	}
	if string(body) != "hello" {
		t.Fatalf("file content = %q, want hello", string(body))
	}
	if len(sent) == 0 {
		t.Fatalf("expected ACK frame to be sent")
	}
	last := sent[len(sent)-1]
	if last.hdr.SourceID() != 100 || last.hdr.TargetID() != 300 {
		t.Fatalf("ACK route = %d -> %d", last.hdr.SourceID(), last.hdr.TargetID())
	}
	if last.payload[0] != protocolfile.KindAck {
		t.Fatalf("ACK kind = %d", last.payload[0])
	}
	if got := binary.BigEndian.Uint64(last.payload[21:29]); got != 5 {
		t.Fatalf("ACK offset = %d, want 5", got)
	}
}

func TestMobileFileRuntime_WriteRespStartsDataSend(t *testing.T) {
	type sentFrame struct {
		hdr     core.IHeader
		payload []byte
	}

	var sent []sentFrame
	rt := newMobileFileRuntime(func(hdr core.IHeader, payload []byte) error {
		sent = append(sent, sentFrame{hdr: hdr.Clone(), payload: append([]byte(nil), payload...)})
		return nil
	}, slog.New(slog.NewTextHandler(io.Discard, nil)))
	defer rt.cancel()

	baseDir := filepath.Join(t.TempDir(), "uploads")
	if _, err := rt.configure(100, 200, baseDir); err != nil {
		t.Fatalf("configure: %v", err)
	}
	stagePath := filepath.Join(baseDir, "logs", "demo.txt")
	if err := os.MkdirAll(filepath.Dir(stagePath), 0o755); err != nil {
		t.Fatalf("MkdirAll: %v", err)
	}
	if err := os.WriteFile(stagePath, []byte("hello"), 0o644); err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	respPayload := wrapFileCtrlPayload(mustEncodeMessage(protocolfile.ActionWriteResp, protocolfile.WriteResp{
		Code:       1,
		Msg:        "ok",
		Op:         protocolfile.OpOffer,
		SessionID:  "00112233-4455-6677-8899-aabbccddeeff",
		Provider:   100,
		Consumer:   300,
		Dir:        "logs",
		Name:       "demo.txt",
		Size:       5,
		Accept:     true,
		ResumeFrom: 0,
	}))
	rt.observe((&header.HeaderTcp{}).
		WithMajor(header.MajorOKResp).
		WithSubProto(protocolfile.SubProtoFile).
		WithSourceID(300).
		WithTargetID(100), respPayload)

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if len(sent) > 0 {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	if len(sent) == 0 {
		t.Fatalf("expected DATA frame to be sent")
	}
	first := sent[0]
	if first.hdr.SourceID() != 100 || first.hdr.TargetID() != 300 {
		t.Fatalf("DATA route = %d -> %d", first.hdr.SourceID(), first.hdr.TargetID())
	}
	if first.payload[0] != protocolfile.KindData {
		t.Fatalf("DATA kind = %d", first.payload[0])
	}
	if body := string(first.payload[29:]); body != "hello" {
		t.Fatalf("DATA body = %q, want hello", body)
	}
}

func TestResolveLocalDownloadPath_RejectsTraversal(t *testing.T) {
	baseDir := filepath.Join(t.TempDir(), "downloads")
	if _, err := resolveLocalDownloadPath(baseDir, "../bad", "demo.txt"); err == nil {
		t.Fatalf("expected traversal dir to fail")
	}
	if _, err := resolveLocalDownloadPath(baseDir, "logs", "../bad"); err == nil {
		t.Fatalf("expected traversal name to fail")
	}
}

func TestFileOffer_RejectsEmptyFile(t *testing.T) {
	fileRuntimeMu.Lock()
	prevRuntime := fileRuntime
	fileRuntimeMu.Unlock()

	rt := newMobileFileRuntime(func(core.IHeader, []byte) error { return nil }, slog.New(slog.NewTextHandler(io.Discard, nil)))
	fileRuntimeMu.Lock()
	fileRuntime = rt
	fileRuntimeMu.Unlock()
	t.Cleanup(func() {
		fileRuntimeMu.Lock()
		fileRuntime = prevRuntime
		fileRuntimeMu.Unlock()
		rt.cancel()
	})

	baseDir := filepath.Join(t.TempDir(), "uploads")
	stagePath := filepath.Join(baseDir, "logs", "empty.txt")
	if err := os.MkdirAll(filepath.Dir(stagePath), 0o755); err != nil {
		t.Fatalf("MkdirAll: %v", err)
	}
	if err := os.WriteFile(stagePath, nil, 0o644); err != nil {
		t.Fatalf("WriteFile: %v", err)
	}

	if _, err := FileOffer("11", "22", "33", "logs", "empty.txt", "", baseDir); err == nil {
		t.Fatalf("expected empty file offer to fail")
	}
}

func mustEncodeMessage(action string, data any) []byte {
	rawData, _ := json.Marshal(data)
	raw, _ := json.Marshal(protocolfile.Message{
		Action: action,
		Data:   rawData,
	})
	return raw
}

func encodeTestFileBinFrame(kind byte, sessionID string, offset uint64, fin bool, body []byte) []byte {
	out := make([]byte, 29+len(body))
	out[0] = kind
	out[1] = 1
	if fin {
		out[2] = 1
	}
	copy(out[5:21], mustParseUUID(sessionID))
	binary.BigEndian.PutUint64(out[21:29], offset)
	copy(out[29:], body)
	return out
}

func mustParseUUID(text string) []byte {
	hexText := strings.ReplaceAll(text, "-", "")
	out := make([]byte, 16)
	for i := 0; i < 16; i++ {
		v, err := strconv.ParseUint(hexText[i*2:i*2+2], 16, 8)
		if err != nil {
			panic(err)
		}
		out[i] = byte(v)
	}
	return out
}
