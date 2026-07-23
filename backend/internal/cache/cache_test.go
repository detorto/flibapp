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
