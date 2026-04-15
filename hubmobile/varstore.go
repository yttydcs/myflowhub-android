package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `varstore` 相关的逻辑。

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/yttydcs/myflowhub-proto/protocol/varstore"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultVarStoreTimeout = 8 * time.Second

// VarStoreList 按 owner 枚举变量名，供 Android 侧浏览目标节点当前暴露的变量集合。
func VarStoreList(sourceID, targetID, owner string) (string, error) {
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(varstore.ActionList, varstore.ListReq{Owner: own})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionListResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionList, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionList, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

// VarStoreGet 读取单个变量当前值，并在 bridge 层先兜底校验空变量名。
func VarStoreGet(sourceID, targetID, name, owner string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(varstore.ActionGet, varstore.GetReq{Name: name, Owner: own})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionGetResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionGet, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionGet, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

// VarStoreSet 写入变量值，同时约束 visibility 只能落在协议允许的枚举里。
func VarStoreSet(sourceID, targetID, name, value, visibility, typ, owner string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
		storeLastError(err)
		return "", err
	}
	if strings.TrimSpace(value) == "" {
		err := errors.New("value is required")
		storeLastError(err)
		return "", err
	}
	visibility = strings.TrimSpace(visibility)
	if visibility != "" && visibility != varstore.VisibilityPublic && visibility != varstore.VisibilityPrivate {
		err := errors.New("visibility invalid")
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(varstore.ActionSet, varstore.SetReq{
		Name:       name,
		Value:      value,
		Visibility: visibility,
		Type:       strings.TrimSpace(typ),
		Owner:      own,
	})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionSetResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionSet, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionSet, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

// VarStoreRevoke 撤销变量；这里复用 get 请求体结构，只保留 name/owner 两个协议关键信息。
func VarStoreRevoke(sourceID, targetID, name, owner string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(varstore.ActionRevoke, varstore.GetReq{Name: name, Owner: own})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionRevokeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionRevoke, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionRevoke, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

// VarStoreSubscribe 订阅变量变更；未显式传 subscriber 时默认回落到当前 source 节点。
func VarStoreSubscribe(sourceID, targetID, name, owner, subscriber string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if own == 0 {
		err := errors.New("owner is required")
		storeLastError(err)
		return "", err
	}
	sub, err := parseUint32("subscriber", subscriber)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if sub == 0 {
		sub = src
	}

	payload, err := transport.EncodeMessage(varstore.ActionSubscribe, varstore.SubscribeReq{
		Name:       name,
		Owner:      own,
		Subscriber: sub,
	})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionSubscribeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionSubscribe, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionSubscribe, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

// VarStoreUnsubscribe 取消订阅，并兼容服务端仍复用 subscribe_resp 的历史响应约定。
func VarStoreUnsubscribe(sourceID, targetID, name, owner, subscriber string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
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

	own, err := parseUint32("owner", owner)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if own == 0 {
		err := errors.New("owner is required")
		storeLastError(err)
		return "", err
	}
	sub, err := parseUint32("subscriber", subscriber)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if sub == 0 {
		sub = src
	}

	payload, err := transport.EncodeMessage(varstore.ActionUnsubscribe, varstore.SubscribeReq{
		Name:       name,
		Owner:      own,
		Subscriber: sub,
	})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultVarStoreTimeout)
	defer cancel()
	// Server uses subscribe_resp for unsubscribe as well (see SubProto varstore handler).
	resp, err := sendAndAwait(ctx, varstore.SubProtoVarStore, src, tgt, payload, varstore.ActionSubscribeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("varstore %s: %w", varstore.ActionUnsubscribe, err)
	}

	var out varstore.VarResp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("varstore %s failed (code=%d)", varstore.ActionUnsubscribe, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}
