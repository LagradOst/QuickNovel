package com.lagradost.quicknovel

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import nl.siegmann.epublib.epub.EpubReader
import java.io.FileInputStream
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.thread


fun setHighLightedText(tv: TextView, start: Int, end: Int): Boolean {
    val wordToSpan: Spannable = SpannableString(tv.text)
    val spans = wordToSpan.getSpans<android.text.Annotation>(0, tv.text.length)
    for (s in spans) {
        wordToSpan.removeSpan(s)
    }
    wordToSpan.setSpan(android.text.Annotation("", "rounded"),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

    tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)

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
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String) {
                        canSpeek = true
                        //   startVoiceRecognitionActivity()
                    }

                    override fun onError(utteranceId: String) {
                        canSpeek = true
                    }

                    override fun onStart(utteranceId: String) {
                        println("DONE")
                    }
                })
            }
        } else {
            showMessage("Initialization Failed!")
        }
    }

    private fun speakOut(msg: String) {
        canSpeek = true
        /*canSpeek = false

        if (msg.isEmpty() || msg.isNullOrBlank()) {
            showMessage("No data")
            return
        }
        speekId++
        tts!!.speak(msg, TextToSpeech.QUEUE_FLUSH, null, speekId.toString())*/
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

    var canSpeek = true
    var speekId = 0

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
            thread {
                sleep(500)

                var index = 0
                while (true) {
                    val invalidStartChars = arrayOf(' ', '.', ',', '\n')
                    while (invalidStartChars.contains(read_text.text[index])) {
                        index++
                    }

                    val arry = arrayOf(".", "\n")
                    var endIndex = 10000
                    for (a in arry) {
                        val indexEnd = read_text.text.indexOf(a, index)
                        if (indexEnd < endIndex) {
                            endIndex = indexEnd
                        }
                    }

                    val message = read_text.text.substring(index, endIndex)
                    if (message
                            .replace("\n", "")
                            .replace("\t", "")
                            .replace(".", "").isNotEmpty()
                    ) {
                        canSpeek = false

                        var msg = message//Regex("\\p{L}").replace(message,"")
                        val invalidChars = arrayOf("-", "<", ">", "_", "^")
                        for (c in invalidChars) {
                            msg = msg.replace(c,"")
                        }
                        ReadActivity.readActivity.runOnUiThread {
                            setHighLightedText(read_text, index, endIndex)
                            if (msg.isNotEmpty()) {
                                speakOut(msg)
                            }
                        }
                        if (msg.isEmpty()) {
                            sleep(500)
                            canSpeek = true
                        }
                        while (!canSpeek) {
                            sleep(1)
                        }
                        //sleep(500)
                    }

                    index = endIndex + 1
                }
            }
            /*
            val lineCount = read_text.layout.lineCount
            thread {
                for (i in 0..lineCount) {
                    try {
                        ReadActivity.readActivity.runOnUiThread {
                            try {
                                if (read_text.layout == null) return@runOnUiThread
                                val start = read_text.layout.getLineStart(i)
                                val end = read_text.layout.getLineEnd(i)
                                val read = text.substring(start, end)
                                println("TEXT:" + read) // TTS
                                setHighLightedText(read_text, read)
                            } catch (e: Exception) {
                                return@runOnUiThread
                            }
                        }
                        sleep(800)
                    } catch (e: Exception) {
                        break
                    }
                }
            }*/
        }
    }
}