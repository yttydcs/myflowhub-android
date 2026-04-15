package hubmobile

// 本文件承载 Android `hubmobile` 桥接中与 `workdir` 相关的逻辑。

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

var (
	workDirMu sync.Mutex
	workDir   string
)

func Init(workdir string) error {
	workdir = strings.TrimSpace(workdir)
	if workdir == "" {
		return errors.New("workDir is required")
	}
	abs := workdir
	if !filepath.IsAbs(abs) {
		if wd, err := os.Getwd(); err == nil && strings.TrimSpace(wd) != "" {
			abs = filepath.Join(wd, workdir)
		}
	}
	if err := os.MkdirAll(abs, 0o755); err != nil {
		return err
	}
	workDirMu.Lock()
	workDir = abs
	workDirMu.Unlock()
	return nil
}

func getWorkDir() (string, error) {
	workDirMu.Lock()
	wd := workDir
	workDirMu.Unlock()
	if strings.TrimSpace(wd) == "" {
		return "", errors.New("workDir not initialized; call Init(workDir) first")
	}
	return wd, nil
}
