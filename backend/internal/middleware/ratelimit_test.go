package middleware

import (
	"net/http/httptest"
	"testing"
)

func TestExtractIPTrustsProxyHeaderOnlyFromLoopback(t *testing.T) {
	proxied := httptest.NewRequest("GET", "/", nil)
	proxied.RemoteAddr = "127.0.0.1:12345"
	proxied.Header.Set("X-Forwarded-For", "203.0.113.10")
	if got := extractIP(proxied); got != "203.0.113.10" {
		t.Fatalf("proxied IP = %q, want 203.0.113.10", got)
	}

	direct := httptest.NewRequest("GET", "/", nil)
	direct.RemoteAddr = "198.51.100.20:12345"
	direct.Header.Set("X-Forwarded-For", "203.0.113.10")
	if got := extractIP(direct); got != "198.51.100.20" {
		t.Fatalf("direct IP = %q, want 198.51.100.20", got)
	}
}
