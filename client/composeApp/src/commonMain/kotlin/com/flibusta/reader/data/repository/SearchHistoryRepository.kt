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
        val normalizedQuery = query
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalizedQuery.isEmpty()) return getHistory()

        val updated = (
            listOf(normalizedQuery) +
                getHistory().filterNot { it.equals(normalizedQuery, ignoreCase = true) }
            )
            .take(MAX_SEARCH_HISTORY_SIZE)
        settings.putString(SEARCH_HISTORY_KEY, updated.joinToString("\n"))
        return updated
    }

    fun remove(query: String): List<String> {
        val updated = getHistory().filterNot { it.equals(query, ignoreCase = true) }
        if (updated.isEmpty()) {
            clear()
        } else {
            settings.putString(SEARCH_HISTORY_KEY, updated.joinToString("\n"))
        }
        return updated
    }

    fun clear() {
        settings.remove(SEARCH_HISTORY_KEY)
    }
}
