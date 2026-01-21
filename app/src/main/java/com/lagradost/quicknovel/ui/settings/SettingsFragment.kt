package com.lagradost.quicknovel.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.BackupUtils.backup
import com.lagradost.quicknovel.util.BackupUtils.restorePrompt
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.util.SingleSelectionHelper.showMultiDialog
import com.lagradost.quicknovel.util.SubtitleHelper
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
import java.io.File
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.databinding.LogcatBinding
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.BackupUtils.setupStream
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.sequences.forEach

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
        fun getCurrentLocale(context: Context): String {
            val res = context.resources
            val conf = res.configuration

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conf?.locales?.get(0)?.toString() ?: "en"
            } else {
                @Suppress("DEPRECATION")
                conf?.locale?.toString() ?: "en"
            }
        }

        // idk, if you find a way of automating this it would be great
        // https://www.iemoji.com/view/emoji/1794/flags/antarctica
        // Emoji Character Encoding Data --> C/C++/Java Src
        // https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
        val appLanguages = arrayListOf(
            /* begin language list */
            Triple("", "English", "en"),
            Triple("", "Türkçe", "tr"),
            /* end language list */
        ).sortedBy { it.second.lowercase() } //ye, we go alphabetical, so ppl don't put their lang on top

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
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.search_providers_list_key),
                            list.map { apiNames[it] }.toSet()
                        )
                    }
                    providersActive = getApiSettings()
                }
            }
        }

        fun getDefaultDir(context: Context): SafeFile? {
            // See https://www.py4u.net/discuss/614761
            return SafeFile.fromMedia(
                context, MediaFileContentType.Downloads
            )?.gotoDirectory("Epub")
        }

        /**
         * Turns a string to an UniFile. Used for stored string paths such as settings.
         * Should only be used to get a download path.
         * */
        private fun basePathToFile(context: Context, path: String?): SafeFile? {
            return when {
                path.isNullOrBlank() -> getDefaultDir(context)
                path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
                else -> SafeFile.fromFilePath(
                    context,
                    path.removePrefix(Environment.getExternalStorageDirectory().path).removePrefix(
                        File.separator
                    ).removeSuffix(File.separator) + File.separator
                )
            }
        }


        /**
         * Base path where downloaded things should be stored, changes depending on settings.
         * Returns the file and a string to be stored for future file retrieval.
         * UniFile.filePath is not sufficient for storage.
         * */
        fun Context.getBasePath(): Pair<SafeFile?, String?> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val basePathSetting =
                settingsManager.getString(getString(R.string.download_path_key), null)
            return basePathToFile(this, basePathSetting) to basePathSetting
        }

        fun getDownloadDirs(context: Context?): List<String> {
            return safe {
                context?.let { ctx ->
                    val defaultDir = getDefaultDir(ctx)?.filePath()

                    val first = listOf(defaultDir)
                    (try {
                        val currentDir = ctx.getBasePath().let { it.first?.filePath() ?: it.second }

                        (first +
                                ctx.getExternalFilesDirs("").mapNotNull { it.path } +
                                currentDir)
                    } catch (e: Exception) {
                        first
                    }).filterNotNull().distinct()
                }
            } ?: emptyList()
        }
    }

    // Open file picker
    private val pathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            // It lies, it can be null if file manager quits.
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            // RW perms for the path
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            context.contentResolver.takePersistableUriPermission(uri, flags)

            val file = SafeFile.fromUri(context, uri)
            val filePath = file?.filePath()
            println("Selected URI path: $uri - Full path: $filePath")

            // Stores the real URI using download_path_key
            // Important that the URI is stored instead of filepath due to permissions.
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putString(getString(R.string.download_path_key), uri.toString()) }

            // From URI -> File path
            // File path here is purely for cosmetic purposes in settings
            (filePath ?: uri.toString()).let {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit { putString(getString(R.string.download_path_pref), it) }
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

        getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
            val tempLangs = appLanguages.toMutableList()
            val current = getCurrentLocale(pref.context)
            val languageCodes = tempLangs.map { (_, _, iso) -> iso }
            val languageNames = tempLangs.map { (emoji, name, iso) ->
                val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
                "$flag $name"
            }
            val index = languageCodes.indexOf(current)

            activity?.showDialog(
                languageNames, index, getString(R.string.provider_lang_settings), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    CommonActivity.setLocale(activity, code)
                    settingsManager.edit { putString(getString(R.string.locale_key), code) }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs(context)

            val currentDir =
                settingsManager.getString(getString(R.string.download_path_pref), null)
                    ?: context?.let { ctx -> getDefaultDir(ctx)?.filePath() }

            activity?.showBottomDialog(
                dirs + listOf("Custom"),
                dirs.indexOf(currentDir),
                getString(R.string.download_path_pref),
                true,
                {}) {
                // Last = custom
                if (it == dirs.size) {
                    try {
                        pathPicker.launch(Uri.EMPTY)
                    } catch (e: Exception) {
                        logError(e)
                    }
                } else {
                    // Sets both visual and actual paths.
                    // key = used path
                    // pref = visual path
                    settingsManager.edit {
                        putString(getString(R.string.download_path_key), dirs[it])
                        putString(getString(R.string.download_path_pref), dirs[it])
                    }
                }
            }
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

                    it to fullName
                }

                context?.showMultiDialog(
                    names.map { it.second },
                    currentList,
                    getString(R.string.provider_lang_settings),
                    {}) { selectedList ->
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.provider_lang_key),
                            selectedList.map { names[it].first }.toMutableSet()
                        )
                    }

                    providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            val builder = AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)

            val binding = LogcatBinding.inflate(layoutInflater, null, false)
            builder.setView(binding.root)

            val dialog = builder.create()
            dialog.show()

            val logList = mutableListOf<String>()
            try {
                // https://developer.android.com/studio/command-line/logcat
                val process = Runtime.getRuntime().exec("logcat -d")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                bufferedReader.lineSequence().forEach { logList.add(it) }
            } catch (e: Exception) {
                logError(e) // kinda ironic
            }

            val adapter = LogcatAdapter().apply { submitList(logList) }
            binding.logcatRecyclerView.layoutManager = LinearLayoutManager(pref.context)
            binding.logcatRecyclerView.adapter = adapter

            binding.copyBtt.setOnClickListener {
                clipboardHelper(txt("Logcat"), logList.joinToString("\n"))
                dialog.dismissSafe(activity)
            }

            binding.clearBtt.setOnClickListener {
                Runtime.getRuntime().exec("logcat -c")
                dialog.dismissSafe(activity)
            }

            binding.saveBtt.setOnClickListener {
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(
                    Date(currentTimeMillis())
                )
                var fileStream: OutputStream?
                try {
                    fileStream = setupStream(
                        it.context,
                        "logcat_${date}",
                        "txt",
                        getDefaultDir(context = it.context)
                    ) ?: throw ErrorLoadingException("No stream")

                    fileStream.writer().use { writer -> writer.write(logList.joinToString("\n")) }
                    dialog.dismissSafe(activity)
                } catch (t: Throwable) {
                    logError(t)
                    showToast(t.message)
                }
            }

            binding.closeBtt.setOnClickListener {
                dialog.dismissSafe(activity)
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
                    settingsManager.edit {
                        putString(getString(R.string.theme_key), prefValues[it])
                    }
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
                    settingsManager.edit {
                        putString(getString(R.string.primary_color_key), prefValues[it])
                    }
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
                    settingsManager.edit {
                        putString(getString(R.string.rating_format_key), prefValues[it])
                    }
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
                    prefValues[1]//As soon as you install the app, everything is displayed as a list even though it is set to grid. This is because it was previously set to .first()
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.rating_format),
                false,
                {}) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.download_format_key), prefValues[it])
                    }
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