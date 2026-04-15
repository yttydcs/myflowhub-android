package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `hubmobile` 相关的逻辑。

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/yttydcs/myflowhub-server/hubruntime"
)

var (
	mu sync.Mutex
	rt *hubruntime.Runtime
)

type statusDTO struct {
	Running bool `json:"running"`

	Addr   string `json:"addr"`
	NodeID uint32 `json:"node_id"`

	ParentEnabled   bool   `json:"parent_enabled"`
	ParentAddr      string `json:"parent_addr"`
	ParentConnected bool   `json:"parent_connected"`

	WorkDir string `json:"workdir"`

	LastError string `json:"last_error"`
}

// Start 启动 Android 宿主内嵌的 Go Hub runtime，并返回 JSON 状态快照。
func Start(addr, parentAddr, selfID, workDir string, rfcommEnable bool, rfcommUUID string, rfcommInsecure bool) (string, error) {
	mu.Lock()
	defer mu.Unlock()

	if rt != nil {
		return marshalStatus(rt.Status()), nil
	}
	if err := Init(workDir); err != nil {
		storeLastError(err)
		return "", err
	}
	opts := hubruntime.DefaultOptionsFromEnv()
	opts.Addr = addr
	opts.ParentAddr = parentAddr
	opts.ParentEnable = parentAddr != ""
	opts.SelfID = selfID
	opts.WorkDir = workDir
	opts.RFCOMMEnable = rfcommEnable
	opts.RFCOMMUUID = rfcommUUID
	opts.RFCOMMInsecure = rfcommInsecure
	opts.NodeID = 0
	opts.Logger = globalLogger

	r, err := hubruntime.New(opts)
	if err != nil {
		storeLastError(err)
		return "", err
	}
	if err := r.Start(context.Background()); err != nil {
		storeLastError(err)
		return "", err
	}
	rt = r
	return marshalStatus(r.Status()), nil
}

// Stop 停掉当前 Hub runtime，并把全局运行指针清空。
func Stop() (string, error) {
	mu.Lock()
	r := rt
	rt = nil
	mu.Unlock()

	if r == nil {
		return marshalStatus(hubruntime.Status{}), nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := r.Stop(ctx); err != nil {
		storeLastError(err)
		return "", err
	}
	return marshalStatus(r.Status()), nil
}

// Status 导出当前 Hub runtime 的最新状态，供 Kotlin 端轮询。
func Status() string {
	mu.Lock()
	r := rt
	mu.Unlock()
	if r == nil {
		return marshalStatus(hubruntime.Status{})
	}
	return marshalStatus(r.Status())
}

// marshalStatus 把 server runtime.Status 压成 Android 侧稳定依赖的 DTO。
func marshalStatus(st hubruntime.Status) string {
	dto := statusDTO{
		Running: st.Running,

		Addr:   st.Addr,
		NodeID: st.NodeID,

		ParentEnabled:   st.ParentEnabled,
		ParentAddr:      st.ParentAddr,
		ParentConnected: st.ParentConnected,

		WorkDir: st.WorkDir,

		LastError: st.LastError,
	}
	raw, _ := json.Marshal(dto)
	return string(raw)
}

// EnsureLinked is a no-op function to make it obvious in Java/Kotlin that the AAR is present.
func EnsureLinked() error {
	mu.Lock()
	defer mu.Unlock()
	if rt == nil {
		err := errors.New("runtime not started")
		storeLastError(err)
		return err
	}
	return nil
}

// EnsureInit 允许 Android 在真正 Start 前先准备 workDir 和 keys。
// EnsureInit is a convenience no-op to allow Android to set workDir early (even before starting hub runtime).
func EnsureInit(workDir string) error {
	if err := Init(workDir); err != nil {
		storeLastError(err)
		return err
	}
	pub, err := EnsureKeys()
	if err != nil {
		storeLastError(err)
		return err
	}
	logInfo("init ok", "pubkey_prefix", fmt.Sprintf("%.12s", pub))
	return nil
}
