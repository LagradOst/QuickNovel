package com.example.epubdownloader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import nl.siegmann.epublib.domain.Book
import java.io.FileInputStream

import nl.siegmann.epublib.epub.EpubReader
import androidx.core.text.HtmlCompat

import android.text.Spanned
import android.view.View
import android.widget.LinearLayout

class ReadActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.read_main)
        val read_text = findViewById<TextView>(R.id.read_text)
        val read_topmargin = findViewById<View>(R.id.read_topmargin)

        val parameter = read_topmargin.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        read_topmargin.layoutParams = parameter

        val intent = intent
        val path = intent.getStringExtra("path") //if it's a string you stored.
        val epubReader = EpubReader()
        val book = epubReader.readEpub(FileInputStream(path))
        val spanned = HtmlCompat.fromHtml(book.contents[0].reader.readText(), HtmlCompat.FROM_HTML_MODE_LEGACY)

        window.navigationBarColor = getColor(R.color.readerBackground)
        read_text.text = spanned
    }
}