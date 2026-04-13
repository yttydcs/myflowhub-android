package hubmobile

// Context: This file supports the Android app or gomobile host flow around keys.

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

const nodeKeysRelPath = "config/node_keys.json"

type nodeKeys struct {
	PrivKey string `json:"privkey"` // base64 DER
	PubKey  string `json:"pubkey"`  // base64 DER
}

var (
	keyMu    sync.Mutex
	nodePriv *ecdsa.PrivateKey
	nodePub  string
)

func EnsureKeys() (string, error) {
	keyMu.Lock()
	if nodePriv != nil && strings.TrimSpace(nodePub) != "" {
		pub := nodePub
		keyMu.Unlock()
		return pub, nil
	}
	keyMu.Unlock()

	wd, err := getWorkDir()
	if err != nil {
		return "", err
	}
	path := filepath.Join(wd, nodeKeysRelPath)

	priv, pub, err := loadOrCreateNodeKeys(path)
	if err != nil {
		return "", err
	}

	keyMu.Lock()
	nodePriv = priv
	nodePub = pub
	keyMu.Unlock()

	logInfo("node keys ensured", "path", path)
	return pub, nil
}

func loadOrCreateNodeKeys(path string) (*ecdsa.PrivateKey, string, error) {
	if priv, pub, err := readNodeKeys(path); err == nil && priv != nil && strings.TrimSpace(pub) != "" {
		return priv, pub, nil
	}
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, "", err
	}
	privDER, _ := x509.MarshalECPrivateKey(priv)
	pubDER, _ := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	privB64 := base64.StdEncoding.EncodeToString(privDER)
	pubB64 := base64.StdEncoding.EncodeToString(pubDER)
	_ = writeNodeKeys(path, nodeKeys{PrivKey: privB64, PubKey: pubB64})
	return priv, pubB64, nil
}

func readNodeKeys(path string) (*ecdsa.PrivateKey, string, error) {
	path = filepath.Clean(path)
	data, err := os.ReadFile(path)
	if err != nil || len(data) == 0 {
		return nil, "", err
	}
	var k nodeKeys
	if err := json.Unmarshal(data, &k); err != nil {
		return nil, "", err
	}
	priv, err := parsePrivKey(k.PrivKey)
	if err != nil {
		return nil, "", err
	}
	if strings.TrimSpace(k.PubKey) == "" {
		return nil, "", errors.New("pubkey empty")
	}
	return priv, k.PubKey, nil
}

func writeNodeKeys(path string, k nodeKeys) error {
	path = filepath.Clean(path)
	_ = os.MkdirAll(filepath.Dir(path), 0o755)
	data, _ := json.MarshalIndent(k, "", "  ")
	return os.WriteFile(path, data, 0o600)
}

func parsePrivKey(b64 string) (*ecdsa.PrivateKey, error) {
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(b64))
	if err != nil {
		return nil, err
	}
	priv, err := x509.ParseECPrivateKey(raw)
	if err != nil {
		return nil, err
	}
	if priv == nil || priv.Curve != elliptic.P256() {
		return nil, errors.New("private key not p256")
	}
	return priv, nil
}

func loginSignBytes(deviceID string, nodeID uint32, ts int64, nonce string) []byte {
	sb := strings.Builder{}
	sb.WriteString("login\n")
	sb.WriteString(strings.TrimSpace(deviceID))
	sb.WriteString("\n")
	sb.WriteString(uintToString(nodeID))
	sb.WriteString("\n")
	sb.WriteString(strconv.FormatInt(ts, 10))
	sb.WriteString("\n")
	sb.WriteString(strings.TrimSpace(nonce))
	return []byte(sb.String())
}

func uintToString(v uint32) string {
	if v == 0 {
		return "0"
	}
	return strconv.FormatUint(uint64(v), 10)
}

func signLogin(priv *ecdsa.PrivateKey, deviceID string, nodeID uint32, ts int64, nonce string) (string, error) {
	if priv == nil {
		return "", errors.New("private key nil")
	}
	msg := loginSignBytes(deviceID, nodeID, ts, nonce)
	hashed := sha256.Sum256(msg)
	sig, err := ecdsa.SignASN1(rand.Reader, priv, hashed[:])
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(sig), nil
}

func generateNonce(n int) string {
	if n <= 0 {
		n = 12
	}
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		// Keep logs only; nonce generation failure is extremely unlikely.
		logWarn("nonce generation failed", "err", err)
		return ""
	}
	return hex.EncodeToString(buf)
}

func getCachedPrivKey() (*ecdsa.PrivateKey, error) {
	keyMu.Lock()
	priv := nodePriv
	keyMu.Unlock()
	if priv != nil {
		return priv, nil
	}
	if _, err := EnsureKeys(); err != nil {
		return nil, err
	}
	keyMu.Lock()
	priv = nodePriv
	keyMu.Unlock()
	if priv == nil {
		return nil, errors.New("private key invalid")
	}
	return priv, nil
}

func nowUnix() int64 {
	return time.Now().Unix()
}
