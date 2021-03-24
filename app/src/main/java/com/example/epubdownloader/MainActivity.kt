package com.example.epubdownloader

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var activity: MainActivity;
    }

    public lateinit var mainContext: Context;

    val api: MainAPI = NovelPassionProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activity = this
        mainContext = this.applicationContext
        thread {
            api.load("https://www.novelpassion.com/novel/world-s-best-martial-artist")
        }
    }
}