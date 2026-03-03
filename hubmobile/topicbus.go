package hubmobile

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/yttydcs/myflowhub-core/header"
	"github.com/yttydcs/myflowhub-proto/protocol/topicbus"
	"github.com/yttydcs/myflowhub-sdk/transport"
)

const defaultTopicBusTimeout = 8 * time.Second

func TopicBusSubscribeSimple(sourceID, targetID, topic string) (string, error) {
	topic = strings.TrimSpace(topic)
	if topic == "" {
		err := errors.New("topic is required")
		storeLastError(err)
		return "", err
	}
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if src == 0 {
		err := errors.New("source_id is required")
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

	payload, err := transport.EncodeMessage(topicbus.ActionSubscribe, topicbus.SubscribeReq{Topic: topic})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultTopicBusTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, topicbus.SubProtoTopicBus, src, tgt, payload, topicbus.ActionSubscribeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("topicbus %s: %w", topicbus.ActionSubscribe, err)
	}

	var out topicbus.Resp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("topicbus %s failed (code=%d)", topicbus.ActionSubscribe, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func TopicBusSubscribeBatchSimple(sourceID, targetID, topicsJSON string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if src == 0 {
		err := errors.New("source_id is required")
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

	topics, err := parseTopicBusTopics(topicsJSON)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if len(topics) == 0 {
		err := errors.New("topics are required")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(topicbus.ActionSubscribeBatch, topicbus.SubscribeBatchReq{Topics: topics})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultTopicBusTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, topicbus.SubProtoTopicBus, src, tgt, payload, topicbus.ActionSubscribeBatchResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("topicbus %s: %w", topicbus.ActionSubscribeBatch, err)
	}

	var out topicbus.Resp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("topicbus %s failed (code=%d)", topicbus.ActionSubscribeBatch, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func TopicBusUnsubscribeSimple(sourceID, targetID, topic string) (string, error) {
	topic = strings.TrimSpace(topic)
	if topic == "" {
		err := errors.New("topic is required")
		storeLastError(err)
		return "", err
	}
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if src == 0 {
		err := errors.New("source_id is required")
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

	payload, err := transport.EncodeMessage(topicbus.ActionUnsubscribe, topicbus.SubscribeReq{Topic: topic})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultTopicBusTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, topicbus.SubProtoTopicBus, src, tgt, payload, topicbus.ActionUnsubscribeResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("topicbus %s: %w", topicbus.ActionUnsubscribe, err)
	}

	var out topicbus.Resp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("topicbus %s failed (code=%d)", topicbus.ActionUnsubscribe, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func TopicBusUnsubscribeBatchSimple(sourceID, targetID, topicsJSON string) (string, error) {
	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if src == 0 {
		err := errors.New("source_id is required")
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

	topics, err := parseTopicBusTopics(topicsJSON)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if len(topics) == 0 {
		err := errors.New("topics are required")
		storeLastError(err)
		return "", err
	}

	payload, err := transport.EncodeMessage(topicbus.ActionUnsubscribeBatch, topicbus.SubscribeBatchReq{Topics: topics})
	if err != nil {
		storeLastError(err)
		return "", err
	}

	ctx, cancel := context.WithTimeout(context.Background(), defaultTopicBusTimeout)
	defer cancel()
	resp, err := sendAndAwait(ctx, topicbus.SubProtoTopicBus, src, tgt, payload, topicbus.ActionUnsubscribeBatchResp)
	if err != nil {
		storeLastError(err)
		return "", fmt.Errorf("topicbus %s: %w", topicbus.ActionUnsubscribeBatch, err)
	}

	var out topicbus.Resp
	if err := json.Unmarshal(resp.Message.Data, &out); err != nil {
		storeLastError(err)
		return "", err
	}
	if out.Code != 1 {
		msg := strings.TrimSpace(out.Msg)
		if msg == "" {
			msg = fmt.Sprintf("topicbus %s failed (code=%d)", topicbus.ActionUnsubscribeBatch, out.Code)
		}
		err := errors.New(msg)
		storeLastError(err)
		return "", err
	}
	raw, _ := json.Marshal(out)
	return string(raw), nil
}

func TopicBusPublish(sourceID, targetID, topic, name, payloadText string) error {
	topic = strings.TrimSpace(topic)
	if topic == "" {
		err := errors.New("topic is required")
		storeLastError(err)
		return err
	}
	name = strings.TrimSpace(name)
	if name == "" {
		err := errors.New("name is required")
		storeLastError(err)
		return err
	}

	src, err := parseUint32("source_id", sourceID)
	if err != nil {
		storeLastError(err)
		return err
	}
	if src == 0 {
		err := errors.New("source_id is required")
		storeLastError(err)
		return err
	}
	tgt, err := parseUint32("target_id", targetID)
	if err != nil {
		storeLastError(err)
		return err
	}
	if tgt == 0 {
		err := errors.New("target_id is required")
		storeLastError(err)
		return err
	}
	if !IsConnected() {
		err := errors.New("not connected")
		storeLastError(err)
		return err
	}

	payload := normalizeTopicBusPayload(payloadText)
	data := topicbus.PublishReq{
		Topic:   topic,
		Name:    name,
		TS:      time.Now().UnixMilli(),
		Payload: payload,
	}
	body, err := transport.EncodeMessage(topicbus.ActionPublish, data)
	if err != nil {
		storeLastError(err)
		return err
	}

	c := ensureClient()
	hdr := (&header.HeaderTcp{}).
		WithMajor(header.MajorCmd).
		WithSubProto(topicbus.SubProtoTopicBus).
		WithSourceID(src).
		WithTargetID(tgt).
		WithTimestamp(uint32(time.Now().Unix()))
	if err := c.Send(hdr, body); err != nil {
		storeLastError(err)
		return toUIError(err)
	}
	return nil
}

func parseTopicBusTopics(raw string) ([]string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}
	var arr []string
	if err := json.Unmarshal([]byte(raw), &arr); err != nil {
		return nil, errors.New("topics_json is invalid json array")
	}
	out := make([]string, 0, len(arr))
	seen := make(map[string]bool, len(arr))
	for _, t := range arr {
		name := strings.TrimSpace(t)
		if name == "" || seen[name] {
			continue
		}
		seen[name] = true
		out = append(out, name)
	}
	return out, nil
}

func normalizeTopicBusPayload(payloadText string) json.RawMessage {
	payloadText = strings.TrimSpace(payloadText)
	if payloadText == "" {
		return nil
	}
	if json.Valid([]byte(payloadText)) {
		return json.RawMessage(payloadText)
	}
	wrapped, _ := json.Marshal(payloadText)
	return wrapped
}
