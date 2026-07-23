package com.flibusta.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.LocalSearchHistoryRepository
import com.flibusta.reader.data.model.AuthorResult
import com.flibusta.reader.data.model.SeriesResult
import com.flibusta.reader.ui.components.BookCard
import com.flibusta.reader.ui.components.SearchScreenModel

class SearchScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val historyRepository = LocalSearchHistoryRepository.current
        val model = rememberScreenModel { SearchScreenModel(repository, historyRepository) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = model::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Книги, авторы, серии...") },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = model::clearSearch) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    } else {
                        IconButton(onClick = model::search) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.search() })
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
                            OutlinedButton(onClick = model::search) { Text("Повторить") }
                        }
                    }
                }
                !state.hasSearched -> {
                    SearchHistory(
                        history = state.history,
                        onSearch = model::searchFromHistory,
                        onClear = model::clearHistory
                    )
                }
                state.authors.isEmpty() && state.series.isEmpty() && state.books.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    var selectedLangs by remember(state.searchedQuery) {
                        mutableStateOf(emptySet<String>())
                    }

                    val availableLangs = remember(state.books) {
                        state.books.mapNotNull { it.language.takeIf { l -> l.isNotBlank() } }
                            .groupingBy { it.uppercase() }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .map { it.key to it.value }
                    }

                    val filteredBooks = remember(state.books, selectedLangs) {
                        if (selectedLangs.isEmpty()) state.books
                        else state.books.filter { it.language.uppercase() in selectedLangs }
                    }

                    if (availableLangs.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableLangs.forEach { (lang, count) ->
                                FilterChip(
                                    selected = lang in selectedLangs,
                                    onClick = {
                                        selectedLangs = if (lang in selectedLangs)
                                            selectedLangs - lang else selectedLangs + lang
                                    },
                                    label = { Text("$lang ($count)", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (state.authors.isNotEmpty()) {
                            item {
                                Text(
                                    "Авторы (${state.authors.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.authors.size, key = { idx -> "a_${state.authors[idx].id}_$idx" }) { idx ->
                                val author = state.authors[idx]
                                AuthorCard(author) {
                                    navigator.push(AuthorScreen(author.id, author.name))
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (state.series.isNotEmpty()) {
                            item {
                                Text(
                                    "Серии (${state.series.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.series.size, key = { idx -> "s_${state.series[idx].id}_$idx" }) { idx ->
                                val s = state.series[idx]
                                SeriesCard(s) {
                                    navigator.push(SeriesScreen(s.id, s.name))
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        if (filteredBooks.isNotEmpty()) {
                            item {
                                Text(
                                    buildString {
                                        append("Книги")
                                        if (selectedLangs.isNotEmpty()) {
                                            append(" (${filteredBooks.size} из ${state.books.size})")
                                        } else {
                                            append(" (${state.books.size})")
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredBooks.size, key = { idx -> "b_${filteredBooks[idx].id}_$idx" }) { idx ->
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
                        } else if (selectedLangs.isNotEmpty() && state.books.isNotEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Нет книг на выбранных языках",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (state.nextPage.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    } else {
                                        OutlinedButton(onClick = model::loadMoreBooks) {
                                            Text("Показать ещё книги")
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
}

@Composable
private fun SearchHistory(
    history: List<String>,
    onSearch: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Введите название книги, автора или серии",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Недавние запросы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClear) {
                    Text("Очистить")
                }
            }
        }
        items(history, key = { it.lowercase() }) { query ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSearch(query) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Text(
                    text = query,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeriesCard(series: SeriesResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.bookCount.isNotEmpty()) {
                    Text(
                        text = series.bookCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "📚",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun AuthorCard(author: AuthorResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (author.bookCount.isNotEmpty()) {
                    Text(
                        text = author.bookCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
