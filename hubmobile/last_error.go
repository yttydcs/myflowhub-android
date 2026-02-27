package hubmobile

import "sync/atomic"

var lastErr atomic.Value // string

func storeLastError(err error) {
	if err == nil {
		return
	}
	lastErr.Store(err.Error())
}

func GetLastError() string {
	if v := lastErr.Load(); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

