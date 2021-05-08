package com.lagradost.quicknovel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.lagradost.quicknovel.ui.result.ResultFragment

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.navigation.NavOptions
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.quicknovel.providers.*
import com.lagradost.quicknovel.ui.download.DownloadFragment
import kotlinx.android.synthetic.main.fragment_result.*
import java.util.HashSet
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

const val defProvider = 0

private val NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors()

// Instantiates the queue of Runnables as a LinkedBlockingQueue
private val workQueue: BlockingQueue<Runnable> =
    LinkedBlockingQueue<Runnable>()

// Sets the amount of time an idle thread waits before terminating
private const val KEEP_ALIVE_TIME = 1L

// Sets the Time Unit to seconds
private val KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS

// Creates a thread pool manager
public val threadPoolExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
    NUMBER_OF_CORES,       // Initial pool size
    NUMBER_OF_CORES,       // Max pool size
    KEEP_ALIVE_TIME,
    KEEP_ALIVE_TIME_UNIT,
    workQueue
)

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

class MainActivity : AppCompatActivity() {
    companion object {
        // === API ===
        lateinit var activity: MainActivity
        var statusBarHeight = 0

        lateinit var navOptions: NavOptions

        val apis: Array<MainAPI> = arrayOf(
            //AllProvider(),
            NovelPassionProvider(),
            BestLightNovelProvider(),
            WuxiaWorldOnlineProvider(),
            RoyalRoadProvider(),
            WuxiaWorldSiteProvider(),
            ReadLightNovelProvider(),
            BoxNovelProvider(),
        )

        val allApi: AllProvider = AllProvider()

        fun getApiFromName(name: String): MainAPI {
            for (a in apis) {
                if (a.name == name) {
                    return a
                }
            }
            return apis[1]
        }

        fun getApiSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

            return settingsManager.getStringSet(activity.getString(R.string.search_providers_list_key),
                setOf(apis[defProvider].name))?.toHashSet() ?: hashSetOf(apis[defProvider].name)
        }

        fun loadResult(url: String, apiName: String) {
            activity.runOnUiThread {
                activity.supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                    .add(R.id.homeRoot, ResultFragment().newInstance(url, apiName))
                    .commit()
            }
        }

        private fun getGridFormat(): String {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
            return settingsManager.getString(activity.getString(R.string.grid_format_key), "grid")!!
        }

        fun getGridFormatId(): Int {
            return when (getGridFormat()) {
                "list" -> R.layout.search_result_compact
                "compact_list" -> R.layout.search_result_super_compact
                else -> R.layout.search_result_grid
            }
        }

        fun fixPaddingStatusbar(v: View) {
            v.setPadding(v.paddingLeft, v.paddingTop + activity.getStatusBarHeight(), v.paddingRight, v.paddingBottom)
        }

        fun getGridIsCompact(): Boolean {
            return getGridFormat() != "grid"
        }

        fun loadResutFromUrl(url: String?) {
            if (url == null) return
            for (api in apis) {
                if (url.contains(api.mainUrl)) {
                    loadResult(url, api.name)
                    break
                }
            }
        }

        fun backPressed(): Boolean {
            val currentFragment = activity.supportFragmentManager.fragments.last {
                it.isVisible
            }

            if (currentFragment != null && activity.supportFragmentManager.fragments.size > 2) {
                //MainActivity.showNavbar()
                activity.supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
                return true
            }
            return false
        }

        @ColorInt
        fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
            val typedArray = obtainStyledAttributes(intArrayOf(resource))
            val color = typedArray.getColor(0, 0)
            typedArray.recycle()

            if (alphaFactor < 1f) {
                val alpha = (color.alpha * alphaFactor).roundToInt()
                return Color.argb(alpha, color.red, color.green, color.blue)
            }

            return color
        }

        fun semihideNavbar() {
            val w: Window? = activity.window // in Activity's onCreate() for instance
            if (w != null) {
                val uiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                w.decorView.systemUiVisibility = uiVisibility
                w.navigationBarColor =
                    activity.getResourceColor(android.R.attr.navigationBarColor, 0.7F) ?: Color.TRANSPARENT
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
                    w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                }
            }
        }

        fun getRating(score: Int): String {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)

            return when (settingsManager.getString(activity.getString(R.string.rating_format_key), "star")) {
                "point10" -> "${(score / 100)}/10"
                "point10d" -> "${"%.1f".format(score / 100f).replace(',', '.')}/10.0"
                "point100" -> "${score / 10}/100"
                else -> "%.2f".format(score.toFloat() / 200f).replace(',', '.') + "★" // star
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
        loadResutFromUrl(data)
        super.onNewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.findViewById<FrameLayout>(R.id.container).background =
            ColorDrawable(ResourcesCompat.getColor(resources, R.color.grayBackground, null)) // FIXES ICON

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        /*val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_search,
            R.id.navigation_download))*/ // R.id.navigation_dashboard, R.id.navigation_notifications
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestination, false)
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
                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings, null, navOptions)
                }
            }
            true
        }

        DataStore.init(this)
        BookDownloader.init()

        statusBarHeight = getStatusBarHeight()
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        val apiNames = getApiSettings()
        allApi.providersActive = apiNames
        val edit = settingsManager.edit()
        edit.putStringSet(getString(R.string.search_providers_list_key), allApi.providersActive)
        edit.apply()
        /*
        val apiName = settingsManager.getString(getString(R.string.provider_list_key), apis[0].name)
        activeAPI = getApiFromName(apiName ?: apis[0].name)
        val edit = settingsManager.edit()
        edit.putString(getString(R.string.provider_list_key, activeAPI.name), activeAPI.name)
        edit.apply()*/

        thread { // IDK, WARMUP OR SMTH, THIS WILL JUST REDUCE THE INITIAL LOADING TIME FOR DOWNLOADS, NO REAL USAGE, SEE @WARMUP
            val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
            for (k in keys) {
                DataStore.getKey<DownloadFragment.DownloadData>(k)
            }
        }

        thread {
            runAutoUpdate(this)
        }

        val data: String? = intent?.data?.toString()
        loadResutFromUrl(data)

        if (!BookDownloader.checkWrite()) {
            BookDownloader.requestRW()
        }
        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
        //loadResult("https://www.royalroad.com/fiction/40182/only-villains-do-that", MainActivity.activeAPI.name)
    }
}