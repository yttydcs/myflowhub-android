package hubmobile

// Context: This file supports the Android app or gomobile host flow around file.

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	protocolfile "github.com/yttydcs/myflowhub-proto/protocol/file"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultFileTimeout = 8 * time.Second
const fileOpMkdir = "mkdir"
const defaultReadTextMaxBytes = 65536

var fileReadAwaitFn = fileReadAndAwait
var fileWriteAwaitFn = fileWriteAndAwait

type filePullStart struct {
	Code         int    `json:"code"`
	Msg          string `json:"msg,omitempty"`
	Op           string `json:"op,omitempty"`
	SessionID    string `json:"session_id,omitempty"`
	Provider     uint32 `json:"provider,omitempty"`
	Consumer     uint32 `json:"consumer,omitempty"`
	Dir          string `json:"dir,omitempty"`
	Name         string `json:"name,omitempty"`
	Size         uint64 `json:"size,omitempty"`
	Sha256       string `json:"sha256,omitempty"`
	StartFrom    uint64 `json:"start_from,omitempty"`
	Chunk        uint32 `json:"chunk_bytes,omitempty"`
	LocalBaseDir string `json:"local_base_dir,omitempty"`
	LocalPath    string `json:"local_path,omitempty"`
}

type fileOfferStart struct {
	Code         int    `json:"code"`
	Msg          string `json:"msg,omitempty"`
	Op           string `json:"op,omitempty"`
	SessionID    string `json:"session_id,omitempty"`
	Provider     uint32 `json:"provider,omitempty"`
	Consumer     uint32 `json:"consumer,omitempty"`
	Dir          string `json:"dir,omitempty"`
	Name         string `json:"name,omitempty"`
	Size         uint64 `json:"size,omitempty"`
	Sha256       string `json:"sha256,omitempty"`
	ResumeFrom   uint64 `json:"resume_from,omitempty"`
	LocalBaseDir string `json:"local_base_dir,omitempty"`
	LocalPath    string `json:"local_path,omitempty"`
}

func FileList(sourceID, hubID, targetID, dir string) (string, error) {
	target, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	req := protocolfile.ReadReq{
		Op:     protocolfile.OpList,
		Target: target,
		Dir:    strings.TrimSpace(dir),
	}
	return fileReadAndAwait(sourceID, hubID, req)
}

func FileReadText(sourceID, hubID, targetID, dir, name, maxBytes string) (string, error) {
	target, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	limit, err := parseInt("max_bytes", maxBytes)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if limit <= 0 {
		limit = defaultReadTextMaxBytes
	}
	req := protocolfile.ReadReq{
		Op:       protocolfile.OpReadText,
		Target:   target,
		Dir:      strings.TrimSpace(dir),
		Name:     strings.TrimSpace(name),
		MaxBytes: uint32(limit),
	}
	return fileReadAndAwait(sourceID, hubID, req)
}

func FileCreateDir(sourceID, hubID, targetID, dir, name string) (string, error) {
	target, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	req := protocolfile.WriteReq{
		Op:     fileOpMkdir,
		Target: target,
		Dir:    strings.TrimSpace(dir),
		Name:   strings.TrimSpace(name),
	}
	return fileWriteAndAwait(sourceID, hubID, req)
}

func FilePull(sourceID, hubID, targetID, dir, name, wantHash, localBaseDir string) (string, error) {
	target, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	src, hub, err := parseFileRoute(sourceID, hubID, target)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if target == src {
		err := errors.New("pull target must be remote")
		storeLastError(err)
		return "", err
	}
	want, err := parseWantHash(wantHash)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	resolvedBaseDir, err := ensureFileRuntime().configure(src, hub, localBaseDir)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	localPath, err := resolveLocalDownloadPath(resolvedBaseDir, dir, name)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	req := protocolfile.ReadReq{
		Op:       protocolfile.OpPull,
		Target:   target,
		Dir:      strings.TrimSpace(dir),
		Name:     strings.TrimSpace(name),
		WantHash: &want,
	}
	raw, err := fileReadAwaitFn(sourceID, hubID, req)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	var resp protocolfile.ReadResp
	if err := json.Unmarshal([]byte(raw), &resp); err != nil {
		storeLastError(err)
		return "", err
	}
	out := filePullStart{
		Code:         resp.Code,
		Msg:          strings.TrimSpace(resp.Msg),
		Op:           strings.TrimSpace(resp.Op),
		SessionID:    strings.TrimSpace(resp.SessionID),
		Provider:     resp.Provider,
		Consumer:     resp.Consumer,
		Dir:          strings.TrimSpace(resp.Dir),
		Name:         strings.TrimSpace(resp.Name),
		Size:         resp.Size,
		Sha256:       strings.TrimSpace(resp.Sha256),
		StartFrom:    resp.StartFrom,
		Chunk:        resp.Chunk,
		LocalBaseDir: resolvedBaseDir,
		LocalPath:    localPath,
	}
	encoded, _ := json.Marshal(out)
	return string(encoded), nil
}

