package parser

import "testing"

func TestPageTokenFromHref(t *testing.T) {
	tests := map[string]string{
		"/opds/search?searchType=books&searchTerm=test&pageNumber=2": "2",
		"https://flibusta.is/opds/new/0/new?pageNumber=7":            "7",
		"/opds/authorsequenceless/123/4":                             "4",
		"/opds/genres":                                               "",
		"not a URL":                                                  "",
	}

	for href, want := range tests {
		if got := pageTokenFromHref(href); got != want {
			t.Errorf("pageTokenFromHref(%q) = %q, want %q", href, got, want)
		}
	}
}
