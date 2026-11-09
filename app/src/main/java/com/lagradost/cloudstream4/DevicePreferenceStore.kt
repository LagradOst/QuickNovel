package com.lagradost.cloudstream4

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lagradost.quicknovel.ui.common.ChapterSortingMethodType
import com.lagradost.quicknovel.ui.common.SortingMethodType
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.PreferenceStore
import com.mihon.common.preference.getEnum
import kotlinx.collections.immutable.toPersistentHashSet

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    val value = remember(context) { AppSettings(context) }
    return value
}

fun AppSettings(context: Context): AppSettings {
    return AppSettings(preferences = AndroidPreferenceStore(context))
}

/**
 * App settings for every setting we have, that way we can just inject AppSettings into a viewmodel,
 * or similar.
 *
 * We use an internal constructor to force the user to construct it using the plantform specific
 * initalizer
 * */
class AppSettings internal constructor(
    preferences: PreferenceStore,
) {
    val ui = UIPreferences(preferences)
    val provider = ProviderPreferences(preferences)
    val updates = UpdatePreferences(preferences)
    val reader = ReaderPreferences(preferences)
    val results = ResultsPagePreferences(preferences)
    val history = HistoryPagePreferences(preferences)
    val downloads = DownloadPagePreferences(preferences)
    val main = MainPagePreferences(preferences)
}

class MainPagePreferences(preferences: PreferenceStore) {
    val isRow = preferences.getBoolean("search_list_view_key", false)
}

class DownloadPagePreferences(preferences: PreferenceStore) {
    val currentPage = preferences.getInt(
        "download_current_tab",
        0,
    )
    val downloadSortingMethod = preferences.getEnum(
        "download_sorting_method",
        SortingMethodType.Default,
    )
    val bookmarkSortingMethod = preferences.getEnum(
        "bookmark_sorting_method",
        SortingMethodType.Default,
    )
    val isRow = preferences.getBoolean("download_list_view_key", true)
}

class HistoryPagePreferences(preferences: PreferenceStore) {
    val sortingMethod = preferences.getEnum(
        "history_sort_method",
        SortingMethodType.Default,
    )
    val isRow = preferences.getBoolean("history_list_view_key", true)
}
class ResultsPagePreferences(preferences: PreferenceStore) {
    val sortingMethod = preferences.getEnum(
        "results_sort_method",
        ChapterSortingMethodType.Default,
    )
}

class ReaderPreferences(preferences: PreferenceStore) {
    val externalReader = preferences.getBoolean(
        "external_reader",
        true,
    )
    val authorNotes =  preferences.getBoolean(
        "author_notes",
        false,
    )
}


class ProviderPreferences(preferences: PreferenceStore) {
    val searchProvidersList =
        preferences.getStringSet("search_providers_list", apis.map { it.name }.toSet())
    val searchLangList =
        preferences.getStringSet("provider_lang_key", apis.map { it.lang }.toPersistentHashSet())
}

class UpdatePreferences(preferences: PreferenceStore) {
    val showAppUpdates = preferences.getBoolean(
        "auto_update",
        true,
    )
    val skipUpdate = preferences.getString("skip_update_key", "")
    val apkInstaller = preferences.getInt("apk_installer_key",1)
}

class UIPreferences(preferences: PreferenceStore) {
    val primaryColor = preferences.getString(
        "primary_color_key", "Normal"
    )
    val theme = preferences.getString(
        "theme_key", "AmoledLight"
    )
    val ratingFormat = preferences.getString(
        "rating_format",
        "star",
    )
    val locale = preferences.getString(
        "locale_key",
        "en",
    )
}