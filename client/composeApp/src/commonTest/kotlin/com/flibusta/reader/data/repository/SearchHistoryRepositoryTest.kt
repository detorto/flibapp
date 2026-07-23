package com.flibusta.reader.data.repository

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHistoryRepositoryTest {

    @Test
    fun addsNewestFirstAndPersistsHistory() {
        val settings = MapSettings()
        val repository = SearchHistoryRepository(settings)

        repository.add("Булгаков")
        repository.add("Мастер и Маргарита")

        assertEquals(
            listOf("Мастер и Маргарита", "Булгаков"),
            SearchHistoryRepository(settings).getHistory()
        )
    }

    @Test
    fun normalizesAndDeduplicatesQueriesIgnoringCase() {
        val repository = SearchHistoryRepository(MapSettings())

        repository.add("  Мастер\n  и Маргарита  ")
        repository.add("мастер и маргарита")

        assertEquals(listOf("мастер и маргарита"), repository.getHistory())
    }

    @Test
    fun keepsOnlyTenRecentQueries() {
        val repository = SearchHistoryRepository(MapSettings())

        (1..12).forEach { repository.add("Запрос $it") }

        assertEquals(10, repository.getHistory().size)
        assertEquals("Запрос 12", repository.getHistory().first())
        assertEquals("Запрос 3", repository.getHistory().last())
    }

    @Test
    fun removesOneEntryOrClearsEverything() {
        val repository = SearchHistoryRepository(MapSettings())
        repository.add("Один")
        repository.add("Два")

        assertEquals(listOf("Два"), repository.remove("Один"))
        repository.clear()

        assertTrue(repository.getHistory().isEmpty())
    }
}
