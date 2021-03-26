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
import android.view.View


val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var activity: MainActivity;
        var statusBarHeight = 0
        val api: MainAPI = NovelPassionProvider()

        fun loadResult(url : String) {
            activity.runOnUiThread {
                activity.supportFragmentManager.beginTransaction()
                    //?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .add(R.id.nav_host_fragment, ResultFragment(url))
                    .commit()
            }
        }
    }

    lateinit var mainContext: Context;

    fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activity = this
        mainContext = this.applicationContext

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_search )) // R.id.navigation_dashboard, R.id.navigation_notifications
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        BookDownloader.init()

        statusBarHeight = getStatusBarHeight()

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
    }
}