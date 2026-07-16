package com.lagradost.quicknovel.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.util.Apis.Companion.apis
import kotlinx.collections.immutable.toPersistentHashSet

@Composable
fun AndroidPreferenceStore.searchProvidersList() = getStringSet(
    stringResource(R.string.search_providers_list_key),
    remember { apis.map { it.name }.toSet() }
)

@Composable
fun AndroidPreferenceStore.searchLangList() = getStringSet(
    stringResource(R.string.provider_lang_key),
    remember { apis.map { it.lang }.toPersistentHashSet() }
)