package com.flibusta.reader.data.repository

import com.russhwolf.settings.Settings

private const val SEARCH_HISTORY_KEY = "search_history"
private const val MAX_SEARCH_HISTORY_SIZE = 10

class SearchHistoryRepository(private val settings: Settings = Settings()) {

    fun getHistory(): List<String> =
        settings.getString(SEARCH_HISTORY_KEY, "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(MAX_SEARCH_HISTORY_SIZE)
            .toList()

    fun add(query: String): List<String> {
        val updated = (listOf(query) + getHistory().filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY_SIZE)
        settings.putString(SEARCH_HISTORY_KEY, updated.joinToString("\n"))
        return updated
    }

    fun clear() {
        settings.remove(SEARCH_HISTORY_KEY)
    }
}
