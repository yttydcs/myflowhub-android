package hubmobile

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

