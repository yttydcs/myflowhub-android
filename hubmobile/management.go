package hubmobile

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	protomgmt "github.com/yttydcs/myflowhub-proto/protocol/management"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultManagementTimeout = 8 * time.Second

func ListNodes(sourceID, targetID string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionListNodes, protomgmt.ListNodesReq{})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionListNodesResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionListNodes, err)
	}

	var data protomgmt.ListNodesResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionListNodes, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

func ListSubtree(sourceID, targetID string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionListSubtree, protomgmt.ListSubtreeReq{})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionListSubtreeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionListSubtree, err)
	}

	var data protomgmt.ListSubtreeResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionListSubtree, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

func NodeInfo(sourceID, targetID string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionNodeInfo, protomgmt.NodeInfoReq{})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionNodeInfoResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionNodeInfo, err)
	}

	var data protomgmt.NodeInfoResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionNodeInfo, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

func ConfigList(sourceID, targetID string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionConfigList, protomgmt.ConfigListReq{})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionConfigListResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionConfigList, err)
	}

	var data protomgmt.ConfigListResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionConfigList, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

func ConfigGet(sourceID, targetID, key string) (string, error) {
	key = strings.TrimSpace(key)
	if key == "" {
		err := errors.New("key is required")
		storeLastError(err)
		return "", err
	}
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionConfigGet, protomgmt.ConfigGetReq{Key: key})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionConfigGetResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionConfigGet, err)
	}

	var data protomgmt.ConfigResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionConfigGet, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}

func ConfigSet(sourceID, targetID, key, value string) (string, error) {
	key = strings.TrimSpace(key)
	if key == "" {
		err := errors.New("key is required")
		storeLastError(err)
		return "", err
	}
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return "", err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(protomgmt.ActionConfigSet, protomgmt.ConfigSetReq{Key: key, Value: value})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultManagementTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, protomgmt.SubProtoManagement, src, tgt, payload, protomgmt.ActionConfigSetResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("management %s: %w", protomgmt.ActionConfigSet, err)
	}

	var data protomgmt.ConfigResp
	if err := json.Unmarshal(resp.Message.Data, &data); err != nil {
		storeLastError(err)
		return "", err
	}
	if data.Code != 1 {
		msg := strings.TrimSpace(data.Msg)
		if msg == "" {
			msg = fmt.Sprintf("management %s failed (code=%d)", protomgmt.ActionConfigSet, data.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(data)
	return string(raw), nil
}
