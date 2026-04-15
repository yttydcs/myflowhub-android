package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `util` 相关的逻辑。

import (
	"context"
	"errors"
	"strconv"
	"strings"
)

func parseUint32(name, raw string) (uint32, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, nil
	}
	n, err := strconv.ParseUint(raw, 10, 32)
	if err != nil {
		return 0, errors.New(name + " invalid")
	}
	return uint32(n), nil
}

func parseUint8(name, raw string) (uint8, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, nil
	}
	n, err := strconv.ParseUint(raw, 10, 8)
	if err != nil {
		return 0, errors.New(name + " invalid")
	}
	return uint8(n), nil
}

func parseInt64(name, raw string) (int64, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, nil
	}
	n, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return 0, errors.New(name + " invalid")
	}
	return n, nil
}

func parseInt(name, raw string) (int, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, nil
	}
	n, err := strconv.ParseInt(raw, 10, 32)
	if err != nil {
		return 0, errors.New(name + " invalid")
	}
	return int(n), nil
}

func toUIError(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return errors.New("request timed out")
	}
	if errors.Is(err, context.Canceled) {
		return errors.New("request canceled")
	}
	msg := strings.ToLower(strings.TrimSpace(err.Error()))
	switch {
	case strings.Contains(msg, "not connected"):
		return errors.New("not connected")
	case strings.Contains(msg, "尚未连接"):
		return errors.New("not connected")
	case strings.Contains(msg, "connection") && strings.Contains(msg, "closed"):
		return errors.New("connection closed")
	default:
		return err
	}
}
