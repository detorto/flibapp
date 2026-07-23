package parser

import (
	"encoding/xml"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

var authorIDRe = regexp.MustCompile(`/a/(\d+)`)
var bookIDRe = regexp.MustCompile(`/b/(\d+)`)
var sequenceIDRe = regexp.MustCompile(`sequence:(\d+)`)

const maxFeedSize = 16 << 20

type genreInfo struct {
	Path  string
	Count string
}

type FlibustaClient struct {
	baseURL    string
	httpClient *http.Client

	genreOnce        sync.Once
	genreMu          sync.RWMutex
	genreIndex       map[string]genreInfo
	genreCountByPath map[string]string
	topGenreStats    map[string]string // top-level genre name → "N подкатегорий"
}

func NewFlibustaClient(baseURL string, client *http.Client) *FlibustaClient {
	c := &FlibustaClient{
		baseURL:          strings.TrimRight(baseURL, "/"),
		httpClient:       client,
		genreIndex:       make(map[string]genreInfo),
		genreCountByPath: make(map[string]string),
		topGenreStats:    make(map[string]string),
	}
	go c.buildGenreIndex()
	return c
}

func (c *FlibustaClient) fetchFeed(path string) (*Feed, error) {
	fullURL := c.baseURL + path

	req, err := http.NewRequest("GET", fullURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("User-Agent", "FlibustaReader/1.0 OPDS Client")
	req.Header.Set("Accept", "application/atom+xml, application/xml, text/xml")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch %s: %w", fullURL, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("status %d for %s", resp.StatusCode, fullURL)
	}

	var feed Feed
	if err := xml.NewDecoder(io.LimitReader(resp.Body, maxFeedSize)).Decode(&feed); err != nil {
		return nil, fmt.Errorf("decode xml: %w", err)
	}
	return &feed, nil
}

func (c *FlibustaClient) ProxyRequest(path string) (io.ReadCloser, http.Header, int, error) {
	fullURL := c.baseURL + path

	req, err := http.NewRequest("GET", fullURL, nil)
	if err != nil {
		return nil, nil, 0, err
	}
	req.Header.Set("User-Agent", "FlibustaReader/1.0")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, nil, 0, err
	}
	return resp.Body, resp.Header, resp.StatusCode, nil
}

// Search returns disambiguation: links to author search and book search
func (c *FlibustaClient) Search(term string) (*SearchDisambiguation, error) {
	feed, err := c.fetchFeed("/opds/search?searchTerm=" + url.QueryEscape(term))
	if err != nil {
		return nil, err
	}

	result := &SearchDisambiguation{}
	for _, e := range feed.Entries {
		link := firstCatalogLink(e.Links)
		switch {
		case strings.Contains(e.ID, "author"):
			result.AuthorsLink = link
		case strings.Contains(e.ID, "title"):
			result.BooksLink = link
		}
	}
	return result, nil
}

func (c *FlibustaClient) SearchBooks(term string, page string) (*PaginatedBooks, error) {
	path := "/opds/search?searchType=books&searchTerm=" + url.QueryEscape(term)
	if page != "" {
		path += "&pageNumber=" + page
	}

	feed, err := c.fetchFeed(path)
	if err != nil {
		return nil, err
	}

	result := &PaginatedBooks{}
	for _, e := range feed.Entries {
		if book := entryToBook(e); book != nil {
			result.Books = append(result.Books, *book)
		}
	}

	for _, l := range feed.Links {
		if l.Rel == "next" {
			result.NextPage = pageTokenFromHref(l.Href)
		}
	}

	return result, nil
}

func (c *FlibustaClient) SearchAuthors(term string) ([]AuthorResult, error) {
	feed, err := c.fetchFeed("/opds/search?searchType=authors&searchTerm=" + url.QueryEscape(term))
	if err != nil {
		return nil, err
	}

	var authors []AuthorResult
	for _, e := range feed.Entries {
		if a := entryToAuthor(e); a != nil {
			authors = append(authors, *a)
		}
	}
	return authors, nil
}

