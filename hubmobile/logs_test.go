package hubmobile

import "testing"

func TestLogBuffer_RingAndPull(t *testing.T) {
	b := newLogBuffer(3)

	s1 := b.append("a")
	s2 := b.append("b")
	s3 := b.append("c")
	if s1 != 1 || s2 != 2 || s3 != 3 {
		t.Fatalf("seq unexpected: %d %d %d", s1, s2, s3)
	}

	next, more, out := b.pull(0, 10)
	if more {
		t.Fatalf("expected hasMore=false")
	}
	if next != 3 {
		t.Fatalf("expected next=3, got %d", next)
	}
	if len(out) != 3 || out[0].Seq != 1 || out[2].Seq != 3 {
		t.Fatalf("unexpected out: %+v", out)
	}

	s4 := b.append("d")
	if s4 != 4 {
		t.Fatalf("expected seq=4, got %d", s4)
	}

	next, more, out = b.pull(0, 10)
	if more {
		t.Fatalf("expected hasMore=false after overwrite")
	}
	if next != 4 {
		t.Fatalf("expected next=4, got %d", next)
	}
	if len(out) != 3 || out[0].Seq != 2 || out[2].Seq != 4 {
		t.Fatalf("unexpected out after overwrite: %+v", out)
	}

	next, more, out = b.pull(2, 1)
	if !more {
		t.Fatalf("expected hasMore=true with limit=1")
	}
	if next != 3 {
		t.Fatalf("expected next=3, got %d", next)
	}
	if len(out) != 1 || out[0].Seq != 3 {
		t.Fatalf("unexpected out limited: %+v", out)
	}

	next, more, out = b.pull(4, 10)
	if more || len(out) != 0 || next != 4 {
		t.Fatalf("expected empty pull at cursor=4; next=%d more=%v out=%v", next, more, out)
	}
}

