package parser

import "encoding/xml"

// Atom/OPDS XML structures

type Feed struct {
	XMLName xml.Name `xml:"feed"`
	ID      string   `xml:"id"`
	Title   string   `xml:"title"`
	Updated string   `xml:"updated"`
	Links   []Link   `xml:"link"`
	Entries []Entry  `xml:"entry"`
}

type Entry struct {
	ID         string     `xml:"id"`
	Title      string     `xml:"title"`
	Updated    string     `xml:"updated"`
	Content    Content    `xml:"content"`
	Authors    []AtomAuthor `xml:"author"`
	Links      []Link     `xml:"link"`
	Categories []Category `xml:"category"`
	Language   string     `xml:"language"`
	Format     string     `xml:"format"`
	Issued     string     `xml:"issued"`
}

type AtomAuthor struct {
	Name string `xml:"name"`
	URI  string `xml:"uri"`
}

type Content struct {
	Type  string `xml:"type,attr"`
	Value string `xml:",chardata"`
}

type Link struct {
	Href  string `xml:"href,attr"`
	Rel   string `xml:"rel,attr"`
	Type  string `xml:"type,attr"`
	Title string `xml:"title,attr"`
}

type Category struct {
	Term  string `xml:"term,attr"`
	Label string `xml:"label,attr"`
}

// JSON API response models

type CatalogEntry struct {
	ID      string `json:"id"`
	Title   string `json:"title"`
	Content string `json:"content,omitempty"`
	Link    string `json:"link"`
}

type BookResult struct {
	ID          string       `json:"id"`
	Title       string       `json:"title"`
	Authors     []AuthorRef  `json:"authors"`
	Genres      []string     `json:"genres,omitempty"`
	Language    string       `json:"language,omitempty"`
	Format      string       `json:"format,omitempty"`
	Year        string       `json:"year,omitempty"`
	Description string       `json:"description,omitempty"`
	CoverURL    string       `json:"cover_url,omitempty"`
	Downloads   []DownloadLink `json:"downloads"`
	WebURL      string       `json:"web_url,omitempty"`
}

type AuthorRef struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type DownloadLink struct {
	Format string `json:"format"`
	URL    string `json:"url"`
}

type AuthorResult struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	BookCount string `json:"book_count,omitempty"`
	ImageURL  string `json:"image_url,omitempty"`
	BooksLink string `json:"books_link"`
	SeqLink   string `json:"sequences_link,omitempty"`
	NoSeqLink string `json:"sequenceless_link,omitempty"`
}

type AuthorDetail struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Bio       string `json:"bio,omitempty"`
	ImageURL  string `json:"image_url,omitempty"`
	BooksLink string `json:"books_link"`
	SeqLink   string `json:"sequences_link,omitempty"`
	NoSeqLink string `json:"sequenceless_link,omitempty"`
}

type SearchDisambiguation struct {
	AuthorsLink string `json:"authors_link"`
	BooksLink   string `json:"books_link"`
}

type PaginatedBooks struct {
	Books    []BookResult `json:"books"`
	NextPage string       `json:"next_page,omitempty"`
}

type GenreList struct {
	Genres []GenreEntry `json:"genres"`
}

type GenreEntry struct {
	Name  string `json:"name"`
	Link  string `json:"link"`
	Count string `json:"count,omitempty"`
}

type GenreBrowseResult struct {
	SubGenres  []GenreEntry   `json:"sub_genres,omitempty"`
	Books      []BookResult   `json:"books,omitempty"`
	NextPage   string         `json:"next_page,omitempty"`
	TotalCount string         `json:"total_count,omitempty"`
}

type NewBooksHub struct {
	Title   string         `json:"title"`
	Entries []CatalogEntry `json:"entries"`
}

type SeriesResult struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	BookCount string `json:"book_count,omitempty"`
	BooksLink string `json:"books_link"`
}