func (c *FlibustaClient) SearchSeries(term string) ([]SeriesResult, error) {
	feed, err := c.fetchFeed("/opds/sequencesindex/" + url.PathEscape(term))
	if err != nil {
		return nil, err
	}

	var series []SeriesResult
	for _, e := range feed.Entries {
		if s := entryToSeries(e); s != nil {
			series = append(series, *s)
		}
	}

	if len(series) == 0 {
		for _, e := range feed.Entries {
			subLink := firstCatalogLink(e.Links)
			if subLink == "" {
				continue
			}
			subFeed, err := c.fetchFeed(subLink)
			if err != nil {
				continue
			}
			for _, sub := range subFeed.Entries {
				if s := entryToSeries(sub); s != nil {
					series = append(series, *s)
				}
			}
		}
	}

	return series, nil
}

func (c *FlibustaClient) GetSeriesBooks(id string, page string) (*PaginatedBooks, error) {
	path := "/opds/sequencebooks/" + id
	if page != "" {
		path += "?pageNumber=" + page
	}

	feed, err := c.fetchFeed(path)
	if err != nil {
		return nil, err
	}

	result := &PaginatedBooks{}
	for _, e := range feed.Entries {
		if book := entryToBook(e); book != nil {
			result.Books = append(result.Books, *book)
		}
	}
	for _, l := range feed.Links {
		if l.Rel == "next" {
			result.NextPage = pageTokenFromHref(l.Href)
		}
	}
	return result, nil
}

func (c *FlibustaClient) GetAuthor(id string) (*AuthorDetail, error) {
	feed, err := c.fetchFeed("/opds/author/" + id)
	if err != nil {
		return nil, err
	}

	detail := &AuthorDetail{ID: id, Name: strings.TrimPrefix(feed.Title, "Книги автора ")}

	for _, e := range feed.Entries {
		if strings.Contains(e.ID, "bio") {
			detail.Bio = e.Content.Value
			detail.ImageURL = findImageLink(e.Links)
		}
		for _, l := range e.Links {
			if strings.Contains(l.Title, "по сериям") {
				detail.SeqLink = l.Href
			}
			if strings.Contains(l.Title, "вне серий") {
				detail.NoSeqLink = l.Href
			}
		}
		if strings.Contains(e.ID, "sequences") && !strings.Contains(e.ID, "sequenceless") {
			detail.SeqLink = firstCatalogLink(e.Links)
		}
		if strings.Contains(e.ID, "sequenceless") {
			detail.NoSeqLink = firstCatalogLink(e.Links)
		}
	}

	detail.BooksLink = "/opds/author/" + id

	return detail, nil
}

func (c *FlibustaClient) GetAuthorBooks(id string, page string) (*PaginatedBooks, error) {
	path := "/opds/authorsequenceless/" + id
	if page != "" {
		path += "/" + page
	}

	result := &PaginatedBooks{}

	feed, err := c.fetchFeed(path)
	if err == nil {
		for _, e := range feed.Entries {
			if book := entryToBook(e); book != nil {
				result.Books = append(result.Books, *book)
			}
		}
		for _, l := range feed.Links {
			if l.Rel == "next" {
				result.NextPage = pageTokenFromHref(l.Href)
			}
		}
	}

	if page == "" {
		seqFeed, err2 := c.fetchFeed("/opds/authorsequences/" + id)
		if err2 == nil {
			for _, seqEntry := range seqFeed.Entries {
				seqLink := firstCatalogLink(seqEntry.Links)
				if seqLink == "" {
					continue
				}
				seqBooksFeed, err3 := c.fetchFeed(seqLink)
				if err3 != nil {
					continue
				}
				for _, e := range seqBooksFeed.Entries {
					if book := entryToBook(e); book != nil {
						result.Books = append(result.Books, *book)
					}
				}
			}
		}
	}

	if len(result.Books) == 0 && err != nil {
		return nil, err
	}

	return result, nil
}

