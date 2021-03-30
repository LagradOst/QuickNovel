package com.example.epubdownloader.ui.settings

import android.content.*
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.epubdownloader.MainActivity
import com.example.epubdownloader.R

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