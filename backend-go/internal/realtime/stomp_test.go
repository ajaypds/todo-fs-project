package realtime

import "testing"

func TestParseFramesAndSerialize(t *testing.T) {
	frames := ParseFrames("SUBSCRIBE\nid:sub-0\ndestination:/topic/a\n\n\x00\nSEND\ndestination:/app/x\n\nhello\x00")
	if len(frames) != 2 {
		t.Fatalf("expected 2 frames, got %d", len(frames))
	}
	if frames[0].Command != "SUBSCRIBE" || frames[0].Header("id") != "sub-0" || frames[0].Header("destination") != "/topic/a" {
		t.Fatalf("bad first frame: %+v", frames[0])
	}
	if frames[1].Body != "hello" {
		t.Fatalf("bad body %q", frames[1].Body)
	}
	if got := errorFrame("m").String(); got != "ERROR\nmessage:m\ncontent-length:0\n\n\x00" {
		t.Fatalf("error frame %q", got)
	}
}