func (c *FlibustaClient) GetGenres() (*GenreList, error) {
	feed, err := c.fetchFeed("/opds/genres")
	if err != nil {
		return nil, err
	}

	c.buildGenreIndex()
	c.genreMu.RLock()
	stats := c.topGenreStats
	c.genreMu.RUnlock()

	result := &GenreList{}
	for _, e := range feed.Entries {
		link := firstCatalogLink(e.Links)
		if link != "" {
			result.Genres = append(result.Genres, GenreEntry{
				Name:  e.Title,
				Link:  link,
				Count: stats[e.Title],
			})
		}
	}
	return result, nil
}

func (c *FlibustaClient) buildGenreIndex() {
	c.genreOnce.Do(func() {
		c.refreshGenreIndex()
		go func() {
			for range time.NewTicker(24 * time.Hour).C {
				c.refreshGenreIndex()
			}
		}()
	})
}

func extractNumber(s string) int {
	for _, word := range strings.Fields(s) {
		if n, err := strconv.Atoi(word); err == nil {
			return n
		}
	}
	return 0
}

func (c *FlibustaClient) refreshGenreIndex() {
	topFeed, err := c.fetchFeed("/opds/genres")
	if err != nil {
		log.Printf("genre index: failed to fetch top-level: %v", err)
		return
	}

	idx := make(map[string]genreInfo)
	pathCount := make(map[string]string)
	topStats := make(map[string]string)
	var mu sync.Mutex
	var wg sync.WaitGroup

	for _, e := range topFeed.Entries {
		link := firstCatalogLink(e.Links)
		if link == "" {
			continue
		}
		idx[e.Title] = genreInfo{Path: link, Count: e.Content.Value}
		if e.Content.Value != "" {
			rel, _ := url.PathUnescape(strings.TrimPrefix(link, "/opds/genres/"))
			pathCount[rel] = e.Content.Value
		}

		title := e.Title
		wg.Add(1)
		go func(catLink string) {
			defer wg.Done()
			subFeed, err := c.fetchFeed(catLink)
			if err != nil {
				return
			}
			subCount := 0
			totalBooks := 0
			mu.Lock()
			defer mu.Unlock()
			for _, sub := range subFeed.Entries {
				subLink := firstCatalogLink(sub.Links)
				if subLink != "" {
					idx[sub.Title] = genreInfo{Path: subLink, Count: sub.Content.Value}
					if sub.Content.Value != "" {
						rel, _ := url.PathUnescape(strings.TrimPrefix(subLink, "/opds/genres/"))
						pathCount[rel] = sub.Content.Value
						totalBooks += extractNumber(sub.Content.Value)
					}
					subCount++
				}
			}
			if totalBooks > 0 {
				topStats[title] = fmt.Sprintf("%d подкатегорий · %d книг", subCount, totalBooks)
			} else if subCount > 0 {
				topStats[title] = fmt.Sprintf("%d подкатегорий", subCount)
			}
		}(link)
	}
	wg.Wait()

	c.genreMu.Lock()
	c.genreIndex = idx
	c.genreCountByPath = pathCount
	c.topGenreStats = topStats
	c.genreMu.Unlock()
	log.Printf("genre index: refreshed with %d entries, %d top-level stats", len(idx), len(topStats))
}

func (c *FlibustaClient) resolveGenre(name string) genreInfo {
	c.buildGenreIndex()

	c.genreMu.RLock()
	defer c.genreMu.RUnlock()
	return c.genreIndex[name]
}

