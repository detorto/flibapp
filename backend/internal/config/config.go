package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port             int
	ListenAddr       string
	FlibustaBaseURL  string
	TorProxy         string
	CacheTTL         time.Duration
	NegativeCacheTTL time.Duration
	SearchTimeout    time.Duration
	SeriesTimeout    time.Duration
	RateLimit        int
	RateLimitWindow  time.Duration
}

func Load() *Config {
	return &Config{
		Port:             envInt("PORT", 8080),
		ListenAddr:       envStr("LISTEN_ADDR", "0.0.0.0"),
		FlibustaBaseURL:  envStr("FLIBUSTA_URL", "https://flibusta.is"),
		TorProxy:         envStr("TOR_PROXY", ""),
		CacheTTL:         time.Duration(envInt("CACHE_TTL_MINUTES", 60)) * time.Minute,
		NegativeCacheTTL: time.Duration(envInt("NEGATIVE_CACHE_TTL_MINUTES", 10)) * time.Minute,
		SearchTimeout:    time.Duration(envInt("SEARCH_TIMEOUT_SECONDS", 15)) * time.Second,
		SeriesTimeout:    time.Duration(envInt("SERIES_SEARCH_TIMEOUT_SECONDS", 8)) * time.Second,
		RateLimit:        envInt("RATE_LIMIT", 300),
		RateLimitWindow:  time.Duration(envInt("RATE_LIMIT_WINDOW_SECONDS", 60)) * time.Second,
	}
}

func envStr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}
