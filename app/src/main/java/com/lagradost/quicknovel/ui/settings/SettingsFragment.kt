package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.*
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.BackupUtils.backup
import com.lagradost.quicknovel.util.BackupUtils.restorePrompt
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.SingleSelectionHelper.showMultiDialog
import com.lagradost.quicknovel.util.SubtitleHelper

class SettingsFragment : PreferenceFragmentCompat() {
    fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
        if (this == null) return null

        return try {
            findPreference(getString(id))
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        val multiPreference =
            findPreference<MultiSelectListPreference>(getString(R.string.search_providers_list_key))!!
        val updatePrefrence =
            findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val providerLangPreference =
            findPreference<Preference>(getString(R.string.provider_lang_key))!!

        val apiNames = apis.map { it.name }

        multiPreference.entries = apiNames.toTypedArray()
        multiPreference.entryValues = apiNames.toTypedArray()

        multiPreference.setOnPreferenceChangeListener { _, newValue ->
            (newValue as HashSet<String>?)?.let {
                providersActive = it
            }
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        updatePrefrence.setOnPreferenceClickListener {
            ioSafe {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        Toast.makeText(this@SettingsFragment.context, "No Update Found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        providerLangPreference.setOnPreferenceClickListener {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it.context)

            activity?.getApiProviderLangSettings()?.let { current ->
                val allLangs = HashSet<String>()
                for (api in apis) {
                    allLangs.add(api.lang)
                }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(allLangs.indexOf(i))
                }

                val names = allLangs.mapNotNull {
                    val fullName = SubtitleHelper.fromTwoLettersToLanguage(it)
                    if (fullName.isNullOrEmpty()) {
                        return@mapNotNull null
                    }

                    Pair(it, fullName)
                }

                context?.showMultiDialog(
                    names.map { it.second },
                    currentList,
                    getString(R.string.provider_lang_settings),
                    {}) { selectedList ->
                    settingsManager.edit().putStringSet(
                        this.getString(R.string.provider_lang_key),
                        selectedList.map { names[it].first }.toMutableSet()
                    ).apply()

                    providersActive = it.context.getApiSettings()
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