func (c *FlibustaClient) BrowseGenre(genrePath string) (*GenreBrowseResult, error) {
	path := "/opds/genres/" + genrePath

	feed, err := c.fetchFeed(path)
	if err != nil {
		return nil, err
	}

	result := parseBrowseResult(feed)

	c.buildGenreIndex()
	c.genreMu.RLock()
	if cnt, ok := c.genreCountByPath[genrePath]; ok && cnt != "" {
		result.TotalCount = cnt
	}
	c.genreMu.RUnlock()

	if len(result.Books) == 0 && len(result.SubGenres) == 0 && !strings.Contains(genrePath, "/") {
		if info := c.resolveGenre(genrePath); info.Path != "" {
			resolvedPath := strings.TrimPrefix(info.Path, "/opds/genres/")
			if resolvedPath != genrePath {
				feed2, err2 := c.fetchFeed(info.Path)
				if err2 == nil {
					result = parseBrowseResult(feed2)
					result.TotalCount = info.Count
				}
			}
		}
	}

	return result, nil
}

func parseBrowseResult(feed *Feed) *GenreBrowseResult {
	result := &GenreBrowseResult{}

	for _, e := range feed.Entries {
		if book := entryToBook(e); book != nil {
			result.Books = append(result.Books, *book)
		}
	}

	if len(result.Books) == 0 {
		for _, e := range feed.Entries {
			link := firstCatalogLink(e.Links)
			if link != "" {
				result.SubGenres = append(result.SubGenres, GenreEntry{
					Name:  e.Title,
					Link:  link,
					Count: e.Content.Value,
				})
			}
		}
	}

	for _, l := range feed.Links {
		if l.Rel == "next" {
			result.NextPage = pageTokenFromHref(l.Href)
		}
	}

	return result
}

func (c *FlibustaClient) GetNew() (*NewBooksHub, error) {
	feed, err := c.fetchFeed("/opds/new")
	if err != nil {
		return nil, err
	}

	hub := &NewBooksHub{Title: feed.Title}
	for _, e := range feed.Entries {
		link := firstCatalogLink(e.Links)
		hub.Entries = append(hub.Entries, CatalogEntry{
			ID:      e.ID,
			Title:   e.Title,
			Content: e.Content.Value,
			Link:    link,
		})
	}
	return hub, nil
}

func (c *FlibustaClient) GetNewBooks(page string) (*PaginatedBooks, error) {
	path := "/opds/new/0/new"
	if page != "" {
		path += "?pageNumber=" + page
	}

	feed, err := c.fetchFeed(path)
	if err != nil {
		return nil, err
	}

	result := &PaginatedBooks{}
	for _, e := range feed.Entries {
		if book := entryToBook(e); book != nil {
			result.Books = append(result.Books, *book)
		}
	}

	for _, l := range feed.Links {
		if l.Rel == "next" {
			result.NextPage = pageTokenFromHref(l.Href)
		}
	}

	return result, nil
}

func (c *FlibustaClient) GetOPDSFeed(path string) (*PaginatedBooks, error) {
	feed, err := c.fetchFeed(path)
	if err != nil {
		return nil, err
	}

	result := &PaginatedBooks{}
	for _, e := range feed.Entries {
		if book := entryToBook(e); book != nil {
			result.Books = append(result.Books, *book)
		}
	}

	for _, l := range feed.Links {
		if l.Rel == "next" {
			result.NextPage = pageTokenFromHref(l.Href)
		}
	}

	return result, nil
}

// Helpers

