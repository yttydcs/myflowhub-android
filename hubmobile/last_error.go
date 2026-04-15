package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `last_error` 相关的逻辑。

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
