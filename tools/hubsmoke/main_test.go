package main

import (
	"bytes"
	"encoding/binary"
	"testing"
)

func TestEncodeDecodeFrameRoundTrip(t *testing.T) {
	payload := []byte("hello")
	h := newHeader(majorCmd, subprotoManagement)
	h.MsgID = 123
	h.Source = 7
	h.Target = 9
	h.TraceID = 456
	h.Timestamp = 1700000000

	frame, err := encodeFrame(h, payload)
	if err != nil {
		t.Fatalf("encodeFrame: %v", err)
	}
	gotH, gotP, err := decodeFrame(bytes.NewReader(frame))
	if err != nil {
		t.Fatalf("decodeFrame: %v", err)
	}
	if gotH.Magic != magicV2 || gotH.Ver != versionV2 || gotH.HdrLen != headerSize {
		t.Fatalf("unexpected fixed header: %+v", gotH)
	}
	if gotH.TypeFmt != h.TypeFmt || gotH.MsgID != h.MsgID || gotH.Source != h.Source || gotH.Target != h.Target || gotH.TraceID != h.TraceID || gotH.Timestamp != h.Timestamp {
		t.Fatalf("unexpected header: want=%+v got=%+v", h, gotH)
	}
	if !bytes.Equal(gotP, payload) {
		t.Fatalf("unexpected payload: want=%q got=%q", payload, gotP)
	}
}

func TestDecodeFrameAllowsExtendedHeader(t *testing.T) {
	payload := []byte("hi")
	extLen := uint8(headerSize + 8)
	h := newHeader(majorCmd, subprotoAuth)
	h.HdrLen = extLen
	h.MsgID = 1

	// Manually build an extended header (extra bytes are ignored by decoder).
	buf := make([]byte, int(extLen)+len(payload))
	binary.BigEndian.PutUint16(buf[0:2], magicV2)
	buf[2] = versionV2
	buf[3] = extLen
	buf[4] = h.TypeFmt
	buf[5] = h.Flags
	buf[6] = h.HopLimit
	buf[7] = h.RouteFlags
	binary.BigEndian.PutUint32(buf[8:12], h.MsgID)
	binary.BigEndian.PutUint32(buf[12:16], h.Source)
	binary.BigEndian.PutUint32(buf[16:20], h.Target)
	binary.BigEndian.PutUint32(buf[20:24], h.TraceID)
	binary.BigEndian.PutUint32(buf[24:28], h.Timestamp)
	binary.BigEndian.PutUint32(buf[28:32], uint32(len(payload)))
	// buf[32:extLen] left as zeros (extension area)
	copy(buf[int(extLen):], payload)

	gotH, gotP, err := decodeFrame(bytes.NewReader(buf))
	if err != nil {
		t.Fatalf("decodeFrame: %v", err)
	}
	if gotH.HdrLen != extLen {
		t.Fatalf("expected hdrlen=%d got=%d", extLen, gotH.HdrLen)
	}
	if !bytes.Equal(gotP, payload) {
		t.Fatalf("unexpected payload: want=%q got=%q", payload, gotP)
	}
}

func TestDecodeFrameRejectsHugePayload(t *testing.T) {
	// Build a frame with payload_len > maxPayloadLen, without the payload body.
	var buf [headerSize]byte
	binary.BigEndian.PutUint16(buf[0:2], magicV2)
	buf[2] = versionV2
	buf[3] = headerSize
	buf[4] = (majorCmd & 0x03) | ((subprotoManagement & 0x3F) << 2)
	binary.BigEndian.PutUint32(buf[28:32], uint32(maxPayloadLen+1))

	_, _, err := decodeFrame(bytes.NewReader(buf[:]))
	if err == nil {
		t.Fatalf("expected error")
	}
}

