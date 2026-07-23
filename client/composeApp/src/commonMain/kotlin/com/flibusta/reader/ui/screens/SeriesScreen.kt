package com.flibusta.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.data.model.BookResult
import com.flibusta.reader.data.repository.BookRepository
import com.flibusta.reader.ui.components.BookCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesScreenState(
    val books: List<BookResult> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val nextPage: String = ""
)

class SeriesScreenModel(
    private val seriesId: String,
    private val repository: BookRepository
) : ScreenModel {
    private val _state = MutableStateFlow(SeriesScreenState())
    val state: StateFlow<SeriesScreenState> = _state.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _state.value = SeriesScreenState(isLoading = true)
            repository.getSeriesBooks(seriesId)
                .onSuccess {
                    _state.value = SeriesScreenState(
                        books = it.books,
                        isLoading = false,
                        nextPage = it.nextPage,
                        hasMore = it.nextPage.isNotEmpty()
                    )
                }
                .onFailure {
                    _state.value = SeriesScreenState(isLoading = false, error = it.message)
                }
        }
    }

    fun loadMore() {
        val next = _state.value.nextPage
        if (next.isEmpty() || _state.value.isLoadingMore) return
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            repository.getSeriesBooks(seriesId, next)
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

class SeriesScreen(
    private val seriesId: String,
    private val seriesName: String
) : Screen {

    override val key: ScreenKey = "SeriesScreen_$seriesId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val model = rememberScreenModel { SeriesScreenModel(seriesId, repository) }
        val state by model.state.collectAsState()
        val listState = rememberLazyListState()

        val nearEnd by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 5
            }
        }
        LaunchedEffect(nearEnd, state.hasMore, state.isLoadingMore) {
            if (nearEnd && state.hasMore && !state.isLoadingMore) model.loadMore()
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        seriesName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.books.isNotEmpty()) {
                        Text(
                            "${state.books.size} книг",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = model::load) { Text("Повторить") }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.books.size, key = { idx -> "${state.books[idx].id}_$idx" }) { idx ->
                            val book = state.books[idx]
                            BookCard(
                                book = book,
                                onCardClick = { navigator.push(BookDetailScreen(book)) },
                                onAuthorClick = { id, name -> navigator.push(AuthorScreen(id, name)) },
                                onGenreClick = { genre -> navigator.push(GenreBrowseScreen(genre, genre)) },
                                onDownloadClick = { _, _ -> }
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
