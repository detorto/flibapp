package com.flibusta.reader.data.api

import com.flibusta.reader.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class FlibustaApi(private val client: HttpClient, private val baseUrlProvider: () -> String) {

    private val baseUrl get() = baseUrlProvider().trimEnd('/')

    suspend fun searchBooks(query: String, page: String = ""): PaginatedBooks {
        return client.get("$baseUrl/api/v1/search/books") {
            parameter("q", query)
            if (page.isNotEmpty()) parameter("page", page)
        }.body()
    }

    suspend fun searchAuthors(query: String): List<AuthorResult> {
        return client.get("$baseUrl/api/v1/search/authors") {
            parameter("q", query)
        }.body()
    }

    suspend fun searchSeries(query: String): List<SeriesResult> {
        return client.get("$baseUrl/api/v1/search/series") {
            parameter("q", query)
        }.body()
    }

    suspend fun getSeriesBooks(id: String, page: String = ""): PaginatedBooks {
        return client.get("$baseUrl/api/v1/series/$id/books") {
            if (page.isNotEmpty()) parameter("page", page)
        }.body()
    }

    suspend fun getAuthor(id: String): AuthorDetail {
        return client.get("$baseUrl/api/v1/author/$id").body()
    }

    suspend fun getAuthorBooks(id: String, page: String = ""): PaginatedBooks {
        return client.get("$baseUrl/api/v1/author/$id/books") {
            if (page.isNotEmpty()) parameter("page", page)
        }.body()
    }

    suspend fun getGenres(): GenreList {
        return client.get("$baseUrl/api/v1/genres").body()
    }

    suspend fun browseGenre(path: String): GenreBrowseResult {
        return client.get("$baseUrl/api/v1/genres/$path").body()
    }

    suspend fun getNew(): NewBooksHub {
        return client.get("$baseUrl/api/v1/new").body()
    }

    suspend fun getNewBooks(page: String = ""): PaginatedBooks {
        return client.get("$baseUrl/api/v1/new/books") {
            if (page.isNotEmpty()) parameter("page", page)
        }.body()
    }

    suspend fun downloadBook(bookId: String, format: String): ByteArray {
        return client.get("$baseUrl/api/v1/download/$bookId/$format").readBytes()
    }

    fun imageUrl(path: String): String {
        if (path.isEmpty()) return ""
        return "$baseUrl/api/v1/image${path}"
    }

    fun downloadUrl(bookId: String, format: String): String {
        return "$baseUrl/api/v1/download/$bookId/$format"
    }

    suspend fun healthCheck(): Boolean {
        return try {
            client.get("$baseUrl/health").status.value == 200
        } catch (_: Exception) {
            false
        }
    }
}
