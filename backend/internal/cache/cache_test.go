package cache

import (
	"fmt"
	"testing"
	"time"
)

func TestCacheIsBounded(t *testing.T) {
	c := New(time.Hour)

	for i := 0; i <= maxItems; i++ {
		c.Set(fmt.Sprintf("key-%d", i), i)
	}

	if got := len(c.items); got != maxItems {
		t.Fatalf("cache size = %d, want %d", got, maxItems)
	}
	if _, ok := c.Get(fmt.Sprintf("key-%d", maxItems)); !ok {
		t.Fatal("newest cache entry was evicted")
	}
}

func TestSetWithTTLExpiresEntry(t *testing.T) {
	c := New(time.Hour)
	c.SetWithTTL("short", "value", 10*time.Millisecond)

	if _, ok := c.Get("short"); !ok {
		t.Fatal("entry expired before its custom TTL")
	}
	time.Sleep(20 * time.Millisecond)
	if _, ok := c.Get("short"); ok {
		t.Fatal("entry remained fresh after its custom TTL")
	}
}
