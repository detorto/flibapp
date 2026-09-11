package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/flibusta-reader/backend/internal/cache"
	"github.com/flibusta-reader/backend/internal/parser"
	"github.com/go-chi/chi/v5"
	"golang.org/x/sync/singleflight"
)

const (
	defaultSearchTimeout    = 15 * time.Second
	defaultSeriesTimeout    = 8 * time.Second
	defaultNegativeCacheTTL = 10 * time.Minute
)

type HandlerOptions struct {
	SearchTimeout    time.Duration
	SeriesTimeout    time.Duration
	NegativeCacheTTL time.Duration
}

type Handler struct {
	client           *parser.FlibustaClient
	cache            *cache.Cache
	searchGroup      singleflight.Group
	searchTimeout    time.Duration
	seriesTimeout    time.Duration
	negativeCacheTTL time.Duration
}

func NewHandler(c *parser.FlibustaClient, cache *cache.Cache, options ...HandlerOptions) *Handler {
	opts := HandlerOptions{
		SearchTimeout:    defaultSearchTimeout,
		SeriesTimeout:    defaultSeriesTimeout,
		NegativeCacheTTL: defaultNegativeCacheTTL,
	}
	if len(options) > 0 {
		if options[0].SearchTimeout > 0 {
			opts.SearchTimeout = options[0].SearchTimeout
		}
		if options[0].SeriesTimeout > 0 {
			opts.SeriesTimeout = options[0].SeriesTimeout
		}
		if options[0].NegativeCacheTTL > 0 {
			opts.NegativeCacheTTL = options[0].NegativeCacheTTL
		}
	}
	return &Handler{
		client:           c,
		cache:            cache,
		searchTimeout:    opts.SearchTimeout,
		seriesTimeout:    opts.SeriesTimeout,
		negativeCacheTTL: opts.NegativeCacheTTL,
	}
}

type cacheLoadResult[T any] struct {
	value     T
	fromCache bool
}

func cachedLoad[T any](
	w http.ResponseWriter,
	h *Handler,
	key string,
	timeout time.Duration,
	ttlFor func(T) time.Duration,
	load func(context.Context) (T, error),
) (T, error) {
	started := time.Now()
	if cached, ok := h.cache.Get(key); ok {
		if result, ok := cached.(T); ok {
			setCacheHeaders(w, "HIT", started)
			return result, nil
		}
	}

	value, err, shared := h.searchGroup.Do(key, func() (any, error) {
		if cached, ok := h.cache.Get(key); ok {
			if result, ok := cached.(T); ok {
				return cacheLoadResult[T]{value: result, fromCache: true}, nil
			}
		}

		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		defer cancel()
		result, err := load(ctx)
		if err != nil {
			return nil, err
		}
		if ttl := ttlFor(result); ttl > 0 {
			h.cache.SetWithTTL(key, result, ttl)
		} else {
			h.cache.Set(key, result)
		}
		return cacheLoadResult[T]{value: result}, nil
	})

	status := "MISS"
	if shared {
		status = "COALESCED"
	}
	if err != nil {
		setCacheHeaders(w, status, started)
		var zero T
		return zero, err
	}
	result := value.(cacheLoadResult[T])
	if result.fromCache {
		status = "HIT"
	}
	setCacheHeaders(w, status, started)
	return result.value, nil
}

func setCacheHeaders(w http.ResponseWriter, status string, started time.Time) {
	w.Header().Set("X-Cache", status)
	w.Header().Set("Server-Timing", fmt.Sprintf("backend;dur=%.1f", float64(time.Since(started).Microseconds())/1000))
}

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, map[string]string{"status": "ok"})
}

func (h *Handler) Search(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}

	cacheKey := "search:" + strings.ToLower(q)
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.Search(q)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result.AuthorsLink != "" || result.BooksLink != "" {
		h.cache.Set(cacheKey, result)
	}
	writeJSON(w, result)
}

func (h *Handler) SearchBooks(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}
	page := r.URL.Query().Get("page")

	cacheKey := "search:books:" + strings.ToLower(q) + ":" + page
	result, err := cachedLoad(w, h, cacheKey, h.searchTimeout,
		func(result *parser.PaginatedBooks) time.Duration {
			if len(result.Books) == 0 && result.NextPage == "" {
				return h.negativeCacheTTL
			}
			return 0
		},
		func(ctx context.Context) (*parser.PaginatedBooks, error) {
			result, err := h.client.SearchBooksContext(ctx, q, page)
			if err == nil && result.Books == nil {
				result.Books = []parser.BookResult{}
			}
			return result, err
		},
	)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	writeJSON(w, result)
}

