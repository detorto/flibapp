package main

import (
	"log"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/flibusta-reader/backend/internal/api"
	"github.com/flibusta-reader/backend/internal/cache"
	"github.com/flibusta-reader/backend/internal/config"
	"github.com/flibusta-reader/backend/internal/middleware"
	"github.com/flibusta-reader/backend/internal/parser"
	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
)

func main() {
	cfg := config.Load()

	httpClient := buildHTTPClient(cfg)
	client := parser.NewFlibustaClient(cfg.FlibustaBaseURL, httpClient)
	c := cache.New(cfg.CacheTTL)
	h := api.NewHandler(client, c, api.HandlerOptions{
		SearchTimeout:    cfg.SearchTimeout,
		SeriesTimeout:    cfg.SeriesTimeout,
		NegativeCacheTTL: cfg.NegativeCacheTTL,
	})

	r := chi.NewRouter()

	rl := middleware.NewRateLimiter(cfg.RateLimit, cfg.RateLimitWindow)
	r.Use(rl.Middleware)
	r.Use(middleware.CORS)
	r.Use(chimw.Compress(5))
	r.Use(chimw.Recoverer)

	r.Get("/health", h.Health)

	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/search", h.Search)
		r.Get("/search/books", h.SearchBooks)
		r.Get("/search/authors", h.SearchAuthors)
		r.Get("/search/series", h.SearchSeries)

		r.Get("/author/{id}", h.Author)
		r.Get("/author/{id}/books", h.AuthorBooks)

		r.Get("/series/{id}/books", h.SeriesBooks)

		r.Get("/genres", h.Genres)
		r.Get("/genres/*", h.GenreBrowse)

		r.Get("/new", h.New)
		r.Get("/new/books", h.NewBooks)

		r.Get("/download/*", h.Download)
		r.Get("/image/*", h.ImageProxy)

		r.Get("/opds/*", h.OPDSProxy)
	})

	addr := net.JoinHostPort(cfg.ListenAddr, strconv.Itoa(cfg.Port))
	log.Printf("starting server on %s", addr)
	log.Printf("flibusta upstream: %s", cfg.FlibustaBaseURL)
	if cfg.TorProxy != "" {
		log.Printf("using tor proxy: %s", cfg.TorProxy)
	}

	server := &http.Server{
		Addr:              addr,
		Handler:           r,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	if err := server.ListenAndServe(); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

func buildHTTPClient(cfg *config.Config) *http.Client {
	transport := &http.Transport{
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   20,
		MaxConnsPerHost:       40,
		IdleConnTimeout:       90 * time.Second,
		DisableCompression:    false,
		TLSHandshakeTimeout:   10 * time.Second,
		ResponseHeaderTimeout: 60 * time.Second,
	}

	if cfg.TorProxy != "" {
		transport.DialContext = (&net.Dialer{
			Timeout:   30 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext

		proxyRaw := cfg.TorProxy
		if !strings.HasPrefix(proxyRaw, "socks5://") {
			proxyRaw = "socks5://" + proxyRaw
		}
		if parsed, err := url.Parse(proxyRaw); err == nil {
			transport.Proxy = http.ProxyURL(parsed)
		}
	}

	return &http.Client{
		Transport: transport,
		Timeout:   90 * time.Second,
	}
}
