package hubmobile

import (
	"encoding/json"
	"strings"
	"sync"

	core "github.com/yttydcs/myflowhub-core"
	"github.com/yttydcs/myflowhub-core/header"
	"github.com/yttydcs/myflowhub-proto/protocol/topicbus"
)

const (
	defaultTopicBusEventCapacity = 2_000
	defaultTopicBusEventPull     = 200
	maxTopicBusEventPull         = 2_000

	// Protect against accidentally buffering huge payloads in memory.
	maxTopicBusEventPayloadBytes = 32 * 1024
)

type topicBusEvent struct {
	Seq     int64           `json:"seq"`
	Topic   string          `json:"topic,omitempty"`
	Name    string          `json:"name,omitempty"`
	TS      int64           `json:"ts,omitempty"`
	Source  uint32          `json:"source_id,omitempty"`
	Target  uint32          `json:"target_id,omitempty"`
	Data    json.RawMessage `json:"data,omitempty"`
	Dropped bool            `json:"dropped,omitempty"`
}

type topicBusEventPullResp struct {
	Cursor     int64           `json:"cursor"`
	NextCursor int64           `json:"next_cursor"`
	HasMore    bool            `json:"has_more"`
	Events     []topicBusEvent `json:"events"`
}

type topicBusEventBuffer struct {
	mu      sync.Mutex
	cap     int
	nextSeq int64
	events  []topicBusEvent
	start   int
	count   int
}

func newTopicBusEventBuffer(capacity int) *topicBusEventBuffer {
	if capacity <= 0 {
		capacity = defaultTopicBusEventCapacity
	}
	return &topicBusEventBuffer{
		cap:    capacity,
		events: make([]topicBusEvent, capacity),
	}
}

func (b *topicBusEventBuffer) append(evt topicBusEvent) int64 {
	b.mu.Lock()
	defer b.mu.Unlock()

	b.nextSeq++
	evt.Seq = b.nextSeq

	if b.cap <= 0 {
		return evt.Seq
	}
	if b.count < b.cap {
		idx := (b.start + b.count) % b.cap
		b.events[idx] = evt
		b.count++
	} else {
		b.events[b.start] = evt
		b.start = (b.start + 1) % b.cap
	}
	return evt.Seq
}

func (b *topicBusEventBuffer) pull(cursor int64, limit int) (next int64, hasMore bool, out []topicBusEvent) {
	if limit <= 0 {
		limit = defaultTopicBusEventPull
	}
	if limit > maxTopicBusEventPull {
		limit = maxTopicBusEventPull
	}
	if cursor < 0 {
		cursor = 0
	}

	b.mu.Lock()
	defer b.mu.Unlock()

	if b.count == 0 {
		return cursor, false, nil
	}

	out = make([]topicBusEvent, 0, limit)
	foundMore := false
	for i := 0; i < b.count; i++ {
		idx := (b.start + i) % b.cap
		evt := b.events[idx]
		if evt.Seq <= cursor {
			continue
		}
		if len(out) < limit {
			out = append(out, evt)
		} else {
			foundMore = true
			break
		}
	}
	if len(out) == 0 {
		return cursor, false, nil
	}
	next = out[len(out)-1].Seq
	hasMore = foundMore
	return next, hasMore, out
}

var globalTopicBusEvents = newTopicBusEventBuffer(defaultTopicBusEventCapacity)

func captureTopicBusUnmatchedFrame(hdr core.IHeader, payload []byte) {
	if hdr == nil || len(payload) == 0 {
		return
	}
	if hdr.Major() != header.MajorMsg {
		return
	}
	if hdr.SubProto() != topicbus.SubProtoTopicBus {
		return
	}
	if len(payload) > maxTopicBusEventPayloadBytes {
		globalTopicBusEvents.append(topicBusEvent{
			Source:  hdr.SourceID(),
			Target:  hdr.TargetID(),
			Dropped: true,
		})
		return
	}

	var msg topicbus.Message
	if err := json.Unmarshal(payload, &msg); err != nil {
		return
	}
	action := strings.TrimSpace(msg.Action)
	if action != topicbus.ActionPublish {
		return
	}
	if len(msg.Data) == 0 {
		return
	}

	var data topicbus.PublishReq
	if err := json.Unmarshal(msg.Data, &data); err != nil {
		return
	}
	data.Topic = strings.TrimSpace(data.Topic)
	data.Name = strings.TrimSpace(data.Name)
	if data.Topic == "" || data.Name == "" {
		return
	}

	dataCopy := append([]byte(nil), msg.Data...)
	globalTopicBusEvents.append(topicBusEvent{
		Topic:  data.Topic,
		Name:   data.Name,
		TS:     data.TS,
		Source: hdr.SourceID(),
		Target: hdr.TargetID(),
		Data:   dataCopy,
	})
}

// TopicBusEventsPull pulls captured topicbus publish frames after cursor (exclusive).
// - cursor: last seen seq; use "0" to read from current buffer start.
// - limit: max returned events (clamped).
func TopicBusEventsPull(cursor, limit string) string {
	cur, err := parseInt64("cursor", cursor)
	if err != nil {
		cur = 0
	}
	lim, err := parseInt("limit", limit)
	if err != nil {
		lim = defaultTopicBusEventPull
	}

	next, more, events := globalTopicBusEvents.pull(cur, lim)
	resp := topicBusEventPullResp{
		Cursor:     cur,
		NextCursor: next,
		HasMore:    more,
		Events:     events,
	}
	raw, _ := json.Marshal(resp)
	return string(raw)
}
