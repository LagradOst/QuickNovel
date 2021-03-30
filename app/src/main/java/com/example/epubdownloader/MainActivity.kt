package com.example.epubdownloader

import android.content.Context
import android.content.res.Resources

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.epubdownloader.ui.result.ResultFragment
import android.view.Window

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat


val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {
    companion object {
        var isInResults = false

        lateinit var activity: MainActivity
        var statusBarHeight = 0

        // val api: MainAPI = RoyalRoadProvider()//NovelPassionProvider()
        private val apis: Array<MainAPI> = arrayOf(NovelPassionProvider(), RoyalRoadProvider())
        val activeAPI : MainAPI = apis[1]

        fun getApiFromName(name : String) : MainAPI {
            for (a in apis) {
                if (a.name == name) {
                    return a
                }
            }
            return activeAPI
        }

        fun loadResult(url: String, apiName: String) {
            if (isInResults) return
            isInResults = true
            activity.runOnUiThread {
                activity.supportFragmentManager.beginTransaction()
                    //?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .add(R.id.homeRoot, ResultFragment().newInstance(url,apiName))
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

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
        //loadResult("https://www.royalroad.com/fiction/40182/only-villains-do-that", MainActivity.activeAPI.name)
    }
}