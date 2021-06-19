package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.Apis.Companion.allApi
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        val multiPreference = findPreference<MultiSelectListPreference>(getString(R.string.search_providers_list_key))!!
        val updatePrefrence = findPreference<Preference>(getString(R.string.manual_check_update_key))!!

        val apiNames = apis.map { it.name }

        multiPreference.entries = apiNames.toTypedArray()
        multiPreference.entryValues = apiNames.toTypedArray()

        multiPreference.setOnPreferenceChangeListener { _, newValue ->
            allApi.providersActive = newValue as HashSet<String>
            return@setOnPreferenceChangeListener true
        }

        updatePrefrence.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        Toast.makeText(this.context, "No Update Found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }

        findPreference<ListPreference>("color_theme")?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
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