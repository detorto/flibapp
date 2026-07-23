package com.flibusta.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.flibusta.reader.data.api.FlibustaApi
import com.flibusta.reader.data.repository.BookRepository
import com.flibusta.reader.data.repository.AppTheme
import com.flibusta.reader.data.repository.SearchHistoryRepository
import com.flibusta.reader.data.repository.SettingsRepository
import com.flibusta.reader.ui.components.LocalImageUrlResolver
import com.flibusta.reader.ui.screens.*
import com.flibusta.reader.ui.theme.FlibustaReaderTheme
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.flibusta.reader.util.FileSaver

val LocalBookRepository = compositionLocalOf<BookRepository> { error("No BookRepository provided") }
val LocalSettingsRepository = compositionLocalOf<SettingsRepository> { error("No SettingsRepository provided") }
val LocalSearchHistoryRepository = compositionLocalOf<SearchHistoryRepository> {
    error("No SearchHistoryRepository provided")
}
val LocalFileSaver = compositionLocalOf<FileSaver?> { null }

@Composable
fun App() {
    val settings = remember { SettingsRepository() }

    val httpClient = remember {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    val api = remember { FlibustaApi(httpClient) { settings.baseUrl.value } }
    val repository = remember { BookRepository(api) }
    val searchHistoryRepository = remember { SearchHistoryRepository() }

    val currentTheme by settings.theme.collectAsState()
    val isDark = when (currentTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    FlibustaReaderTheme(darkTheme = isDark) {
        CompositionLocalProvider(
            LocalImageUrlResolver provides { path -> api.imageUrl(path) },
            LocalBookRepository provides repository,
            LocalSettingsRepository provides settings,
            LocalSearchHistoryRepository provides searchHistoryRepository
        ) {
            TabNavigator(SearchTab) { tabNavigator ->
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TabItem(SearchTab)
                            TabItem(NewBooksTab)
                            TabItem(GenresTab)
                            TabItem(SettingsTab)
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        CurrentTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current

    NavigationBarItem(
        selected = tabNavigator.current.key == tab.key,
        onClick = { tabNavigator.current = tab },
        icon = {
            tab.options.icon?.let { painter ->
                Icon(painter, contentDescription = tab.options.title)
            }
        },
        label = { Text(tab.options.title) }
    )
}

object SearchTab : Tab {
    override val key = "search"
    @Composable override fun Content() { Navigator(SearchScreen()) }
    override val options: TabOptions
        @Composable get() = TabOptions(0u, "Поиск", rememberVectorPainter(Icons.Default.Search))
}

object NewBooksTab : Tab {
    override val key = "new"
    @Composable override fun Content() { Navigator(NewBooksScreen()) }
    override val options: TabOptions
        @Composable get() = TabOptions(1u, "Новинки", rememberVectorPainter(Icons.Default.Star))
}

object GenresTab : Tab {
    override val key = "genres"
    @Composable override fun Content() { Navigator(GenresScreen()) }
    override val options: TabOptions
        @Composable get() = TabOptions(2u, "Жанры", rememberVectorPainter(Icons.AutoMirrored.Filled.List))
}

object SettingsTab : Tab {
    override val key = "settings"
    @Composable override fun Content() { Navigator(SettingsScreen()) }
    override val options: TabOptions
        @Composable get() = TabOptions(3u, "Настройки", rememberVectorPainter(Icons.Default.Settings))
}

@Composable
private fun rememberVectorPainter(imageVector: ImageVector): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.graphics.vector.rememberVectorPainter(imageVector)
}
