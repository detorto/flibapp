package api

import (
	"net/http/httptest"
	"strings"
	"testing"
)

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
