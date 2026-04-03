package hubmobile

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	protocolfile "github.com/yttydcs/myflowhub-proto/protocol/file"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultFileTimeout = 8 * time.Second
const fileOpMkdir = "mkdir"
const defaultReadTextMaxBytes = 65536

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
	if strings.TrimSpace(req.Op) == protocolfile.OpReadText && strings.TrimSpace(req.Name) == "" {
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
