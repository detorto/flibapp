package api

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/flibusta-reader/backend/internal/cache"
	"github.com/flibusta-reader/backend/internal/parser"
)

const emptyFeed = `<?xml version="1.0" encoding="utf-8"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>`

func TestReadSearchQueryNormalizesWhitespace(t *testing.T) {
	request := httptest.NewRequest("GET", "/?q=%20%20Harry%20%20Potter%20", nil)
	response := httptest.NewRecorder()

	got, ok := readSearchQuery(response, request)

	if !ok || got != "Harry Potter" {
		t.Fatalf("readSearchQuery() = %q, %v; want %q, true", got, ok, "Harry Potter")
	}
}

func TestReadSearchQueryRejectsLongQuery(t *testing.T) {
	request := httptest.NewRequest("GET", "/?q="+strings.Repeat("я", 201), nil)
	response := httptest.NewRecorder()

	if _, ok := readSearchQuery(response, request); ok {
		t.Fatal("readSearchQuery accepted a query longer than 200 characters")
	}
	if response.Code != 400 {
		t.Fatalf("status = %d, want 400", response.Code)
	}
}

func TestSearchBooksCachesSuccessfulEmptyResult(t *testing.T) {
	var requests atomic.Int32
	handler := newTestHandler(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/opds/search" && r.URL.Query().Get("searchType") == "books" {
			requests.Add(1)
		}
		w.Header().Set("Content-Type", "application/atom+xml")
		_, _ = w.Write([]byte(emptyFeed))
	}, HandlerOptions{NegativeCacheTTL: time.Minute})

	first := httptest.NewRecorder()
	handler.SearchBooks(first, httptest.NewRequest("GET", "/?q=missing", nil))
	second := httptest.NewRecorder()
	handler.SearchBooks(second, httptest.NewRequest("GET", "/?q=missing", nil))

	if first.Code != http.StatusOK || second.Code != http.StatusOK {
		t.Fatalf("statuses = %d, %d; want 200, 200", first.Code, second.Code)
	}
	if got := requests.Load(); got != 1 {
		t.Fatalf("upstream requests = %d, want 1", got)
	}
	if got := first.Header().Get("X-Cache"); got != "MISS" {
		t.Fatalf("first X-Cache = %q, want MISS", got)
	}
	if got := second.Header().Get("X-Cache"); got != "HIT" {
		t.Fatalf("second X-Cache = %q, want HIT", got)
	}
}

func TestCachedLoadCoalescesConcurrentMisses(t *testing.T) {
	handler := &Handler{cache: cache.New(time.Hour)}
	started := make(chan struct{})
	release := make(chan struct{})
	var once sync.Once
	var loads atomic.Int32
	loader := func(context.Context) (string, error) {
		loads.Add(1)
		once.Do(func() { close(started) })
		<-release
		return "result", nil
	}

	const callers = 8
	var ready sync.WaitGroup
	var done sync.WaitGroup
	ready.Add(callers)
	done.Add(callers)
	begin := make(chan struct{})
	for range callers {
		go func() {
			defer done.Done()
			ready.Done()
			<-begin
			recorder := httptest.NewRecorder()
			result, err := cachedLoad(recorder, handler, "same-key", time.Second, func(string) time.Duration { return 0 }, loader)
			if err != nil || result != "result" {
				t.Errorf("cachedLoad() = %q, %v", result, err)
			}
		}()
	}
	ready.Wait()
	close(begin)
	<-started
	time.Sleep(25 * time.Millisecond)
	close(release)
	done.Wait()

	if got := loads.Load(); got != 1 {
		t.Fatalf("loader calls = %d, want 1", got)
	}
}

func TestSearchSeriesHonorsShortTimeout(t *testing.T) {
	handler := newTestHandler(t, func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/opds/sequencesindex/") {
			<-r.Context().Done()
			return
		}
		w.Header().Set("Content-Type", "application/atom+xml")
		_, _ = w.Write([]byte(emptyFeed))
	}, HandlerOptions{SeriesTimeout: 30 * time.Millisecond})

	started := time.Now()
	response := httptest.NewRecorder()
	handler.SearchSeries(response, httptest.NewRequest("GET", "/?q=slow", nil))

	if response.Code != http.StatusBadGateway {
		t.Fatalf("status = %d, want 502", response.Code)
	}
	if elapsed := time.Since(started); elapsed > 500*time.Millisecond {
		t.Fatalf("series timeout took %s, want under 500ms", elapsed)
	}
	if response.Header().Get("Server-Timing") == "" {
		t.Fatal("Server-Timing header is missing")
	}
}

func newTestHandler(t *testing.T, upstream http.HandlerFunc, options HandlerOptions) *Handler {
	t.Helper()
	server := httptest.NewServer(upstream)
	t.Cleanup(server.Close)
	client := parser.NewFlibustaClient(server.URL, server.Client())
	return NewHandler(client, cache.New(time.Hour), options)
}
