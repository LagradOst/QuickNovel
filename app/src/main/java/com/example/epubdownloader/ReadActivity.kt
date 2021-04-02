package com.example.epubdownloader

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.speech.tts.TextToSpeech
import nl.siegmann.epublib.domain.Book
import java.io.FileInputStream

import nl.siegmann.epublib.epub.EpubReader
import androidx.core.text.HtmlCompat

import android.text.Spanned
import android.view.View
import android.widget.LinearLayout
import android.text.Spannable

import android.text.style.BackgroundColorSpan

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import java.lang.Exception
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.thread
import android.util.Log

import android.text.SpannableStringBuilder
import androidx.core.text.buildSpannedString
import androidx.core.text.clearSpans
import androidx.core.text.inSpans


fun setHighLightedText(tv: TextView, textToHighlight: String): Boolean {
    val tvt = tv.text.toString()
    val wordToSpan: Spannable = SpannableString(tv.text)
    val ofe = tvt.indexOf(textToHighlight, 0)
    if (ofe == -1) {
        return false
    } else {
        wordToSpan.clearSpans()
        wordToSpan.setSpan(android.text.Annotation("","rounded"), ofe,  ofe + textToHighlight.length-1 + 50, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
    }
    return true
}

class ReadActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    companion object {
        lateinit var readActivity: ReadActivity
    }

    private var tts: TextToSpeech? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                showMessage("This Language is not supported")
            } else { // TESTING

            }
        } else {
            showMessage("Initialization Failed!")
        }
    }

    private fun speakOut(msg: String) {
        if (msg.isEmpty() || msg.isNullOrBlank()) {
            showMessage("No data")
            return
        }
        tts!!.speak(msg, TextToSpeech.QUEUE_FLUSH, null)
    }

    public override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    lateinit var path: String
    lateinit var read_text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.read_main)

        readActivity = this
        tts = TextToSpeech(this, this)

        val read_topmargin = findViewById<View>(R.id.read_topmargin)

        val parameter = read_topmargin.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        read_topmargin.layoutParams = parameter

        val intent = intent
        path = intent.getStringExtra("path")!!


        read_text = findViewById(R.id.read_text)

        val epubReader = EpubReader()
        val book = epubReader.readEpub(FileInputStream(path))

        val spanned = HtmlCompat.fromHtml(book.contents[0].reader.readText(), HtmlCompat.FROM_HTML_MODE_LEGACY)

        window.navigationBarColor = getColor(R.color.readerBackground)
        read_text.text = spanned
        val text = read_text.text
        read_text.post {
            val lineCount = read_text.layout.lineCount
            thread {
                for (i in 0..lineCount) {
                    try {
                        ReadActivity.readActivity.runOnUiThread {
                            try {
                                if(read_text.layout == null) return@runOnUiThread
                                val start = read_text.layout.getLineStart(i)
                                val end = read_text.layout.getLineEnd(i)
                                val read = text.substring(start, end)
                                println("TEXT:" + read) // TTS
                                setHighLightedText(read_text, read)
                            } catch (e : Exception) {
                                return@runOnUiThread
                            }
                        }
                        sleep(800)
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }
    }
}