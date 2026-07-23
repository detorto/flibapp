package com.flibusta.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthorRef(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class DownloadLink(
    val format: String = "",
    val url: String = ""
)

@Serializable
data class BookResult(
    val id: String = "",
    val title: String = "",
    val authors: List<AuthorRef> = emptyList(),
    val genres: List<String> = emptyList(),
    val language: String = "",
    val format: String = "",
    val year: String = "",
    val description: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val downloads: List<DownloadLink> = emptyList(),
    @SerialName("web_url") val webUrl: String = ""
)

@Serializable
data class AuthorResult(
    val id: String = "",
    val name: String = "",
    @SerialName("book_count") val bookCount: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("books_link") val booksLink: String = "",
    @SerialName("sequences_link") val sequencesLink: String = "",
    @SerialName("sequenceless_link") val sequencelessLink: String = ""
)

@Serializable
data class AuthorDetail(
    val id: String = "",
    val name: String = "",
    val bio: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("books_link") val booksLink: String = "",
    @SerialName("sequences_link") val sequencesLink: String = "",
    @SerialName("sequenceless_link") val sequencelessLink: String = ""
)

@Serializable
data class PaginatedBooks(
    val books: List<BookResult> = emptyList(),
    @SerialName("next_page") val nextPage: String = ""
)

@Serializable
data class GenreEntry(
    val name: String = "",
    val link: String = "",
    val count: String = ""
)

@Serializable
data class GenreList(
    val genres: List<GenreEntry> = emptyList()
)

@Serializable
data class GenreBrowseResult(
    @SerialName("sub_genres") val subGenres: List<GenreEntry> = emptyList(),
    val books: List<BookResult> = emptyList(),
    @SerialName("next_page") val nextPage: String = "",
    @SerialName("total_count") val totalCount: String = ""
)

@Serializable
data class CatalogEntry(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val link: String = ""
)

@Serializable
data class NewBooksHub(
    val title: String = "",
    val entries: List<CatalogEntry> = emptyList()
)

@Serializable
data class SearchDisambiguation(
    @SerialName("authors_link") val authorsLink: String = "",
    @SerialName("books_link") val booksLink: String = ""
)

@Serializable
data class SeriesResult(
    val id: String = "",
    val name: String = "",
    @SerialName("book_count") val bookCount: String = "",
    @SerialName("books_link") val booksLink: String = ""
)

@Serializable
data class ErrorResponse(
    val error: String = ""
)
