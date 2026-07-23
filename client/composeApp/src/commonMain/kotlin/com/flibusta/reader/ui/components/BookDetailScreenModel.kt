package com.flibusta.reader.ui.components

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.flibusta.reader.data.model.AuthorDetail
import com.flibusta.reader.data.model.BookResult
import com.flibusta.reader.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthorScreenState(
    val author: AuthorDetail? = null,
    val books: List<BookResult> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingBooks: Boolean = false,
    val error: String? = null,
    val booksError: String? = null
)

class AuthorScreenModel(
    private val authorId: String,
    private val repository: BookRepository
) : ScreenModel {

    private val _state = MutableStateFlow(AuthorScreenState())
    val state: StateFlow<AuthorScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = AuthorScreenState(isLoading = true)

            val authorResult = repository.getAuthor(authorId)
            val booksResult = repository.getAuthorBooks(authorId)

            authorResult.onSuccess { author ->
                val books = booksResult.map { it.books }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    author = author,
                    books = books,
                    isLoading = false,
                    booksError = if (booksResult.isFailure) booksResult.exceptionOrNull()?.message else null
                )
                if (booksResult.isFailure) {
                    retryBooks()
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load author"
                )
            }
        }
    }

    fun retryBooks() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoadingBooks = true, booksError = null)
            kotlinx.coroutines.delay(1500)
            repository.getAuthorBooks(authorId)
                .onSuccess { pb ->
                    _state.value = _state.value.copy(
                        books = pb.books,
                        isLoadingBooks = false,
                        booksError = null
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingBooks = false,
                        booksError = e.message ?: "Не удалось загрузить книги"
                    )
                }
        }
    }
}

data class GenresScreenState(
    val genres: List<com.flibusta.reader.data.model.GenreEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class GenresScreenModel(private val repository: BookRepository) : ScreenModel {

    private val _state = MutableStateFlow(GenresScreenState())
    val state: StateFlow<GenresScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = GenresScreenState(isLoading = true)
            repository.getGenres()
                .onSuccess { _state.value = GenresScreenState(genres = it.genres, isLoading = false) }
                .onFailure { _state.value = GenresScreenState(isLoading = false, error = it.message) }
        }
    }
}

data class BookListState(
    val books: List<BookResult> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val title: String = ""
)

data class GenreBrowseState(
    val subGenres: List<com.flibusta.reader.data.model.GenreEntry> = emptyList(),
    val books: List<BookResult> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: String = "",
    val hasMore: Boolean = false,
    val totalCount: String = ""
)

class GenreBrowseScreenModel(
    private val genrePath: String,
    private val repository: BookRepository
) : ScreenModel {

    private val _state = MutableStateFlow(GenreBrowseState())
    val state: StateFlow<GenreBrowseState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = GenreBrowseState(isLoading = true)
            repository.browseGenre(genrePath)
                .onSuccess {
                    _state.value = GenreBrowseState(
                        subGenres = it.subGenres,
                        books = it.books,
                        isLoading = false,
                        nextPage = it.nextPage,
                        hasMore = it.nextPage.isNotEmpty(),
                        totalCount = it.totalCount
                    )
                }
                .onFailure {
                    _state.value = GenreBrowseState(isLoading = false, error = it.message)
                }
        }
    }

    fun loadMore() {
        val next = _state.value.nextPage
        if (next.isEmpty() || _state.value.isLoadingMore) return

        val path = next.removePrefix("/opds/genres/")

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            repository.browseGenre(path)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        books = _state.value.books + result.books,
                        isLoadingMore = false,
                        nextPage = result.nextPage,
                        hasMore = result.nextPage.isNotEmpty()
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }
}

class NewBooksScreenModel(private val repository: BookRepository) : ScreenModel {

    private val _state = MutableStateFlow(BookListState(title = "Новинки"))
    val state: StateFlow<BookListState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getNewBooks()
                .onSuccess { _state.value = _state.value.copy(books = it.books, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }
}

data class DownloadState(
    val format: String = "",
    val isDownloading: Boolean = false,
    val data: ByteArray? = null,
    val error: String? = null
)
