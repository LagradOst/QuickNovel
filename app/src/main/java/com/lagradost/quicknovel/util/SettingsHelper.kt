package com.lagradost.quicknovel.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R

object SettingsHelper {
    fun Context.getRatingReview(score: Int): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return when (settingsManager.getString(getString(R.string.rating_format_key), "star")) {
            "star" -> "${score / 200}★"
            else -> "${(score / 100)}/10"
        }
    }

    fun Context.getRating(score: Int): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        return when (settingsManager.getString(getString(R.string.rating_format_key), "star")) {
            "point10" -> "${(score / 100)}/10"
            "point10d" -> "${"%.1f".format(score / 100f).replace(',', '.')}/10.0"
            "point100" -> "${score / 10}/100"
            else -> "%.2f".format(score.toFloat() / 200f).replace(',', '.') + "★" // star
        }
    }

    fun Context.getGridIsCompact(): Boolean {
        return getGridFormat() != "grid"
    }

    private fun Context.getGridFormat(): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getString(getString(R.string.grid_format_key), "grid")!!
    }

    fun Context.getGridFormatId(): Int {
        return when (getGridFormat()) {
            "list" -> R.layout.search_result_compact
            "compact_list" -> R.layout.search_result_super_compact
            else -> R.layout.search_result_grid
        }
    }

}