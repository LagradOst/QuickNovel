package com.lagradost.quicknovel.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.BaseStyles.whiteButtonColors
import com.lagradost.quicknovel.compose.CloudStreamPrimaryColor
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.modeToTheme
import com.lagradost.quicknovel.compose.perfToColor
import com.lagradost.quicknovel.compose.perfToMode
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.tachiyomi.Preference
import com.lagradost.quicknovel.tachiyomi.SearchableSettings
import com.lagradost.quicknovel.tachiyomi.TextPreferenceWidget
import com.lagradost.quicknovel.ui.settings.SettingsFragment.Companion.getBasePath
import com.lagradost.quicknovel.ui.settings.SettingsFragment.Companion.getDefaultDir
import com.lagradost.quicknovel.ui.settings.SettingsFragment.Companion.getDownloadDirs
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.BackupUtils
import com.lagradost.quicknovel.util.BackupUtils.setupStream
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.SubtitleHelper
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.safefile.SafeFile
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.title_settings)

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val store = AndroidPreferenceStore(context)
        val scope = rememberCoroutineScope()
        val downloadKeyStore = store.getString(stringResource(R.string.download_path_key))

        val downloadVisualStore = store.getString(
            stringResource(R.string.download_path_visual),
            safe { getDefaultDir(context)?.filePath() } ?: stringResource(R.string.unknown)
        )

        val pathPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                // It lies, it can be null if file manager quits.
                if (uri == null) return@rememberLauncherForActivityResult
                val context = context
                // RW perms for the path
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = SafeFile.fromUri(context, uri)
                val filePath = file?.filePath()
                println("Selected URI path: $uri - Full path: $filePath")

                // Stores the real URI using download_path_key
                // Important that the URI is stored instead of filepath due to permissions.
                downloadKeyStore.set(uri.toString())

                // From URI -> File path
                // File path here is purely for cosmetic purposes in settings
                downloadVisualStore.set(filePath ?: uri.toString())
            }

        val restoreFileSelector =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    BackupUtils.restoreFromUri(context, uri).onFailure { error ->
                        logError(error)
                        showToast(
                            txt(R.string.restore_failed_format, error.toString())
                        )
                    }.onSuccess {
                        showToast(
                            R.string.restore_success,
                            Toast.LENGTH_LONG
                        )
                        activity?.recreate()
                    }
                }
            }

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.general_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        icon = painterResource(R.drawable.ic_baseline_cloud_24),
                        title = stringResource(R.string.search_providers),
                        pref = store.searchProvidersList(),
                        entries = apis.associate { it.name to it.name }.toPersistentMap(),
                        subtitleProvider = { v, _ ->
                            stringResource(R.string.active_providers, v.size)
                        }),
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.ic_baseline_language_24),
                        title = stringResource(R.string.locale_settings),
                        pref = store.getString(
                            stringResource(R.string.locale_key),
                            "en",
                        ),
                        entries = arrayListOf(
                            /* begin language list */
                            ("en" to "English"),
                            ("tr" to "Türkçe"),
                            ("es" to "Español"),
                            ("ru" to "Русский"),
                            /* end language list */
                        ).sortedBy { it.second.lowercase() }.map { (code, name) ->
                            val flag = SubtitleHelper.getFlagFromIso(code) ?: "🌐"
                            code to "$flag $name"
                        }.associate { it }.toPersistentMap(),
                    ),
                    Preference.PreferenceItem.MultiSelectListPreference(
                        icon = painterResource(R.drawable.ic_baseline_language_24),
                        title = stringResource(R.string.provider_lang_settings),
                        pref = store.searchLangList(),
                        entries = apis.map { api ->
                            val lang = api.lang
                            val langName = SubtitleHelper.fromTwoLettersToLanguage(lang)!!
                            lang to langName
                        }.sortedBy { it.second.lowercase() }.map { (code, name) ->
                            val flag = SubtitleHelper.getFlagFromIso(code) ?: "🌐"
                            code to "$flag $name"
                        }.associate { it }.toPersistentMap(),
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.theme),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.ic_baseline_color_lens_24),
                        title = stringResource(R.string.theme),
                        pref = store.getString(
                            stringResource(R.string.theme_key),
                            "AmoledLight",
                        ),
                        entries = stringArrayResource(R.array.themes_names_values).zip(
                            stringArrayResource(R.array.themes_names)
                        ).associate { it }.toPersistentMap().let { mapping ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                mapping.removing("Monet")
                            } else {
                                mapping
                            }
                        },
                        iconProvider = { k, _ ->
                            val theme = modeToTheme(perfToMode(k), CloudStreamPrimaryColor.NORMAL)
                            RoundColor(theme.background)
                        }
                    ),
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.ic_baseline_color_lens_24),
                        title = stringResource(R.string.primary_color_settings),
                        pref = store.getString(
                            stringResource(R.string.primary_color_key),
                            "Normal",
                        ),
                        entries = stringArrayResource(R.array.themes_overlay_names_values).zip(
                            stringArrayResource(R.array.themes_overlay_names)
                        ).associate { it }.toPersistentMap().let { mapping ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                mapping.removing("Monet").removing("Monet2")
                            } else {
                                mapping
                            }
                        },
                        iconProvider = { k, _ ->
                            val color = perfToColor(k)
                            RoundColor(color.color)
                        }
                    ),
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.ic_baseline_star_24),
                        title = stringResource(R.string.rating_format),
                        pref = store.getString(
                            stringResource(R.string.rating_format_key),
                            "star",
                        ),
                        entries = stringArrayResource(R.array.RatingFormatData).zip(
                            stringArrayResource(R.array.RatingFormat)
                        ).associate { it }.toPersistentMap()
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.app_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.ic_baseline_system_update_24),
                        title = stringResource(R.string.check_for_update),
                        onClick = {
                            // Todo refactor
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    if (true != activity?.runAutoUpdate(false)) {
                                        showToast(R.string.no_update_found, Toast.LENGTH_SHORT)
                                    }
                                }
                            }
                        }
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        icon = painterResource(R.drawable.ic_baseline_notifications_active_24),
                        title = stringResource(R.string.show_app_updates),
                        subtitle = stringResource(R.string.show_app_updates_desc),
                        pref = store.getBoolean(
                            stringResource(R.string.auto_update_key),
                            true,
                        ),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.baseline_save_as_24),
                        title = stringResource(R.string.backup_settings),
                        onClick = {
                            scope.launch {
                                BackupUtils.createBackupFile(context, activity)
                                    .onFailure { error ->
                                        logError(error)
                                        if (error.message != null) {
                                            showToast(
                                                txt(
                                                    R.string.backup_failed_error_format,
                                                    error.toString()
                                                ),
                                                Toast.LENGTH_LONG
                                            )
                                        } else {
                                            showToast(
                                                R.string.backup_failed,
                                                Toast.LENGTH_LONG
                                            )
                                        }
                                    }
                                    .onSuccess {
                                        showToast(
                                            R.string.backup_success,
                                            Toast.LENGTH_LONG
                                        )
                                    }
                            }
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.baseline_restore_page_24),
                        title = stringResource(R.string.restore_settings),
                        onClick = {
                            try {
                                restoreFileSelector.launch(
                                    arrayOf(
                                        "text/plain",
                                        "text/str",
                                        "text/x-unknown",
                                        "application/json",
                                        "unknown/unknown",
                                        "content/unknown",
                                    )
                                )
                            } catch (t: Throwable) {
                                logError(t)
                                showToast(t.message)
                            }
                        }
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.reader_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        icon = painterResource(R.drawable.ic_baseline_menu_book_24),
                        title = stringResource(R.string.external_reader),
                        subtitle = stringResource(R.string.external_reader_desc),
                        pref = store.getBoolean(
                            stringResource(R.string.external_reader_key),
                            true,
                        ),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        icon = painterResource(R.drawable.ic_baseline_edit_24),
                        title = stringResource(R.string.remove_bloat),
                        subtitle = stringResource(R.string.remove_bloat_desc),
                        pref = store.getBoolean(
                            stringResource(R.string.remove_external_key),
                            true,
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.download_path_pref),
                        icon = painterResource(R.drawable.netflix_download),
                        pref = downloadVisualStore,
                        subtitleProvider = { v, e ->
                            e[v] ?: v
                        },
                        entries = (getDownloadDirs(context) + "Custom").associateWith { it }
                            .toPersistentMap(),
                        onValueChanged = { value ->
                            if (value != "Custom") {
                                downloadKeyStore.set(value)
                                true
                            } else {
                                try {
                                    pathPicker.launch(Uri.EMPTY)
                                } catch (t: Throwable) {
                                    logError(t)
                                }
                                false
                            }
                        }
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.info),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(R.string.show_log_cat),
                        content = {
                            var showDialog by remember { mutableStateOf(false) }
                            TextPreferenceWidget(
                                title = stringResource(R.string.show_log_cat),
                                subtitle = null,
                                icon = painterResource(R.drawable.baseline_description_24),
                                onPreferenceClick = {
                                    showDialog = true
                                },
                            )
                            if (showDialog) {
                                LogcatDialog {
                                    showDialog = false
                                }
                            }

                        }
                    ),

                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.ic_github_logo),
                        title = "Github",
                        subtitle = "https://github.com/LagradOst/QuickNovel",
                        onClick = {
                            openInBrowser("https://github.com/LagradOst/QuickNovel")
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.cs3_cloud),
                        title = "Anime and Movie app by the same devs",
                        subtitle = "https://github.com/recloudstream/cloudstream",
                        onClick = {
                            openInBrowser("https://github.com/recloudstream/cloudstream")
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.ic_baseline_discord_24),
                        title = "Join Discord",
                        subtitle = "https://discord.gg/5Hus6fM",
                        onClick = {
                            openInBrowser("https://discord.gg/5Hus6fM")
                        }
                    ),
                )
            ))
    }
}


