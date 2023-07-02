package com.lagradost.quicknovel.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadFormat

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

    private fun Context.getDownloadFormat(): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getString(getString(R.string.download_format_key), "list")!!
    }

    /*
    fun Context.setDownloadGridIsCompact( item : Int) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        when(item) {
            0 ->"normal"
            1 -> "grid"
            else -> "normal"
        }
    }*/

    fun Context.getDownloadIsCompact(): Boolean {
        return getDownloadFormat() != "grid"
    }

}