func (h *Handler) SearchAuthors(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}

	cacheKey := "search:authors:" + strings.ToLower(q)
	result, err := cachedLoad(w, h, cacheKey, h.searchTimeout,
		func(result []parser.AuthorResult) time.Duration {
			if len(result) == 0 {
				return h.negativeCacheTTL
			}
			return 0
		},
		func(ctx context.Context) ([]parser.AuthorResult, error) {
			return h.client.SearchAuthorsContext(ctx, q)
		},
	)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result == nil {
		result = []parser.AuthorResult{}
	}

	writeJSON(w, result)
}

func (h *Handler) SearchSeries(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}

	cacheKey := "search:series:" + strings.ToLower(q)
	result, err := cachedLoad(w, h, cacheKey, h.seriesTimeout,
		func(result []parser.SeriesResult) time.Duration {
			if len(result) == 0 {
				return h.negativeCacheTTL
			}
			return 0
		},
		func(ctx context.Context) ([]parser.SeriesResult, error) {
			return h.client.SearchSeriesContext(ctx, q)
		},
	)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result == nil {
		result = []parser.SeriesResult{}
	}

	writeJSON(w, result)
}

func (h *Handler) SeriesBooks(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	page := r.URL.Query().Get("page")

	cacheKey := "series:books:" + id + ":" + page
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetSeriesBooks(id, page)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result.Books == nil {
		result.Books = []parser.BookResult{}
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) Author(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	cacheKey := "author:" + id
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetAuthor(id)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) AuthorBooks(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	page := r.URL.Query().Get("page")

	cacheKey := "author:books:" + id + ":" + page
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetAuthorBooks(id, page)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) Genres(w http.ResponseWriter, r *http.Request) {
	cacheKey := "genres"
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetGenres()
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) GenreBrowse(w http.ResponseWriter, r *http.Request) {
	genrePath := chi.URLParam(r, "*")
	if genrePath == "" {
		writeError(w, http.StatusBadRequest, "genre path required")
		return
	}

	cacheKey := "genre:browse:" + genrePath
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.BrowseGenre(genrePath)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) New(w http.ResponseWriter, r *http.Request) {
	cacheKey := "new"
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetNew()
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) NewBooks(w http.ResponseWriter, r *http.Request) {
	page := r.URL.Query().Get("page")

	cacheKey := "new:books:" + page
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetNewBooks(page)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) OPDSProxy(w http.ResponseWriter, r *http.Request) {
	path := chi.URLParam(r, "*")
	if path == "" {
		writeError(w, http.StatusBadRequest, "path is required")
		return
	}

	page := r.URL.Query().Get("page")
	fullPath := "/opds/" + path
	if page != "" {
		fullPath += "?pageNumber=" + page
	}

	cacheKey := "opds:" + fullPath
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.GetOPDSFeed(fullPath)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	h.cache.Set(cacheKey, result)
	writeJSON(w, result)
}

func (h *Handler) Download(w http.ResponseWriter, r *http.Request) {
	path := chi.URLParam(r, "*")
	if path == "" {
		writeError(w, http.StatusBadRequest, "path required")
		return
	}

	body, headers, status, err := h.client.ProxyRequest("/b/" + path)
	if err != nil {
		writeError(w, http.StatusBadGateway, "download failed")
		return
	}
	defer body.Close()

	if status != http.StatusOK {
		writeError(w, http.StatusBadGateway, "download failed")
		return
	}

	if ct := headers.Get("Content-Type"); ct != "" {
		w.Header().Set("Content-Type", ct)
	} else {
		w.Header().Set("Content-Type", "application/octet-stream")
	}
	if cd := headers.Get("Content-Disposition"); cd != "" {
		w.Header().Set("Content-Disposition", cd)
	}
	if cl := headers.Get("Content-Length"); cl != "" {
		w.Header().Set("Content-Length", cl)
	}

	io.Copy(w, body)
}

func (h *Handler) ImageProxy(w http.ResponseWriter, r *http.Request) {
	path := chi.URLParam(r, "*")
	if path == "" {
		writeError(w, http.StatusBadRequest, "path required")
		return
	}

	body, headers, status, err := h.client.ProxyRequest("/" + path)
	if err != nil {
		writeError(w, http.StatusBadGateway, "image fetch failed")
		return
	}
	defer body.Close()

	if status != http.StatusOK {
		w.WriteHeader(status)
		return
	}

	if ct := headers.Get("Content-Type"); ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	w.Header().Set("Cache-Control", "public, max-age=86400")
	io.Copy(w, body)
}

func writeJSON(w http.ResponseWriter, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	json.NewEncoder(w).Encode(data)
}

func readSearchQuery(w http.ResponseWriter, r *http.Request) (string, bool) {
	q := strings.Join(strings.Fields(r.URL.Query().Get("q")), " ")
	if q == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return "", false
	}
	if utf8.RuneCountInString(q) > 200 {
		writeError(w, http.StatusBadRequest, "query is too long")
		return "", false
	}
	return q, true
}

func writeError(w http.ResponseWriter, code int, msg string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}
