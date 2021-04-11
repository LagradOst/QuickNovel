package com.lagradost.quicknovel

import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import nl.siegmann.epublib.epub.EpubReader
import java.io.FileInputStream
import java.lang.Exception
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import java.util.Locale


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
    lateinit var read_scroll: ScrollView
    lateinit var read_time: TextView
    lateinit var read_chapter_name: TextView

    var canSpeek = true
    var speekId = 0
    var isTTSRunning = true
    val lockTTS = true
    var minScroll = 0
    var maxScroll = 0

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    fun updateTimeText() {
        val currentTime: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        if (read_time != null) {
            read_time.text = currentTime
            read_time.postDelayed({ -> updateTimeText() }, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.read_main)
        read_text = findViewById(R.id.read_text)
        read_scroll = findViewById(R.id.read_scroll)
        read_chapter_name = findViewById(R.id.read_chapter_name)
        read_time = findViewById(R.id.read_time)
        val read_topmargin = findViewById<View>(R.id.read_topmargin)

        hideSystemUI()

        readActivity = this
        tts = TextToSpeech(this, this)


        val parameter = read_topmargin.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        read_topmargin.layoutParams = parameter

        val intent = intent
        path = intent.getStringExtra("path")!!

        val epubReader = EpubReader()
        val book = epubReader.readEpub(FileInputStream(path))

        val chapterIndex = 0
        val chapter = book.tableOfContents.tocReferences[chapterIndex]
        val spanned = HtmlCompat.fromHtml(
            chapter.resource.reader.readText().replace("...", "…"),
            HtmlCompat.FROM_HTML_MODE_LEGACY)

        read_chapter_name.text = chapter.title ?: "Chapter ${chapterIndex + 1}"

        book.tableOfContents.allUniqueResources
        window.navigationBarColor = getColor(R.color.readerBackground)
        read_text.text = spanned
        val text = read_text.text
        //println("TEXT:" + book.contents[0].reader.readText())

        read_scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (lockTTS && isTTSRunning) {
                if (read_scroll.height + scrollY <= minScroll) {
                    read_scroll.scrollTo(0, minScroll - read_scroll.height)
                } else if (scrollY >= maxScroll) {
                    read_scroll.scrollTo(0, maxScroll)
                }
            }
        }

        updateTimeText()

        read_text.post {
            val startLines: ArrayList<Int> = ArrayList()
            for (i in 0..read_text.layout.lineCount) {
                startLines.add(read_text.layout.getLineStart(i))
            }
            thread {
                isTTSRunning = true
                var index = 0
                while (true) {
                    val invalidStartChars =
                        arrayOf(' ', '.', ',', '\n', '\"',
                            '\'', '’', '‘', '“', '”', '«', '»', '「', '」', '…')
                    while (invalidStartChars.contains(read_text.text[index])) {
                        index++
                        if (index >= read_text.text.length) {
                            return@thread //TODO NEXT CHAPTER
                        }
                    }

                    var endIndex = Int.MAX_VALUE
                    for (a in arrayOf(".", "\n", ";", "?", ":")) {
                        val indexEnd = read_text.text.indexOf(a, index)
                        if (indexEnd == -1) continue
                        /*while (true) {
                            if (indexEnd + 1 < read_text.text.length) {
                                if (read_text.text[indexEnd + 1] == '.') {
                                    indexEnd++
                                    continue
                                }
                            }
                            break
                        }*/

                        if (indexEnd < endIndex) {
                            endIndex = indexEnd + 1
                        }
                    }


                    if (endIndex > read_text.text.length) {
                        endIndex = read_text.text.length
                    }
                    if (index >= read_text.text.length) {
                        return@thread //TODO NEXT CHAPTER
                    }

                    val invalidEndChars =
                        arrayOf('\n')
                    while (true) {
                        var containsInvalidEndChar = false
                        for (a in invalidEndChars) {
                            if (endIndex <= 0 || endIndex > read_text.text.length) break
                            if (read_text.text[endIndex - 1] == a) {
                                containsInvalidEndChar = true
                                endIndex--
                            }
                        }
                        if (!containsInvalidEndChar) {
                            break
                        }
                    }


                    try {
                        // THIS PART IF FOR THE SPEAK PART, REMOVING STUFF THAT IS WACK
                        val message = read_text.text.substring(index, endIndex)
                        var msg = message//Regex("\\p{L}").replace(message,"")
                        val invalidChars =
                            arrayOf("-", "<", ">", "_", "^", "\'", "«", "»", "「", "」", "—", "¿")
                        for (c in invalidChars) {
                            msg = msg.replace(c, "")
                        }
                        msg = msg.replace("...", ",")
                        /*.replace("…", ",")*/

                        if (msg
                                .replace("\n", "")
                                .replace("\t", "")
                                .replace(".", "").isNotEmpty()
                        ) {
                            canSpeek = false

                            readActivity.runOnUiThread {
                                for (s in 0..startLines.size) {
                                    if (startLines[s] > index) {
                                        for (e in s..startLines.size) {
                                            if (startLines[e] > endIndex) {
                                                if (read_text.layout == null) return@runOnUiThread
                                                maxScroll = read_text.layout.getLineTop(s)
                                                minScroll = read_text.layout.getLineBottom(e)
                                                if (read_scroll.height + read_scroll.scrollY < minScroll ||
                                                    read_scroll.scrollY > maxScroll
                                                ) {
                                                    read_scroll.scrollTo(0, read_text.layout.getLineTop(s))
                                                }
                                                break
                                            }
                                        }
                                        //read_scroll.scrollTo(0, read_text.layout.getLineTop(i) - 200) // SKIP SNAP SETTING

                                        break
                                    }
                                }
                                setHighLightedText(read_text, index, endIndex)
                                if (msg.isNotEmpty()) {
                                    speakOut(msg)
                                }
                            }
                            sleep(1000)
                            if (msg.isEmpty()) {
                                sleep(500)
                                canSpeek = true
                            }
                            while (!canSpeek) {
                                sleep(1)
                            }
                            //sleep(500)
                        }
                    } catch (e: Exception) {
                        println(e)
                        return@thread
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