package com.flibusta.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.data.model.GenreEntry
import com.flibusta.reader.ui.components.BookCard
import com.flibusta.reader.ui.components.GenreBrowseScreenModel
import com.flibusta.reader.ui.components.GenresScreenModel

private fun extractGenrePath(opdsLink: String): String {
    val prefix = "/opds/genres/"
    return if (opdsLink.startsWith(prefix)) opdsLink.removePrefix(prefix) else opdsLink
}

class GenresScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val model = rememberScreenModel { GenresScreenModel(repository) }
        val state by model.state.collectAsState()
        var searchQuery by remember { mutableStateOf("") }

        val filteredGenres = remember(state.genres, searchQuery) {
            if (searchQuery.isBlank()) state.genres
            else state.genres.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск жанра…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

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
                    Text(
                        buildString {
                            if (searchQuery.isNotBlank()) {
                                append("Найдено ${filteredGenres.size} из ${state.genres.size}")
                            } else {
                                append("${state.genres.size} жанров")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredGenres, key = { it.name }) { genre ->
                            GenreCard(genre) {
                                navigator.push(
                                    GenreBrowseScreen(
                                        displayName = genre.name,
                                        genrePath = extractGenrePath(genre.link),
                                        totalCount = genre.count
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(genre: GenreEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = genre.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (genre.count.isNotBlank()) {
                Text(
                    text = genre.count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

class GenreBrowseScreen(
    private val displayName: String,
    private val genrePath: String,
    private val totalCount: String = ""
) : Screen {

    override val key = "genre_browse_$genrePath"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val model = rememberScreenModel { GenreBrowseScreenModel(genrePath, repository) }
        val state by model.state.collectAsState()
        var searchQuery by remember { mutableStateOf("") }

        val filteredSubGenres = remember(state.subGenres, searchQuery) {
            if (searchQuery.isBlank()) state.subGenres
            else state.subGenres.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        val filteredBooks = remember(state.books, searchQuery) {
            if (searchQuery.isBlank()) state.books
            else state.books.filter { book ->
                book.title.contains(searchQuery, ignoreCase = true) ||
                    book.authors.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
                state.subGenres.isNotEmpty() -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск в «$displayName»…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Text(
                        buildString {
                            if (searchQuery.isNotBlank()) {
                                append("Найдено ${filteredSubGenres.size} из ${state.subGenres.size}")
                            } else {
                                append("${state.subGenres.size} подкатегорий")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredSubGenres, key = { it.link }) { genre ->
                            GenreCard(genre) {
                                navigator.push(
                                    GenreBrowseScreen(
                                        displayName = genre.name,
                                        genrePath = extractGenrePath(genre.link),
                                        totalCount = genre.count
                                    )
                                )
                            }
                        }
                    }
                }
                state.books.isEmpty() && !state.hasMore -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет книг", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по названию или автору…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    val listState = rememberLazyListState()

                    LaunchedEffect(listState, state.hasMore, state.isLoadingMore, searchQuery) {
                        snapshotFlow {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 3
                        }.collect { nearEnd ->
                            if (nearEnd && state.hasMore && !state.isLoadingMore && searchQuery.isBlank()) {
                                model.loadMore()
                            }
                        }
                    }

                    val tc = state.totalCount.ifEmpty { totalCount }
                    Text(
                        buildString {
                            if (searchQuery.isNotBlank()) {
                                append("Найдено ${filteredBooks.size}")
                                if (tc.isNotEmpty() && state.hasMore) {
                                    append(" / $tc")
                                } else if (!state.hasMore) {
                                    append(" из ${state.books.size}")
                                }
                            } else if (!state.hasMore) {
                                append("${state.books.size} книг")
                            } else if (tc.isNotEmpty()) {
                                append(tc)
                            } else {
                                append("${state.books.size} книг…")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    if (searchQuery.isNotBlank() && filteredBooks.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Ничего не найдено",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredBooks.size, key = { idx -> "${filteredBooks[idx].id}_$idx" }) { idx ->
                            val book = filteredBooks[idx]
                                BookCard(
                                    book = book,
                                    onCardClick = {
                                        navigator.push(BookDetailScreen(book))
                                    },
                                    onAuthorClick = { id, name ->
                                        navigator.push(AuthorScreen(id, name))
                                    },
                                    onGenreClick = { genre ->
                                        navigator.push(GenreBrowseScreen(genre, genre))
                                    },
                                    onDownloadClick = { _, _ -> }
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
}
