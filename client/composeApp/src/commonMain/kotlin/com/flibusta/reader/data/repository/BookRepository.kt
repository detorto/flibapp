package com.flibusta.reader.data.repository

import com.flibusta.reader.data.api.FlibustaApi
import com.flibusta.reader.data.model.*

class BookRepository(val api: FlibustaApi) {

    suspend fun searchBooks(query: String, page: String = ""): Result<PaginatedBooks> =
        runCatching { api.searchBooks(query, page) }

    suspend fun searchAuthors(query: String): Result<List<AuthorResult>> =
        runCatching { api.searchAuthors(query) }

    suspend fun searchSeries(query: String): Result<List<SeriesResult>> =
        runCatching { api.searchSeries(query) }

    suspend fun getSeriesBooks(id: String, page: String = ""): Result<PaginatedBooks> =
        runCatching { api.getSeriesBooks(id, page) }

    suspend fun getAuthor(id: String): Result<AuthorDetail> =
        runCatching { api.getAuthor(id) }

    suspend fun getAuthorBooks(id: String, page: String = ""): Result<PaginatedBooks> =
        runCatching { api.getAuthorBooks(id, page) }

    suspend fun getGenres(): Result<GenreList> =
        runCatching { api.getGenres() }

    suspend fun browseGenre(path: String): Result<GenreBrowseResult> =
        runCatching { api.browseGenre(path) }

    suspend fun getNew(): Result<NewBooksHub> =
        runCatching { api.getNew() }

    suspend fun getNewBooks(page: String = ""): Result<PaginatedBooks> =
        runCatching { api.getNewBooks(page) }

    suspend fun downloadBook(bookId: String, format: String): Result<ByteArray> =
        runCatching { api.downloadBook(bookId, format) }

    fun imageUrl(path: String): String = api.imageUrl(path)

    fun downloadUrl(bookId: String, format: String): String = api.downloadUrl(bookId, format)
}
