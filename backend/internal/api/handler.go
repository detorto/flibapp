package api

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/flibusta-reader/backend/internal/cache"
	"github.com/flibusta-reader/backend/internal/parser"
	"github.com/go-chi/chi/v5"
)

type Handler struct {
	client *parser.FlibustaClient
	cache  *cache.Cache
}

func NewHandler(c *parser.FlibustaClient, cache *cache.Cache) *Handler {
	return &Handler{client: c, cache: cache}
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
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.SearchBooks(q, page)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result.Books == nil {
		result.Books = []parser.BookResult{}
	}

	if len(result.Books) > 0 || result.NextPage != "" {
		h.cache.Set(cacheKey, result)
	}
	writeJSON(w, result)
}

func (h *Handler) SearchAuthors(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}

	cacheKey := "search:authors:" + strings.ToLower(q)
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.SearchAuthors(q)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result == nil {
		result = []parser.AuthorResult{}
	}

	if len(result) > 0 {
		h.cache.Set(cacheKey, result)
	}
	writeJSON(w, result)
}

func (h *Handler) SearchSeries(w http.ResponseWriter, r *http.Request) {
	q, ok := readSearchQuery(w, r)
	if !ok {
		return
	}

	cacheKey := "search:series:" + strings.ToLower(q)
	if v, ok := h.cache.Get(cacheKey); ok {
		writeJSON(w, v)
		return
	}

	result, err := h.client.SearchSeries(q)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream error")
		return
	}

	if result == nil {
		result = []parser.SeriesResult{}
	}

	if len(result) > 0 {
		h.cache.Set(cacheKey, result)
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
