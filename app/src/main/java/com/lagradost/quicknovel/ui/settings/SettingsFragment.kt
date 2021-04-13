package com.lagradost.quicknovel.ui.settings

import android.content.*
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        val multiPreference = findPreference<MultiSelectListPreference>(getString(R.string.search_providers_list_key))!!

        val apiNames = MainActivity.apis.map { it.name }

        multiPreference.entries = apiNames.toTypedArray()
        multiPreference.entryValues = apiNames.toTypedArray()

        multiPreference.setOnPreferenceChangeListener { preference, newValue ->
            MainActivity.allApi.providersActive = newValue as HashSet<String>
            return@setOnPreferenceChangeListener true
        }
        /*
        val listPreference = findPreference<ListPreference>("provider_list")!!

        val apiNames = MainActivity.apis.map { it.name }

        listPreference.entries = apiNames.toTypedArray()
        listPreference.entryValues = apiNames.toTypedArray()
        listPreference.setOnPreferenceChangeListener { preference, newValue ->
            MainActivity.activeAPI = MainActivity.getApiFromName(newValue.toString())
            return@setOnPreferenceChangeListener true
        }*/
    }
}