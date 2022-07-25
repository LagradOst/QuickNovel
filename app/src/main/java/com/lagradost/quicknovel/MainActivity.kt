package com.lagradost.quicknovel

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.nicehttp.Requests
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.BookDownloader.checkWrite
import com.lagradost.quicknovel.BookDownloader.createQuickStream
import com.lagradost.quicknovel.BookDownloader.openQuickStream
import com.lagradost.quicknovel.BookDownloader.requestRW
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.mvvm.ioSafe
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.providers.RedditProvider
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.ui.result.ResultFragment
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.Apis.Companion.printProviders
import com.lagradost.quicknovel.util.BackupUtils.setUpBackup
import com.lagradost.quicknovel.util.Coroutines
import com.lagradost.quicknovel.util.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.getResourceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        var app = Requests().apply {
            defaultHeaders = mapOf("user-agent" to USER_AGENT)
        }

        // === API ===
        lateinit var activity: MainActivity

        lateinit var navOptions: NavOptions

        fun AppCompatActivity.loadResult(url: String, apiName: String, startAction: Int = 0) {
            runOnUiThread {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.enter_anim,
                        R.anim.exit_anim,
                        R.anim.pop_enter,
                        R.anim.pop_exit
                    )
                    .add(R.id.homeRoot, ResultFragment().newInstance(url, apiName))
                    .commit()
            }
        }

        fun Activity?.loadSearchResult(card: SearchResponse, startAction: Int = 0) {
            (this as AppCompatActivity?)?.loadResult(card.url, card.apiName, startAction)
        }

        fun AppCompatActivity.loadResultFromUrl(url: String?) {
            if (url == null) return
            for (api in apis) {
                if (url.contains(api.mainUrl)) {
                    loadResult(url, api.name)
                    break
                }
            }

            // kinda dirty ik
            val reddit = RedditProvider()
            reddit.isValidLink(url)?.let { name ->
                try {
                    Coroutines.main {
                        val uri = withContext(Dispatchers.IO) {
                            activity.createQuickStream(
                                BookDownloader.QuickStreamData(
                                    BookDownloader.QuickStreamMetaData(
                                        "Not found",
                                        name,
                                        reddit.name,
                                    ),
                                    null,
                                    mutableListOf(ChapterData("Single Post", url, null, null, null))
                                )
                            )
                        }
                        activity.openQuickStream(uri)
                    }
                } catch (e : Exception) {
                    logError(e)
                }
            }
        }

        fun AppCompatActivity.backPressed(): Boolean {
            val currentFragment = supportFragmentManager.fragments.last {
                it.isVisible
            }

            if (currentFragment is NavHostFragment) {
                val child = currentFragment.childFragmentManager.fragments.last {
                    it.isVisible
                }
                if (child is MainPageFragment) {
                    val navController = findNavController(R.id.nav_host_fragment)
                    navController.navigate(R.id.navigation_homepage, Bundle(), navOptions)
                    return true
                }
            }

            if (currentFragment != null && supportFragmentManager.fragments.size > 2) {
                if (supportFragmentManager.fragments.size == 3) {
                    window?.navigationBarColor =
                        colorFromAttribute(R.attr.darkBackground)
                }
                //MainActivity.showNavbar()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.enter_anim,
                        R.anim.exit_anim,
                        R.anim.pop_enter,
                        R.anim.pop_exit
                    )
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
                supportFragmentManager
                return true
            }
            return false
        }

        fun semihideNavbar() {
            val w: Window? = activity.window // in Activity's onCreate() for instance
            if (w != null) {
                val uiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                w.decorView.systemUiVisibility = uiVisibility
                w.navigationBarColor =
                    activity.getResourceColor(android.R.attr.navigationBarColor, 0.7F)
            }
        }

        fun showNavbar() {
            val w: Window? = activity.window // in Activity's onCreate() for instance
            if (w != null) {
                w.decorView.systemUiVisibility = 0
                w.navigationBarColor = android.R.attr.navigationBarColor
            }
        }

        fun transNavbar(trans: Boolean) {
            val w: Window? = activity.window // in Activity's onCreate() for instance
            if (w != null) {
                if (trans) {
                    w.setFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    )
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                }
            }
        }
    }

    /* // MOON READER WONT RETURN THE DURATION, BUT THIS CAN BE USED FOR SOME USER FEEDBACK IN THE FUTURE??? SEE @moonreader
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }*/


    fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    override fun onBackPressed() {
        if (backPressed()) return
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent?) {
        val data: String? = intent?.data?.toString()
        loadResultFromUrl(data)
        super.onNewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val themeName = settingsManager.getString("theme", "Dark")
        val currentTheme = when (themeName) {
            "Black" -> R.style.AppTheme
            "Dark" -> R.style.DarkAlternative
            "Light" -> R.style.LightMode
            else -> R.style.AppTheme
        }

        val themeOverlayName = settingsManager.getString("color_theme", "Blue")
        val currentOverlayTheme = when (themeOverlayName) {
            "Normal" -> R.style.OverlayPrimaryColorNormal
            "CarnationPink" -> R.style.OverlayPrimaryColorCarnationPink
            "Orange" -> R.style.OverlayPrimaryColorOrange
            "DarkGreen" -> R.style.OverlayPrimaryColorDarkGreen
            "Maroon" -> R.style.OverlayPrimaryColorMaroon
            "NavyBlue" -> R.style.OverlayPrimaryColorNavyBlue
            "Grey" -> R.style.OverlayPrimaryColorGrey
            "White" -> R.style.OverlayPrimaryColorWhite
            "Brown" -> R.style.OverlayPrimaryColorBrown
            "Banana" -> R.style.OverlayPrimaryColorBanana
            "Blue" -> R.style.OverlayPrimaryColorBlue
            "Purple" -> R.style.OverlayPrimaryColorPurple
            "Green" -> R.style.OverlayPrimaryColorGreen
            "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
            "Red" -> R.style.OverlayPrimaryColorRed
            else -> R.style.OverlayPrimaryColorNormal
        }
        //val isLightTheme = themeName == "Light"

        theme.applyStyle(
            currentTheme,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW
        theme.applyStyle(currentOverlayTheme, true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpBackup()

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)

        window.navigationBarColor = colorFromAttribute(R.attr.darkBackground)
        navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestinationId, false)
            .build()
/*
        navView.setOnNavigationItemReselectedListener { item ->
            return@setOnNavigationItemReselectedListener
        }*/
        navView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_homepage -> {
                    navController.navigate(R.id.navigation_homepage, null, navOptions)
                }
                R.id.navigation_search -> {
                    navController.navigate(R.id.navigation_search, null, navOptions)
                }
                R.id.navigation_download -> {
                    navController.navigate(R.id.navigation_download, null, navOptions)
                }
                R.id.navigation_history -> {
                    navController.navigate(R.id.navigation_history, null, navOptions)
                }
                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings, null, navOptions)
                }
            }
            true
        }

        navView.itemRippleColor =
            ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))

        val apiNames = getApiSettings()
        providersActive = apiNames
        val edit = settingsManager.edit()
        edit.putStringSet(getString(R.string.search_providers_list_key), providersActive)
        edit.apply()
        /*
        val apiName = settingsManager.getString(getString(R.string.provider_list_key), apis[0].name)
        activeAPI = getApiFromName(apiName ?: apis[0].name)
        val edit = settingsManager.edit()
        edit.putString(getString(R.string.provider_list_key, activeAPI.name), activeAPI.name)
        edit.apply()*/

        thread { // IDK, WARMUP OR SMTH, THIS WILL JUST REDUCE THE INITIAL LOADING TIME FOR DOWNLOADS, NO REAL USAGE, SEE @WARMUP
            val keys = getKeys(DOWNLOAD_FOLDER)
            for (k in keys) {
                getKey<DownloadFragment.DownloadData>(k)
            }
        }

        ioSafe {
            runAutoUpdate()
        }

        val data: String? = intent?.data?.toString()
        loadResultFromUrl(data)

        if (!checkWrite()) {
            requestRW()
        }

        printProviders()

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
        //loadResult("https://www.royalroad.com/fiction/40182/only-villains-do-that", MainActivity.activeAPI.name)
        thread {
            test()
        }
    }

    fun test() {
        // val response = app.get("https://ranobes.net/up/a-bored-lich/936969-1.html")
        // println(response.text)
    }
}