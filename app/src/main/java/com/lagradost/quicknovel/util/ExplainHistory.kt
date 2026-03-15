package com.lagradost.quicknovel.util

import android.content.Context
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.quicknovel.EXPLAIN_HISTORY_KEY
import com.lagradost.quicknovel.DataStore
import com.lagradost.quicknovel.DataStore.getSharedPrefs
import com.lagradost.quicknovel.DataStore.mapper

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExplainEntry(
    val selected: String = "",
    val paragraph: String = "",
    val result: String = "",
    val model: String = "",
    val timestamp: Long = 0L
)

object ExplainHistory {
    private val listType = object : TypeReference<List<ExplainEntry>>() {}

    fun save(context: Context, entry: ExplainEntry) {
        val existing = getAll(context).toMutableList()
        existing.add(0, entry) // newest first
        try {
            context.getSharedPrefs().edit()
                .putString(EXPLAIN_HISTORY_KEY, mapper.writeValueAsString(existing))
                .apply()
        } catch (e: Exception) {
            // ignore storage failures silently
        }
    }

    fun getAll(context: Context): List<ExplainEntry> {
        return try {
            val json = context.getSharedPrefs().getString(EXPLAIN_HISTORY_KEY, null) ?: return emptyList()
            mapper.readValue(json, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPrefs().edit().remove(EXPLAIN_HISTORY_KEY).apply()
    }
}
