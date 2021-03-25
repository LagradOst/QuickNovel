package com.example.epubdownloader

import android.content.Context

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.epubdownloader.ui.result.ResultFragment

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var activity: MainActivity;
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

        //loadResult("https://www.novelpassion.com/novel/battle-frenzy")
    }
}