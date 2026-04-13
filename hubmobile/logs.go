package hubmobile

// Context: This file supports the Android app or gomobile host flow around logs.

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"reflect"
	"strings"
	"sync"
	"time"
)

const (
	defaultLogCapacity = 10_000
	defaultLogPull     = 200
	maxLogPull         = 2_000
)

type logLine struct {
	Seq  int64  `json:"seq"`
	Line string `json:"line"`
}

type logPullResp struct {
	Cursor     int64     `json:"cursor"`
	NextCursor int64     `json:"next_cursor"`
	HasMore    bool      `json:"has_more"`
	Lines      []logLine `json:"lines"`
}

type logBuffer struct {
	mu      sync.Mutex
	cap     int
	nextSeq int64
	lines   []logLine
	start   int // oldest index
	count   int // number of valid items
}

func newLogBuffer(capacity int) *logBuffer {
	if capacity <= 0 {
		capacity = defaultLogCapacity
	}
	return &logBuffer{
		cap:   capacity,
		lines: make([]logLine, capacity),
	}
}

func (b *logBuffer) append(line string) int64 {
	line = strings.TrimRight(line, "\r\n")

	b.mu.Lock()
	b.nextSeq++
	seq := b.nextSeq

	entry := logLine{Seq: seq, Line: line}
	if b.cap <= 0 {
		b.mu.Unlock()
		return seq
	}
	if b.count < b.cap {
		idx := (b.start + b.count) % b.cap
		b.lines[idx] = entry
		b.count++
	} else {
		// Overwrite oldest.
		b.lines[b.start] = entry
		b.start = (b.start + 1) % b.cap
	}
	b.mu.Unlock()
	return seq
}

func (b *logBuffer) pull(cursor int64, limit int) (next int64, hasMore bool, out []logLine) {
	if limit <= 0 {
		limit = defaultLogPull
	}
	if limit > maxLogPull {
		limit = maxLogPull
	}
	if cursor < 0 {
		cursor = 0
	}

	b.mu.Lock()
	defer b.mu.Unlock()

	if b.count == 0 {
		return cursor, false, nil
	}

	out = make([]logLine, 0, limit)
	foundMore := false
	for i := 0; i < b.count; i++ {
		idx := (b.start + i) % b.cap
		line := b.lines[idx]
		if line.Seq <= cursor {
			continue
		}
		if len(out) < limit {
			out = append(out, line)
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

var (
	globalLogs   = newLogBuffer(defaultLogCapacity)
	globalLogger = slog.New(newRingHandler(globalLogs))
)

type ringHandler struct {
	logs  *logBuffer
	attrs []slog.Attr
	group string
}

func newRingHandler(logs *logBuffer) *ringHandler {
	return &ringHandler{logs: logs}
}

func (h *ringHandler) Enabled(_ context.Context, _ slog.Level) bool {
	// Keep all levels for now; log buffer is bounded.
	return true
}

func (h *ringHandler) Handle(_ context.Context, r slog.Record) error {
	var sb strings.Builder
	// Keep time in local timezone for easier reading on device.
	sb.WriteString(r.Time.Local().Format(time.RFC3339Nano))
	sb.WriteString(" ")
	sb.WriteString(levelText(r.Level))
	sb.WriteString(" ")
	sb.WriteString(strings.TrimSpace(r.Message))

	appendAttrs(&sb, h.group, h.attrs)
	r.Attrs(func(a slog.Attr) bool {
		appendAttr(&sb, h.group, a)
		return true
	})

	h.logs.append(sb.String())
	return nil
}

func (h *ringHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	if len(attrs) == 0 {
		return h
	}
	cp := *h
	cp.attrs = append(cloneAttrs(h.attrs), attrs...)
	return &cp
}

func (h *ringHandler) WithGroup(name string) slog.Handler {
	name = strings.TrimSpace(name)
	if name == "" {
		return h
	}
	cp := *h
	if cp.group == "" {
		cp.group = name
	} else {
		cp.group = cp.group + "." + name
	}
	return &cp
}

func cloneAttrs(attrs []slog.Attr) []slog.Attr {
	if len(attrs) == 0 {
		return nil
	}
	cp := make([]slog.Attr, len(attrs))
	copy(cp, attrs)
	return cp
}

func appendAttrs(sb *strings.Builder, group string, attrs []slog.Attr) {
	for _, a := range attrs {
		appendAttr(sb, group, a)
	}
}

func appendAttr(sb *strings.Builder, group string, a slog.Attr) {
	key := strings.TrimSpace(a.Key)
	if key == "" {
		return
	}
	if group != "" {
		key = group + "." + key
	}
	sb.WriteString(" ")
	sb.WriteString(key)
	sb.WriteString("=")
	sb.WriteString(formatValue(a.Value.Resolve()))
}

func levelText(level slog.Level) string {
	switch {
	case level <= slog.LevelDebug:
		return "DEBUG"
	case level <= slog.LevelInfo:
		return "INFO"
	case level <= slog.LevelWarn:
		return "WARN"
	default:
		return "ERROR"
	}
}

func formatValue(v slog.Value) string {
	switch v.Kind() {
	case slog.KindString:
		return fmt.Sprintf("%q", v.String())
	case slog.KindInt64:
		return fmt.Sprintf("%d", v.Int64())
	case slog.KindUint64:
		return fmt.Sprintf("%d", v.Uint64())
	case slog.KindBool:
		if v.Bool() {
			return "true"
		}
		return "false"
	case slog.KindFloat64:
		return fmt.Sprintf("%g", v.Float64())
	case slog.KindTime:
		return v.Time().Format(time.RFC3339Nano)
	case slog.KindDuration:
		return v.Duration().String()
	case slog.KindAny:
		any := v.Any()
		if any == nil {
			return "null"
		}
		// Avoid huge logs and reflection-heavy formatting.
		switch t := any.(type) {
		case string:
			return fmt.Sprintf("%q", t)
		case error:
			return fmt.Sprintf("%q", t.Error())
		case fmt.Stringer:
			return fmt.Sprintf("%q", t.String())
		default:
			// Fall back to type name only to keep logs stable & bounded.
			return fmt.Sprintf("%q", reflect.TypeOf(any).String())
		}
	default:
		return fmt.Sprintf("%q", v.String())
	}
}

// LogsPull pulls log lines after cursor (exclusive).
// - cursor: last seen seq; use "0" to read from current buffer start.
// - limit: max returned lines (clamped).
func LogsPull(cursor, limit string) string {
	cur, err := parseInt64("cursor", cursor)
	if err != nil {
		cur = 0
	}
	lim, err := parseInt("limit", limit)
	if err != nil {
		lim = defaultLogPull
	}

	next, more, lines := globalLogs.pull(cur, lim)
	resp := logPullResp{
		Cursor:     cur,
		NextCursor: next,
		HasMore:    more,
		Lines:      lines,
	}
	raw, _ := json.Marshal(resp)
	return string(raw)
}

func logInfo(msg string, args ...any) {
	globalLogger.Info(msg, args...)
}

func logWarn(msg string, args ...any) {
	globalLogger.Warn(msg, args...)
}

func logError(msg string, args ...any) {
	globalLogger.Error(msg, args...)
}
