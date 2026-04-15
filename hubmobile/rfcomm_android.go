//go:build android

package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `rfcomm_android` 相关的逻辑。

import "github.com/yttydcs/myflowhub-core/listener/rfcomm_listener"

// SetRFCOMMProvider injects an Android Bluetooth Classic RFCOMM provider.
//
// It must be called from Java/Kotlin before enabling RFCOMM listener/dial,
// otherwise Go will return an explicit "android rfcomm provider not set" error.
func SetRFCOMMProvider(p rfcomm_listener.AndroidRFCOMMProvider) {
	rfcomm_listener.SetAndroidRFCOMMProvider(p)
}
