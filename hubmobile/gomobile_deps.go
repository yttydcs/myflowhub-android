//go:build !android

package hubmobile

import (
	// Ensure `golang.org/x/mobile/bind` exists in the module graph.
	// gomobile runs `gobind` on the host during `gomobile bind`, and gobind
	// imports this package via `go/packages`.
	_ "golang.org/x/mobile/bind"
)

