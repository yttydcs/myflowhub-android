//go:build !android

package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `gomobile_deps` 相关的逻辑。

import (
	// Ensure `golang.org/x/mobile/bind` exists in the module graph.
	// gomobile runs `gobind` on the host during `gomobile bind`, and gobind
	// imports this package via `go/packages`.
	_ "golang.org/x/mobile/bind"
)