@Composable
fun LogcatDialog(dismiss: () -> Unit) {

    val list = remember { mutableStateOf(persistentListOf<LogcatItem>()) }
    LaunchedEffect(dismiss) {
        try {
            // https://developer.android.com/studio/command-line/logcat
            val process = Runtime.getRuntime().exec("logcat --binary -d")
            val items = arrayListOf<LogcatItem>()
            LogcatBinaryParser(process.inputStream).use { parser ->
                while (true) {
                    val item = parser.parseItem() ?: break
                    items.add(item)
                }
            }

            list.value = items.toPersistentList()
        } catch (e: Exception) {
            logError(e) // kinda ironic
        }
    }

    val context = LocalContext.current
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = {
            Text(text = stringResource(R.string.log_cat))
        },
        text = {
            LazyColumn {
                items(items = list.value) { item ->
                    LogcatItem(item)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(
                        Date(System.currentTimeMillis())
                    )
                    var fileStream: OutputStream?
                    try {
                        fileStream = setupStream(
                            context,
                            "logcat_${date}",
                            "txt",
                            context.getBasePath().first ?: getDefaultDir(context)
                            ?: throw IOException("No file")
                        ) ?: throw ErrorLoadingException("No stream")

                        fileStream.writer().use { writer ->
                            list.value.forEach {
                                writer.write(it.toString())
                                writer.write("\n\n")
                            }
                        }
                        dismiss()
                        showToast(R.string.downloaded)
                    } catch (t: Throwable) {
                        logError(t)
                        showToast(t.message)
                    }
                }, colors = whiteButtonColors
            ) { Text(text = stringResource(R.string.save)) }
            Button(
                onClick = {
                    clipboardHelper(
                        txt("Logcat"),
                        list.value.joinToString(separator = "\n\n") { it.toString() }
                    )
                }, colors = whiteButtonColors
            ) { Text(text = stringResource(R.string.copylog)) }
            Button(
                onClick = {
                    try {
                        Runtime.getRuntime().exec("logcat -c")
                    } catch (t: Throwable) {
                        logError(t)
                    }
                    dismiss()
                }, colors = whiteButtonColors
            ) { Text(text = stringResource(R.string.clear)) }
        },
        dismissButton = {
            Button(
                onClick = dismiss, colors = blackButtonColors
            ) { Text(text = stringResource(R.string.close)) }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}


@Composable
fun LogcatItem(item: LogcatItem) {
    val interactionSource = remember { MutableInteractionSource() }

    val color = when (item.level) {
        LogcatLevel.Fatal -> Color.Magenta
        LogcatLevel.Error -> Color.Red
        LogcatLevel.Warning -> Color.Yellow
        LogcatLevel.Info -> Color.White
        LogcatLevel.Debug -> Color.Green
        LogcatLevel.Verbose -> Color.Gray
        null -> Color.Transparent
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        item.level?.identifier?.let { value ->
            Text(
                value,
                modifier = Modifier
                    .padding(2.dp)
                    .rounded()
                    .background(colors.onBackground)
                    .padding(4.dp),
                color = colors.surfaceVariant
            )
        }
        Text(
            item.date.toHumanReadable(),
            modifier = Modifier
                .padding(2.dp)
                .rounded()
                .background(colors.surfaceVariant)
                .padding(4.dp),
            color = colors.onBackground
        )
        Text(
            item.tag,
            modifier = Modifier
                .padding(2.dp)
                .rounded()
                .background(colors.surfaceVariant)
                .padding(4.dp),
            color = colors.onBackground
        )
    }
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    clipboardHelper(txt("Logcat"), item.toString())
                })
            .rounded()
            .ripple(interactionSource)
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .circle()
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            item.message,
            style = BaseStyles.textStyle
        )
    }
}

@Composable
fun RoundColor(color: Color) {
    Box(
        modifier = Modifier
            .padding(start = 15.dp)
            .size(20.dp)
            .border(width = 1.5.dp, shape = CircleShape, color = colors.onBackground)
            .background(color, CircleShape)
    )
}

@PreviewLightDark
@Composable
private fun SettingScreenPreview() {
    val screen = SettingScreen()
    CloudStreamTheme {
        screen.Content()
    }
}