func FileOffer(sourceID, hubID, targetID, dir, name, wantHash, localBaseDir string) (string, error) {
	target, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	src, hub, err := parseFileRoute(sourceID, hubID, target)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if target == src {
		err := errors.New("offer target must be remote")
		storeLastError(err)
		return "", err
	}
	want, err := parseWantHash(wantHash)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	resolvedBaseDir, err := ensureFileRuntime().configure(src, hub, localBaseDir)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	localPath, err := resolveLocalDownloadPath(resolvedBaseDir, dir, name)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	info, err := os.Stat(localPath)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if info.IsDir() {
		err := errors.New("offer source must be a file")
		storeLastError(err)
		return "", err
	}
	if info.Size() <= 0 {
		err := errors.New("offer source file must not be empty")
		storeLastError(err)
		return "", err
	}

	shaHex := ""
	if want {
		shaHex, err = sha256FileHex(localPath)
		if err != nil {
			storeLastError(err)
			return "", err
		}
	}
	sessionID, err := newFileSessionID()
	if err != nil {
		storeLastError(err)
		return "", err
	}
	overwrite := true
	req := protocolfile.WriteReq{
		Op:        protocolfile.OpOffer,
		Target:    target,
		SessionID: sessionID,
		Dir:       strings.TrimSpace(dir),
		Name:      strings.TrimSpace(name),
		Size:      uint64(info.Size()),
		Sha256:    shaHex,
		Overwrite: &overwrite,
	}
	raw, err := fileWriteAwaitFn(sourceID, hubID, req)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	var resp protocolfile.WriteResp
	if err := json.Unmarshal([]byte(raw), &resp); err != nil {
		storeLastError(err)
		return "", err
	}
	if !resp.Accept {
		err := errors.New(strings.TrimSpace(resp.Msg))
		if err.Error() == "" {
			err = errors.New("offer rejected")
		}
		storeLastError(err)
		return "", err
	}

	out := fileOfferStart{
		Code:         resp.Code,
		Msg:          strings.TrimSpace(resp.Msg),
		Op:           strings.TrimSpace(resp.Op),
		SessionID:    strings.TrimSpace(resp.SessionID),
		Provider:     resp.Provider,
		Consumer:     resp.Consumer,
		Dir:          strings.TrimSpace(resp.Dir),
		Name:         strings.TrimSpace(resp.Name),
		Size:         resp.Size,
		Sha256:       strings.TrimSpace(resp.Sha256),
		ResumeFrom:   resp.ResumeFrom,
		LocalBaseDir: resolvedBaseDir,
		LocalPath:    localPath,
	}
	encoded, _ := json.Marshal(out)
	return string(encoded), nil
}

func fileReadAndAwait(sourceID, hubID string, req protocolfile.ReadReq) (string, error) {
	src, hub, err := parseFileRoute(sourceID, hubID, req.Target)
	if err != nil {
		return "", err
	}
	if strings.TrimSpace(req.Op) == "" {
		err := errors.New("op is required")
		storeLastError(err)
		return "", err
	}
	if (strings.TrimSpace(req.Op) == protocolfile.OpReadText || strings.TrimSpace(req.Op) == protocolfile.OpPull) && strings.TrimSpace(req.Name) == "" {
		err := errors.New("name is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protocolfile.ActionRead, req)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultFileTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protocolfile.SubProtoFile, src, hub, wrapFileCtrlPayload(payload), protocolfile.ActionReadResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("file %s: %w", strings.TrimSpace(req.Op), err)
	}

	var out protocolfile.ReadResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if err := validateFileReadResp(req.Op, out); err != nil {
		storeLastError(err)
		return "", err
	}

	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func fileWriteAndAwait(sourceID, hubID string, req protocolfile.WriteReq) (string, error) {
	src, hub, err := parseFileRoute(sourceID, hubID, req.Target)
	if err != nil {
		return "", err
	}
	if strings.TrimSpace(req.Op) == "" {
		err := errors.New("op is required")
		storeLastError(err)
		return "", err
	}
	if strings.TrimSpace(req.Name) == "" {
		err := errors.New("name is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protocolfile.ActionWrite, req)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultFileTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protocolfile.SubProtoFile, src, hub, wrapFileCtrlPayload(payload), protocolfile.ActionWriteResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("file %s: %w", strings.TrimSpace(req.Op), err)
	}

	var out protocolfile.WriteResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if err := validateFileWriteResp(req.Op, out); err != nil {
		storeLastError(err)
		return "", err
	}

	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func parseFileRoute(sourceID, hubID string, targetID uint32) (uint32, uint32, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return 0, 0, err
	}
	if src == 0 {
		err := errors.New("source_id is required")
		storeLastError(err)
		return 0, 0, err
	}
	hub, err := parseUint32("hub_id", hubID)
	if err != nil {
		storeLastError(err)
		return 0, 0, err
	}
	if hub == 0 {
		err := errors.New("hub_id is required")
		storeLastError(err)
		return 0, 0, err
	}
	if targetID == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return 0, 0, err
	}
	return src, hub, nil
}

func wrapFileCtrlPayload(payload []byte) []byte {
	out := make([]byte, 1+len(payload))
	out[0] = protocolfile.KindCtrl
	copy(out[1:], payload)
	return out
}

func validateFileReadResp(expectedOp string, resp protocolfile.ReadResp) error {
	return validateFileResp(expectedOp, resp.Op, resp.Code, resp.Msg, "file read")
}

func validateFileWriteResp(expectedOp string, resp protocolfile.WriteResp) error {
	return validateFileResp(expectedOp, resp.Op, resp.Code, resp.Msg, "file write")
}

func validateFileResp(expectedOp, actualOp string, code int, msg, fallback string) error {
	if code != 1 {
		message := strings.TrimSpace(msg)
		if message == "" {
			message = fmt.Sprintf("%s failed (code=%d)", fallback, code)
		}
		return errors.New(message)
	}
	expected := strings.TrimSpace(expectedOp)
	actual := strings.TrimSpace(actualOp)
	if expected != "" && actual != "" && expected != actual {
		return fmt.Errorf("unexpected file op: want %s, got %s", expected, actual)
	}
	return nil
}

func sha256FileHex(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func newFileSessionID() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", err
	}
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		raw[0:4],
		raw[4:6],
		raw[6:8],
		raw[8:10],
		raw[10:16],
	), nil
}
