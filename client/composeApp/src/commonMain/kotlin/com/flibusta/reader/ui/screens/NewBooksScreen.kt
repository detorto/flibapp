package com.flibusta.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.ui.components.BookCard
import com.flibusta.reader.ui.components.NewBooksScreenModel

class NewBooksScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val model = rememberScreenModel { NewBooksScreenModel(repository) }
        val state by model.state.collectAsState()

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
            state.books.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет новинок", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            "Новинки за неделю",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(state.books.size, key = { idx -> "${state.books[idx].id}_$idx" }) { idx ->
                        val book = state.books[idx]
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
                }
            }
        }
    }
}
