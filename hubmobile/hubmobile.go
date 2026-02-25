package hubmobile

import (
	"context"
	"encoding/json"
	"errors"
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

func Start(addr, parentAddr, selfID, workDir string) (string, error) {
	mu.Lock()
	defer mu.Unlock()

	if rt != nil {
		return marshalStatus(rt.Status()), nil
	}
	opts := hubruntime.DefaultOptionsFromEnv()
	opts.Addr = addr
	opts.ParentAddr = parentAddr
	opts.ParentEnable = parentAddr != ""
	opts.SelfID = selfID
	opts.WorkDir = workDir
	opts.NodeID = 0

	r, err := hubruntime.New(opts)
	if err != nil {
		return "", err
	}
	if err := r.Start(context.Background()); err != nil {
		return "", err
	}
	rt = r
	return marshalStatus(r.Status()), nil
}

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
		return "", err
	}
	return marshalStatus(r.Status()), nil
}

func Status() string {
	mu.Lock()
	r := rt
	mu.Unlock()
	if r == nil {
		return marshalStatus(hubruntime.Status{})
	}
	return marshalStatus(r.Status())
}

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
		return errors.New("runtime not started")
	}
	return nil
}
