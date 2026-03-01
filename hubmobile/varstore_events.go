package hubmobile

import (
	"encoding/json"
	"strings"
	"sync"

	core "github.com/yttydcs/myflowhub-core"
	"github.com/yttydcs/myflowhub-proto/protocol/varstore"
)

const (
	defaultVarStoreEventCapacity = 2_000
	defaultVarStoreEventPull     = 200
	maxVarStoreEventPull         = 2_000

	// Protect against accidentally buffering huge payloads in memory.
	maxVarStoreEventPayloadBytes = 32 * 1024
)

type varStoreEvent struct {
	Seq     int64           `json:"seq"`
	Action  string          `json:"action"`
	Source  uint32          `json:"source_id,omitempty"`
	Target  uint32          `json:"target_id,omitempty"`
	Data    json.RawMessage `json:"data,omitempty"`
	Dropped bool            `json:"dropped,omitempty"`
}

type varStoreEventPullResp struct {
	Cursor     int64          `json:"cursor"`
	NextCursor int64          `json:"next_cursor"`
	HasMore    bool           `json:"has_more"`
	Events     []varStoreEvent `json:"events"`
}

type varStoreEventBuffer struct {
	mu      sync.Mutex
	cap     int
	nextSeq int64
	events  []varStoreEvent
	start   int
	count   int
}

func newVarStoreEventBuffer(capacity int) *varStoreEventBuffer {
	if capacity <= 0 {
		capacity = defaultVarStoreEventCapacity
	}
	return &varStoreEventBuffer{
		cap:    capacity,
		events: make([]varStoreEvent, capacity),
	}
}

func (b *varStoreEventBuffer) append(evt varStoreEvent) int64 {
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

func (b *varStoreEventBuffer) pull(cursor int64, limit int) (next int64, hasMore bool, out []varStoreEvent) {
	if limit <= 0 {
		limit = defaultVarStoreEventPull
	}
	if limit > maxVarStoreEventPull {
		limit = maxVarStoreEventPull
	}
	if cursor < 0 {
		cursor = 0
	}

	b.mu.Lock()
	defer b.mu.Unlock()

	if b.count == 0 {
		return cursor, false, nil
	}

	out = make([]varStoreEvent, 0, limit)
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

var globalVarStoreEvents = newVarStoreEventBuffer(defaultVarStoreEventCapacity)

func captureVarStoreUnmatchedFrame(hdr core.IHeader, payload []byte) {
	if hdr == nil || len(payload) == 0 {
		return
	}
	if hdr.SubProto() != varstore.SubProtoVarStore {
		return
	}
	if len(payload) > maxVarStoreEventPayloadBytes {
		// Keep a marker so UI can at least see something happened.
		globalVarStoreEvents.append(varStoreEvent{
			Action:  "dropped",
			Source:  hdr.SourceID(),
			Target:  hdr.TargetID(),
			Dropped: true,
		})
		return
	}

	var msg varstore.Message
	if err := json.Unmarshal(payload, &msg); err != nil {
		return
	}
	action := strings.TrimSpace(msg.Action)
	if action == "" {
		return
	}

	switch action {
	case varstore.ActionVarChanged,
		varstore.ActionVarDeleted,
		varstore.ActionNotifySet,
		varstore.ActionNotifyRevoke,
		varstore.ActionUpSet,
		varstore.ActionUpRevoke:
	default:
		return
	}

	dataCopy := append([]byte(nil), msg.Data...)
	globalVarStoreEvents.append(varStoreEvent{
		Action: action,
		Source: hdr.SourceID(),
		Target: hdr.TargetID(),
		Data:   dataCopy,
	})
}

// VarStoreEventsPull pulls captured varstore notify frames after cursor (exclusive).
// - cursor: last seen seq; use "0" to read from current buffer start.
// - limit: max returned events (clamped).
func VarStoreEventsPull(cursor, limit string) string {
	cur, err := parseInt64("cursor", cursor)
	if err != nil {
		cur = 0
	}
	lim, err := parseInt("limit", limit)
	if err != nil {
		lim = defaultVarStoreEventPull
	}

	next, more, events := globalVarStoreEvents.pull(cur, lim)
	resp := varStoreEventPullResp{
		Cursor:     cur,
		NextCursor: next,
		HasMore:    more,
		Events:     events,
	}
	raw, _ := json.Marshal(resp)
	return string(raw)
}

