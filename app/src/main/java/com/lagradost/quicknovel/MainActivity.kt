package com.lagradost.quicknovel

import android.content.res.Resources

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.lagradost.quicknovel.ui.result.ResultFragment

import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.providers.*
import com.lagradost.quicknovel.ui.download.DownloadFragment
import java.util.HashSet
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

const val defProvider = 0

class MainActivity : AppCompatActivity() {
    companion object {
        var isInResults = false

        lateinit var activity: MainActivity
        var statusBarHeight = 0

        val apis: Array<MainAPI> = arrayOf(
            //AllProvider(),
            NovelPassionProvider(),
            RoyalRoadProvider(),
            BestLightNovelProvider(),
            WuxiaWorldOnlineProvider()
        )

        val allApi: AllProvider = AllProvider()

        var activeAPI: MainAPI = apis[1]

        fun getApiFromName(name: String): MainAPI {
            for (a in apis) {
                if (a.name == name) {
                    return a
                }
            }
            return activeAPI
        }

        fun getApiSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

            return settingsManager.getStringSet(activity.getString(R.string.search_providers_list_key),
                setOf(apis[defProvider].name))?.toHashSet() ?: hashSetOf(apis[defProvider].name)
        }

        fun loadResult(url: String, apiName: String) {
            if (isInResults) return
            isInResults = true
            activity.runOnUiThread {
                activity.supportFragmentManager.beginTransaction()
                    //?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .add(R.id.homeRoot, ResultFragment().newInstance(url, apiName))
                    .commit()
            }
        }

        fun backPressed(): Boolean {
            val currentFragment = activity.supportFragmentManager.fragments.last {
                it.isVisible
            }

            if (isInResults && currentFragment != null) {
                activity.supportFragmentManager.beginTransaction()
                    //?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
                return true
            }
            return false
        }
    }

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
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_search,
            R.id.navigation_download)) // R.id.navigation_dashboard, R.id.navigation_notifications
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
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

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
        //loadResult("https://www.royalroad.com/fiction/40182/only-villains-do-that", MainActivity.activeAPI.name)
    }
}