package com.lagradost.quicknovel.ui.settings

import android.content.*
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val listPreference = findPreference<ListPreference>("provider_list")!!
        listPreference.entries = MainActivity.apis.map { it.name }.toTypedArray()
        listPreference.entryValues = MainActivity.apis.map { it.name }.toTypedArray()
        listPreference.setOnPreferenceChangeListener { preference, newValue ->
            MainActivity.activeAPI = MainActivity.getApiFromName(newValue.toString())
            return@setOnPreferenceChangeListener true
        }
    }
}