func entryToBook(e Entry) *BookResult {
	if strings.Contains(e.ID, ":bio:") || strings.Contains(e.ID, ":sequences") {
		return nil
	}

	hasDownload := false
	for _, l := range e.Links {
		if strings.Contains(l.Rel, "acquisition") && !strings.Contains(l.Type, "html") {
			hasDownload = true
			break
		}
	}
	if !hasDownload && len(e.Authors) == 0 {
		return nil
	}

	bookID := ""
	for _, l := range e.Links {
		if l.Rel == "alternate" {
			m := bookIDRe.FindStringSubmatch(l.Href)
			if len(m) > 1 {
				bookID = m[1]
			}
		}
	}
	if bookID == "" {
		for _, l := range e.Links {
			if strings.Contains(l.Rel, "acquisition") {
				m := bookIDRe.FindStringSubmatch(l.Href)
				if len(m) > 1 {
					bookID = m[1]
					break
				}
			}
		}
	}

	book := &BookResult{
		ID:        bookID,
		Title:     e.Title,
		Language:  e.Language,
		Format:    e.Format,
		Year:      e.Issued,
		Authors:   []AuthorRef{},
		Genres:    []string{},
		Downloads: []DownloadLink{},
	}

	if e.Content.Value != "" {
		book.Description = e.Content.Value
	}

	for _, a := range e.Authors {
		authorID := ""
		if m := authorIDRe.FindStringSubmatch(a.URI); len(m) > 1 {
			authorID = m[1]
		}
		book.Authors = append(book.Authors, AuthorRef{ID: authorID, Name: a.Name})
	}

	for _, cat := range e.Categories {
		book.Genres = append(book.Genres, cat.Label)
	}

	book.CoverURL = findImageLink(e.Links)

	for _, l := range e.Links {
		if !strings.Contains(l.Rel, "acquisition") {
			continue
		}
		format := formatFromLink(l)
		if format != "" {
			book.Downloads = append(book.Downloads, DownloadLink{
				Format: format,
				URL:    l.Href,
			})
		}
	}

	for _, l := range e.Links {
		if l.Rel == "alternate" && strings.Contains(l.Type, "html") {
			book.WebURL = l.Href
		}
	}

	return book
}

func entryToSeries(e Entry) *SeriesResult {
	m := sequenceIDRe.FindStringSubmatch(e.ID)
	if len(m) < 2 {
		return nil
	}
	return &SeriesResult{
		ID:        m[1],
		Name:      e.Title,
		BookCount: e.Content.Value,
		BooksLink: "/opds/sequencebooks/" + m[1],
	}
}

func entryToAuthor(e Entry) *AuthorResult {
	m := regexp.MustCompile(`author:(\d+)`).FindStringSubmatch(e.ID)
	if len(m) < 2 {
		return nil
	}

	a := &AuthorResult{
		ID:        m[1],
		Name:      e.Title,
		BookCount: e.Content.Value,
		BooksLink: "/opds/author/" + m[1],
	}

	a.ImageURL = findImageLink(e.Links)

	for _, l := range e.Links {
		if strings.Contains(l.Title, "по сериям") {
			a.SeqLink = l.Href
		}
		if strings.Contains(l.Title, "вне серий") {
			a.NoSeqLink = l.Href
		}
	}

	return a
}

func firstCatalogLink(links []Link) string {
	for _, l := range links {
		if strings.Contains(l.Type, "opds-catalog") || strings.Contains(l.Type, "atom+xml") {
			return l.Href
		}
	}
	return ""
}

func findImageLink(links []Link) string {
	for _, l := range links {
		if strings.Contains(l.Rel, "image") && !strings.Contains(l.Rel, "thumbnail") {
			return l.Href
		}
	}
	for _, l := range links {
		if strings.Contains(l.Rel, "image") {
			return l.Href
		}
	}
	return ""
}

func formatFromLink(l Link) string {
	href := l.Href
	parts := strings.Split(strings.TrimRight(href, "/"), "/")
	if len(parts) > 0 {
		last := parts[len(parts)-1]
		switch last {
		case "fb2", "epub", "mobi", "txt", "rtf", "html", "pdf", "djvu":
			return last
		case "download":
			// e.g. /b/123/download for pdf/docx
			if strings.Contains(l.Type, "pdf") {
				return "pdf"
			}
			if strings.Contains(l.Type, "djvu") {
				return "djvu"
			}
			if strings.Contains(l.Type, "doc") {
				return "doc"
			}
			return "download"
		}
	}
	return ""
}

func pageTokenFromHref(href string) string {
	parsed, err := url.Parse(href)
	if err == nil {
		if page := parsed.Query().Get("pageNumber"); page != "" {
			return page
		}
		path := strings.TrimRight(parsed.Path, "/")
		if idx := strings.LastIndex(path, "/"); idx >= 0 {
			last := path[idx+1:]
			if _, err := strconv.Atoi(last); err == nil {
				return last
			}
		}
	}
	return ""
}
