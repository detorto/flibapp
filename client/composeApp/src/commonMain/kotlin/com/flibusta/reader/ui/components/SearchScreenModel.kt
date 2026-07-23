package com.flibusta.reader.ui.components

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.flibusta.reader.data.model.AuthorResult
import com.flibusta.reader.data.model.BookResult
import com.flibusta.reader.data.model.SeriesResult
import com.flibusta.reader.data.repository.BookRepository
import com.flibusta.reader.data.repository.SearchHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val searchedQuery: String = "",
    val authors: List<AuthorResult> = emptyList(),
    val series: List<SeriesResult> = emptyList(),
    val books: List<BookResult> = emptyList(),
    val history: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextPage: String = "",
    val error: String? = null,
    val hasSearched: Boolean = false
)

class SearchScreenModel(
    private val repository: BookRepository,
    private val historyRepository: SearchHistoryRepository
) : ScreenModel {

    private val _state = MutableStateFlow(SearchState(history = historyRepository.getHistory()))
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var paginationJob: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun clearSearch() {
        searchJob?.cancel()
        paginationJob?.cancel()
        _state.value = SearchState(history = _state.value.history)
    }

    fun clearHistory() {
        historyRepository.clear()
        _state.value = _state.value.copy(history = emptyList())
    }

    fun removeFromHistory(query: String) {
        _state.value = _state.value.copy(history = historyRepository.remove(query))
    }

    fun searchFromHistory(query: String) {
        _state.value = _state.value.copy(query = query)
        search()
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return

        searchJob?.cancel()
        paginationJob?.cancel()
        val history = historyRepository.add(q)
        _state.value = _state.value.copy(
            query = q,
            searchedQuery = q,
            authors = emptyList(),
            series = emptyList(),
            books = emptyList(),
            history = history,
            isLoading = true,
            isLoadingMore = false,
            nextPage = "",
            error = null,
            hasSearched = false
        )

        searchJob = screenModelScope.launch {

            val authorsDeferred = async { repository.searchAuthors(q) }
            val booksDeferred = async { repository.searchBooks(q) }
            val seriesDeferred = async { repository.searchSeries(q) }

            val authorsResult = authorsDeferred.await()
            val booksResult = booksDeferred.await()
            val seriesResult = seriesDeferred.await()

            val authors = authorsResult.getOrDefault(emptyList())
            val booksPage = booksResult.getOrNull()
            val series = seriesResult.getOrDefault(emptyList())
            val error = if (authorsResult.isFailure && booksResult.isFailure && seriesResult.isFailure) {
                authorsResult.exceptionOrNull()?.message ?: "Search failed"
            } else null

            _state.value = _state.value.copy(
                authors = authors,
                series = series,
                books = booksPage?.books.orEmpty(),
                isLoading = false,
                nextPage = booksPage?.nextPage.orEmpty(),
                hasSearched = true,
                error = error
            )
        }
    }

    fun loadMoreBooks() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.nextPage.isBlank()) return

        paginationJob = screenModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            repository.searchBooks(current.searchedQuery, current.nextPage)
                .onSuccess { page ->
                    val existingKeys = _state.value.books.mapTo(mutableSetOf()) { it.id to it.title }
                    _state.value = _state.value.copy(
                        books = _state.value.books + page.books.filter { (it.id to it.title) !in existingKeys },
                        nextPage = page.nextPage,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoadingMore = false
                    )
                }
        }
    }
}
