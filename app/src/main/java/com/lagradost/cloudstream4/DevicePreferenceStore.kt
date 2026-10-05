package com.lagradost.cloudstream4

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.PreferenceStore
import kotlinx.collections.immutable.toPersistentHashSet

@Composable
fun rememberAppSettings() : AppSettings {
    val context = LocalContext.current
    val value = remember(context) { AppSettings(context) }
    return value
}

fun AppSettings(context: Context) : AppSettings {
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
}

class ProviderPreferences(preferences: PreferenceStore) {
    val searchProvidersList = preferences.getStringSet("search_providers_list",apis.map { it.name }.toSet())
    val searchLangList = preferences.getStringSet("provider_lang_key", apis.map { it.lang }.toPersistentHashSet())
}

class UIPreferences(preferences: PreferenceStore) {
    val primaryColor = preferences.getString(
        "primary_color_key", "Normal"
    )
    val theme = preferences.getString(
        "theme_key", "AmoledLight"
    )
}