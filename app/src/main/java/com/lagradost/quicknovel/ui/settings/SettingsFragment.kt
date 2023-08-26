package com.lagradost.quicknovel.ui.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.BackupUtils.backup
import com.lagradost.quicknovel.util.BackupUtils.restorePrompt
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.SingleSelectionHelper
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showMultiDialog
import com.lagradost.quicknovel.util.SubtitleHelper

class SettingsFragment : PreferenceFragmentCompat() {
    private fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
        if (this == null) return null

        return try {
            findPreference(getString(id))
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    companion object {
        fun showSearchProviders(context: Context?) {
            if (context == null) return
            val apiNames = apis.map { it.name }

            context.apply {
                val active = getApiSettings()
                showMultiDialog(
                    apiNames,
                    apiNames.mapIndexed { index, s -> index to active.contains(s) }
                        .filter { it.second }
                        .map { it.first }.toList(),
                    getString(R.string.search_providers),
                    {}) { list ->
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
                    val edit = settingsManager.edit()
                    edit.putStringSet(
                        getString(R.string.search_providers_list_key),
                        list.map { apiNames[it] }.toSet()
                    )
                    edit.apply()
                    providersActive = getApiSettings()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val multiPreference = getPref(R.string.search_providers_list_key)

        val updatePrefrence =
            findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val providerLangPreference =
            findPreference<Preference>(getString(R.string.provider_lang_key))!!

        multiPreference?.setOnPreferenceClickListener {
            showSearchProviders(activity)
            return@setOnPreferenceClickListener true
        }

        /*multiPreference.entries = apiNames.toTypedArray()
        multiPreference.entryValues = apiNames.toTypedArray()

        multiPreference.setOnPreferenceChangeListener { _, newValue ->
            (newValue as HashSet<String>?)?.let {
                providersActive = it
            }
            return@setOnPreferenceChangeListener true
        }*/

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
                if (true != activity?.runAutoUpdate(false)) {
                    showToast("No Update Found", Toast.LENGTH_SHORT)
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

        getPref(R.string.theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.theme),
                false,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.theme_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues =
                resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.primary_color_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.rating_format_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.RatingFormat)
            val prefValues = resources.getStringArray(R.array.RatingFormatData)

            val current =
                settingsManager.getString(getString(R.string.rating_format_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.rating_format),
                false,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.rating_format_key), prefValues[it])
                        .apply()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.download_format_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.DownloadGridFormat)
            val prefValues = resources.getStringArray(R.array.DownloadGridFormatData)

            val current =
                settingsManager.getString(
                    getString(R.string.download_format_key),
                    prefValues.first()
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.rating_format),
                false,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.download_format_key), prefValues[it])
                        .apply()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        /*getPref(R.string.theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names)
            val prefValues = resources.getStringArray(R.array.themes_names_values)

            val currentPref =
                settingsManager.getString(getString(R.string.theme_key), "Blue")

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPref),
                getString(R.string.theme),
                true,
                {}) { index ->
                settingsManager.edit()
                    .putString(getString(R.string.theme_key), prefValues[index])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }*/


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