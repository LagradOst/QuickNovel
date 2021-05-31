package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import com.lagradost.quicknovel.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.UIHelper.popupMenu
import com.lagradost.quicknovel.ui.OrientationType
import kotlinx.android.synthetic.main.read_main.*
import kotlinx.coroutines.*
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.FileInputStream
import java.lang.Exception
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt


const val OVERFLOW_NEXT_CHAPTER_DELTA = 600
const val OVERFLOW_NEXT_CHAPTER_SHOW_PROCENTAGE = 10
const val OVERFLOW_NEXT_CHAPTER_NEXT = 90
const val OVERFLOW_NEXT_CHAPTER_SAFESPACE = 20
const val TOGGLE_DISTANCE = 20f

fun clearTextViewOfSpans(tv: TextView) {
    val wordToSpan: Spannable = SpannableString(tv.text)
    val spans = wordToSpan.getSpans<android.text.Annotation>(0, tv.text.length)
    for (s in spans) {
        wordToSpan.removeSpan(s)
    }
    tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
}

fun setHighLightedText(tv: TextView, start: Int, end: Int): Boolean {
    try {
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
    } catch (e: Exception) {
        return false
    }
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
                    }

                    override fun onError(utteranceId: String) {
                        canSpeek = true
                    }

                    override fun onStart(utteranceId: String) {

                    }
                })
            }
        } else {
            showMessage("Initialization Failed!")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        stopTTS()
        globalTTSLines.clear()
    }

    private fun speakOut(msg: String) {
        canSpeek = false

        if (msg.isEmpty() || msg.isBlank()) {
            showMessage("No data")
            return
        }
        speekId++
        if (tts != null) {
            tts!!.speak(msg, TextToSpeech.QUEUE_FLUSH, null, speekId.toString())
        }
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
    /*
    lateinit var read_text: TextView
    lateinit var read_scroll: ScrollView
    lateinit var read_time: TextView
    lateinit var read_chapter_name: TextView*/

    var canSpeek = true
    var speekId = 0
    var isTTSRunning = false
    val lockTTS = true
    var minScroll = 0
    var maxScroll = 0

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // if (hasFocus) hideSystemUI()
    }

    fun changeStatusBarState(hide: Boolean) {
        /* if (hide) {
              window?.setFlags(
                  WindowManager.LayoutParams.FLAG_FULLSCREEN,
                  WindowManager.LayoutParams.FLAG_FULLSCREEN
              )
          } else {
              window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
          }*/
    }

    var isHidden = true

    private fun hideSystemUI() {
        isHidden = true
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
        changeStatusBarState(true)

        reader_bottom_view.translationY = 0f
        ObjectAnimator.ofFloat(reader_bottom_view, "translationY", reader_bottom_view.height.toFloat()).apply {
            duration = 200
            start()
        }.doOnEnd {
            reader_bottom_view.visibility = View.GONE
        }
        read_toolbar_holder.translationY = 0f
        ObjectAnimator.ofFloat(read_toolbar_holder, "translationY", -read_toolbar_holder.height.toFloat()).apply {
            duration = 200
            start()
        }.doOnEnd {
            read_toolbar_holder.visibility = View.GONE
        }
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        isHidden = false
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                /* or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION*/
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        changeStatusBarState(false)

        read_toolbar_holder.visibility = View.VISIBLE
        reader_bottom_view.visibility = View.VISIBLE
        reader_bottom_view.translationY = reader_bottom_view.height.toFloat()
        read_toolbar_holder.translationY = -read_toolbar_holder.height.toFloat()
        ObjectAnimator.ofFloat(reader_bottom_view, "translationY", 0f).apply {
            duration = 200
            start()
        }

        ObjectAnimator.ofFloat(read_toolbar_holder, "translationY", 0f).apply {
            duration = 200
            start()
        }

    }

    fun updateTimeText() {
        val currentTime: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        if (read_time != null) {
            read_time.text = currentTime
            read_time.postDelayed({ -> updateTimeText() }, 1000)
        }
    }

    private fun loadNextChapter() : Boolean {
        return if (currentChapter >= maxChapter - 1) {
            Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
            false
        } else {
            loadChapter(currentChapter + 1, true)
            read_scroll.smoothScrollTo(0, 0)
            true
        }
    }

    private fun loadPrevChapter() : Boolean {
        return if (currentChapter <= 0) {
            false
            //Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
        } else {
            loadChapter(currentChapter - 1, false)
            true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        //TODO SETTING VOLUME KEYS
        return if (isHidden && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (textLines != null) {
                val readHeight = read_scroll.height - read_overlay.height
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if(isTTSRunning) {
                        nextTTSLine()
                    }
                    else {
                        if (read_scroll.scrollY >= getScrollRange()) {
                            loadNextChapter()
                        } else {
                            for (t in textLines!!) {
                                if (t.topPosition > mainScrollY + readHeight) {
                                    read_scroll.scrollTo(0, t.topPosition)
                                    read_scroll.fling(0)
                                    return true
                                }
                            }
                            loadNextChapter()
                        }
                    }
                } else {
                    if(isTTSRunning) {
                        prevTTSLine()
                    }
                    else {
                        if (read_scroll.scrollY <= 0) {
                            loadPrevChapter()
                        } else {
                            for (t in textLines!!) {
                                if (t.topPosition > mainScrollY - read_scroll.height) {
                                    read_scroll.scrollTo(0, t.topPosition)
                                    read_scroll.fling(0)
                                    return true
                                }
                            }
                            loadPrevChapter()
                        }
                    }
                }

                return true
            }
            super.onKeyDown(keyCode, event)
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    data class TextLine(val startIndex: Int, val endIndex: Int, val topPosition: Int, val bottomPosition: Int)

    lateinit var book: Book
    lateinit var chapterTitles: ArrayList<String>
    var maxChapter: Int = 0

    var currentChapter = 0
    var textLines: ArrayList<TextLine>? = null
    var mainScrollY = 0
    var scrollYOverflow = 0f


    var startY: Float? = null
    var scrollStartY: Float = 0f
    var scrollStartX: Float = 0f
    var scrollDistance: Float = 0f

    var overflowDown: Boolean = true
    var chapterName: String? = null

    @SuppressLint("SetTextI18n")
    fun updateChapterName(scrollX: Int) {
        if (read_scroll.height == 0) {
            read_chapter_name.text = chapterName!!
            return
        }
        val height = maxOf(1, getScrollRange())
        val chaptersTotal = ceil(height.toDouble() / read_scroll.height).toInt()
        val currentChapter = read_scroll.scrollY * chaptersTotal / height
        read_chapter_name.text = "${chapterName!!} (${currentChapter + 1}/${chaptersTotal + 1})"
    }

    private fun loadChapter(chapterIndex: Int, scrollToTop: Boolean, scrollToRemember: Boolean = false) {

        DataStore.setKey(EPUB_CURRENT_POSITION, book.title, chapterIndex)

        fun scroll() {
            readActivity.runOnUiThread {
                if (scrollToRemember) {
                    val scrollToY = DataStore.getKey<Int>(EPUB_CURRENT_POSITION_SCROLL, book.title, null)
                    if (scrollToY != null) {
                        read_scroll.scrollTo(0, scrollToY)
                        read_scroll.fling(0)
                        return@runOnUiThread
                    }
                }

                val scrollToY = if (scrollToTop) 0 else getScrollRange()
                read_scroll.scrollTo(0, scrollToY)
                read_scroll.fling(0)
                updateChapterName(scrollToY)

            }
        }
        read_text.alpha = 0f

        val chapter = book.tableOfContents.tocReferences[chapterIndex]
        chapterName = chapter.title ?: "Chapter ${chapterIndex + 1}"
        currentChapter = chapterIndex

        read_toolbar.title = book.title
        read_toolbar.subtitle = chapterName
        read_title_text.text = chapterName

        updateChapterName(0)

        val txt = chapter.resource.reader.readText()
            //TODO NICE TABLE
            .replace("<tr>", "<div style=\"text-align: center\">")
            .replace("</tr>", "</div>")
            .replace("<td>", "")
            .replace("</td>", " ")
        val document = Jsoup.parse(txt)

        // REMOVE USELESS STUFF THAT WONT BE USED IN A NORMAL TXT
        document.select("style").remove()
        document.select("script").remove()

        for (a in document.allElements) {
            if (a != null && a.hasText() &&
                (a.text() == chapterName || (a.tagName() == "h3" && a.text().startsWith("Chapter ${chapterIndex + 1}")))
            ) { // IDK, SOME MIGHT PREFER THIS SETTING??
                a.remove() // THIS REMOVES THE TITLE
                break
            }
        }

        /*
          var lastBr = false
        lastBr = if (a.tagName() == "br") { // REMOVE DUPLICATE br, SO NO LONG LINE BREAKS
            if (lastBr) {
                a.remove()
            }
            true
        } else {
            false
        }*/

        val spanned = HtmlCompat.fromHtml(
            document.html()
                //.replace("\n\n", "\n") // REMOVES EMPTY SPACE
                .replace("...", "…") // MAKES EASIER TO WORK WITH
                .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER
                .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER
            , HtmlCompat.FROM_HTML_MODE_LEGACY)
        println("TEXT:" + document.html())
        read_text.text = spanned
        read_text.post {
            loadTextLines()
            scroll()
            read_text.alpha = 1f

            globalTTSLines.clear()
            interuptTTS()
            if(isTTSRunning) {
                startTTS(true)
            }
        }
    }

    private fun loadTextLines() {
        textLines = ArrayList()
        val lay = read_text.layout ?: return
        for (i in 0..lay.lineCount) {
            try {
                if (lay == null) return
                textLines?.add(TextLine(lay.getLineStart(i),
                    lay.getLineEnd(i),
                    lay.getLineTop(i),
                    lay.getLineBottom(i)))
            } catch (e: Exception) {
                println("EX: $e")
            }
        }
    }

    private fun interuptTTS() {
        if (tts != null) {
            tts!!.stop()
        }
        canSpeek = true
    }

    private fun nextTTSLine() {
        //readFromIndex++
        interuptTTS()
    }

    private fun prevTTSLine() {
        readFromIndex -= 2
        interuptTTS()
    }

    private fun stopTTS() {
        isTTSRunning = false
        clearTextViewOfSpans(read_text)
        interuptTTS()
    }

    data class TTSLine(
        val speakOutMsg: String,
        val startIndex: Int,
        val endIndex: Int,
        val minScroll: Int,
        val maxScroll: Int,
    )

    var globalTTSLines = ArrayList<TTSLine>()

    private fun prepareTTS(callback: (Boolean) -> Unit) {
        val job = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + job)
        uiScope.launch {
            val ttsLines = ArrayList<TTSLine>()

            var index = 0
            while (true) {
                val invalidStartChars =
                    arrayOf(' ', '.', ',', '\n', '\"',
                        '\'', '’', '‘', '“', '”', '«', '»', '「', '」', '…')
                while (invalidStartChars.contains(read_text.text[index])) {
                    index++
                    if (index >= read_text.text.length) {
                        globalTTSLines = ttsLines
                        callback.invoke(true)
                        return@launch //TODO NEXT CHAPTER
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
                    globalTTSLines = ttsLines
                    callback.invoke(true)
                    return@launch //TODO NEXT CHAPTER
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
                        msg = msg.replace(c, " ")
                    }
                    msg = msg.replace("...", " ")

                    /*.replace("…", ",")*/

                    if (msg
                            .replace("\n", "")
                            .replace("\t", "")
                            .replace(".", "").isNotEmpty()
                    ) {
                        /*for (s in 0..startLines.size) {
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
                               }*/
                        if (textLines == null)
                            return@launch
                        for (s in 0..(textLines?.size ?: 0)) {
                            val start = textLines?.get(s) ?: return@launch

                            if (start.startIndex > index) {
                                for (e in s..(textLines?.size ?: 0)) {
                                    val end = textLines?.get(e) ?: return@launch
                                    if (end.startIndex > endIndex) {
                                        if (read_text.layout == null) return@launch
                                        maxScroll = start.topPosition//read_text.layout.getLineTop(s)
                                        minScroll = end.bottomPosition // read_text.layout.getLineBottom(e)
                                        ttsLines.add(TTSLine(msg, index, endIndex, minScroll, maxScroll))
                                        /*if (read_scroll.height + read_scroll.scrollY < minScroll ||
                                            read_scroll.scrollY > maxScroll
                                        ) {
                                            read_scroll.scrollTo(0, read_text.layout.getLineTop(s))
                                        }*/
                                        break
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    println(e)
                    return@launch
                }
                index = endIndex + 1
            }
        }
    }

    private var readFromIndex = 0
    private fun runTTS(fromStart: Boolean = false) {
        isTTSRunning = true

        val job = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + job)
        uiScope.launch {
            if(fromStart) {
                readFromIndex = 0
            }
            else {
                for ((startIndex, line) in globalTTSLines.withIndex()) {
                    if (read_scroll.scrollY + read_title_text.height - 10 <= line.minScroll) {
                        readFromIndex = startIndex
                        break
                    }
                }
            }

            while (true) {
                try {
                    if (!isTTSRunning) return@launch
                    if(globalTTSLines.size == 0) return@launch
                    if(readFromIndex < 0) {
                        if(!loadPrevChapter()) {
                            stopTTS()
                        }
                        return@launch
                    }
                    else if(readFromIndex >= globalTTSLines.size) {
                        if(!loadNextChapter()) {
                            stopTTS()
                        }
                        return@launch
                    }
                    println("INDEX::: " + globalTTSLines.size + "||" + readFromIndex)
                    val line = globalTTSLines[readFromIndex]

                    setHighLightedText(read_text, line.startIndex, line.endIndex)
                    minScroll = line.minScroll
                    maxScroll = line.maxScroll

                    if (read_scroll != null) {
                        checkTTSRange(read_scroll.scrollY, true)
                    }

                    val msg = line.speakOutMsg
                    if (msg.isNotEmpty() && msg.isNotBlank()) {
                        speakOut(msg)
                    }

                    if (msg.isEmpty()) {
                        delay(500)
                        canSpeek = true
                    }
                    while (!canSpeek) {
                        delay(10)
                        if (!isTTSRunning) return@launch
                    }
                    readFromIndex++
                } catch (e: Exception) {
                    println(e)
                    return@launch
                }
            }
        }
    }

    private fun startTTS(fromStart : Boolean = false) {
        if (globalTTSLines.size <= 0) {
            prepareTTS {
                if (it) {
                    runTTS(fromStart)
                } else {
                    Toast.makeText(this, "Error parsing text", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runTTS(fromStart)
        }
    }

    private fun checkTTSRange(scrollY: Int, scrollToTop: Boolean = false) {
        if (lockTTS && isTTSRunning) {
            if (read_scroll.height + scrollY - read_title_text.height -10.toPx <= minScroll) { // FOR WHEN THE TEXT IS ON THE BOTTOM OF THE SCREEN
                if(scrollToTop) {
                    read_scroll.scrollTo(0, maxScroll + read_title_text.height)
                }
                else {
                    read_scroll.scrollTo(0, minScroll - read_scroll.height + read_title_text.height + 10.toPx)
                }
                read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
            } else if (scrollY - read_title_text.height >= maxScroll) { // WHEN TEXT IS ON TOP
                read_scroll.scrollTo(0, maxScroll + read_title_text.height)
                read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
            }
        }
    }

    fun selectChapter() {
        val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
        //builderSingle.setIcon(R.drawable.ic_launcher)
        builderSingle.setTitle(chapterTitles[currentChapter]) //  "Select Chapter"

        val arrayAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)

        arrayAdapter.addAll(chapterTitles)

        builderSingle.setNegativeButton("Cancel",
            DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() })

        builderSingle.setAdapter(arrayAdapter, DialogInterface.OnClickListener { dialog, which ->
            val strName = arrayAdapter.getItem(which)
            val builderInner: AlertDialog.Builder = AlertDialog.Builder(this)
            loadChapter(which, true)
            /*  builderInner.setMessage(strName)
              builderInner.setTitle("Your Selected Item is")
              builderInner.setPositiveButton("Ok",
                  DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() })
              builderInner.show()*/
        })

        val dialog = builderSingle.create()
        dialog.setOnShowListener {
            //dialog.listView.smoothScrollToPositionFromTop(100,0,0)
            dialog.listView.post {
                dialog.listView.requestFocusFromTouch()
                dialog.listView.setSelection(currentChapter)
            }

            //dialog.listView.setSelection(-1)
        }

        dialog.show()
    }

    private fun getScrollRange(): Int {
        var scrollRange = 0
        if (read_scroll.childCount > 0) {
            val child: View = read_scroll.getChildAt(0)
            scrollRange = max(0,
                child.height - (read_scroll.height - read_scroll.paddingBottom - read_scroll.paddingTop))
        }
        return scrollRange
    }

    /*
    private fun setViewerFlags(flag: Int, mask: Int) {
        viewer_flags = viewer_flags and mask.inv() or (flag and mask)
    }*/

    //var viewer_flags: Int = 0
    var orientationType: Int = OrientationType.DEFAULT.prefValue
    /*
    var orientationType: Int
        get() = viewer_flags and OrientationType.MASK
        set(rotationType) = setViewerFlags(rotationType, OrientationType.MASK)*/

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.read_main)
        read_title_text.minHeight = read_toolbar.height

        //read_text = findViewById(R.id.read_text)
        //read_scroll = findViewById(R.id.read_scroll)
        //read_chapter_name = findViewById(R.id.read_chapter_name)
        //read_time = findViewById(R.id.read_time)
        //val read_topmargin = findViewById<View>(R.id.read_topmargin)

        // hideSystemUI()
        fixPaddingStatusbar(read_toolbar)

        //<editor-fold desc="Screen Rotation">
        fun setRot(org: OrientationType) {
            orientationType = org.prefValue
            requestedOrientation = org.flag
            read_action_rotate.setImageResource(org.iconRes)
        }

        read_action_rotate.setOnClickListener {
            read_action_rotate.popupMenu(
                items = OrientationType.values().map { it.prefValue to it.stringRes },
                selectedItemId = orientationType
                //   ?: preferences.defaultOrientationType(),
            ) {
                val org = OrientationType.fromSpinner(itemId)
                DataStore.setKey(EPUB_LOCK_ROTATION, itemId)
                setRot(org)
            }
        }

            //read_text.marg(read_text.left, read_toolbar_holder.height, read_text.right, read_text.bottom)


        setRot(OrientationType.fromSpinner(DataStore.getKey(EPUB_LOCK_ROTATION,
            OrientationType.DEFAULT.prefValue)))
        //</editor-fold>

        read_action_chapters.setOnClickListener {
            selectChapter()
        }

        read_action_tts.setOnClickListener {
            if (isTTSRunning) {
                stopTTS()
            } else {
                startTTS()
            }
        }

        /*
        read_text.setOnClickListener {
            if(isHidden) {
                showSystemUI()
            }
            else {
                hideSystemUI()
            }
        }*/

        hideSystemUI()

        read_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        read_toolbar.setNavigationOnClickListener {
            finish() // KILLS ACTIVITY
        }
        read_overflow_progress.max = OVERFLOW_NEXT_CHAPTER_DELTA

        readActivity = this
        tts = TextToSpeech(this, this)


        val parameter = read_topmargin.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        read_topmargin.layoutParams = parameter
        window.navigationBarColor =
            colorFromAttribute(R.attr.grayBackground) //getColor(R.color.readerHightlightedMetaInfo)

        val intent = intent
        path = intent.getStringExtra("path")!!

        val epubReader = EpubReader()
        book = epubReader.readEpub(FileInputStream(path))
        maxChapter = book.tableOfContents.tocReferences.size
        loadChapter(DataStore.getKey<Int>(EPUB_CURRENT_POSITION, book.title) ?: 0, true, true)
        updateTimeText()
        read_scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            checkTTSRange(scrollY)

            DataStore.setKey(EPUB_CURRENT_POSITION_SCROLL, book.title, scrollY)

            mainScrollY = scrollY
            updateChapterName(scrollY)
        }

        fun toggleShow() {
            if (isHidden) {
                showSystemUI()
            } else {
                hideSystemUI()
            }
        }

        read_scroll.setOnTouchListener { _, event ->
            val height = getScrollRange()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (mainScrollY >= height) {
                        overflowDown = true
                        startY = event.y
                    } else if (mainScrollY == 0) {
                        overflowDown = false
                        startY = event.y
                    }

                    scrollStartY = event.y
                    scrollStartX = event.x
                    scrollDistance = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = scrollStartX - event.x
                    val deltaY = scrollStartY - event.y
                    scrollDistance += abs(deltaX) + abs(deltaY)
                    scrollStartY = event.y
                    scrollStartX = event.x

                    fun deltaShow() {
                        if (scrollYOverflow * 100 / OVERFLOW_NEXT_CHAPTER_DELTA > OVERFLOW_NEXT_CHAPTER_SHOW_PROCENTAGE) {
                            /*read_overflow_progress.visibility = View.VISIBLE
                            read_overflow_progress.progress =
                                minOf(scrollYOverflow.toInt(), OVERFLOW_NEXT_CHAPTER_DELTA)*/

                            read_text.translationY = (if (overflowDown) -1f else 1f) * sqrt(minOf(scrollYOverflow,
                                OVERFLOW_NEXT_CHAPTER_DELTA.toFloat())) * 4 // *4 is the amount the page moves when you overload it

                        }
                    }

                    if (!overflowDown && (mainScrollY <= OVERFLOW_NEXT_CHAPTER_SAFESPACE.toDp || scrollYOverflow > OVERFLOW_NEXT_CHAPTER_SHOW_PROCENTAGE) && startY != null && currentChapter > 0) {
                        scrollYOverflow = maxOf(0f, event.y - startY!!)
                        deltaShow()
                    } else if (overflowDown && (mainScrollY >= height - OVERFLOW_NEXT_CHAPTER_SAFESPACE.toDp || scrollYOverflow > OVERFLOW_NEXT_CHAPTER_SHOW_PROCENTAGE) && startY != null) { // && currentChapter < maxChapter
                        scrollYOverflow = maxOf(0f, startY!! - event.y)
                        deltaShow()
                    } else {
                        read_overflow_progress.visibility = View.GONE
                        read_text.translationY = 0f
                    }
                }
                MotionEvent.ACTION_UP -> {
                    println("ACTION_UP")

                    if (scrollDistance < TOGGLE_DISTANCE) {
                        toggleShow()
                    }

                    read_overflow_progress.visibility = View.GONE
                    read_text.translationY = 0f
                    startY = null

                    if (100 * scrollYOverflow / OVERFLOW_NEXT_CHAPTER_DELTA >= OVERFLOW_NEXT_CHAPTER_NEXT) {
                        if (mainScrollY >= height && overflowDown) {
                            loadNextChapter()
                        } else if (mainScrollY == 0 && !overflowDown) {
                            loadPrevChapter()
                        }
                    }
                    scrollYOverflow = 0f
                }
            }
            return@setOnTouchListener false
        }

        chapterTitles = ArrayList()
        for ((index, chapter) in book.tableOfContents.tocReferences.withIndex()) {
            chapterTitles.add(chapter.title ?: "Chapter ${index + 1}")
        }
        read_overlay.setOnClickListener {
            selectChapter()
        }

        //val text = read_text.text
        //println("TEXT:" + book.contents[0].reader.readText())
/*
        read_scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (lockTTS && isTTSRunning) {
                if (read_scroll.height + scrollY <= minScroll) {
                    read_scroll.scrollTo(0, minScroll - read_scroll.height)
                    read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
                } else if (scrollY >= maxScroll) {
                    read_scroll.scrollTo(0, maxScroll)
                    read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
                }
            }
        }

        read_text.post {
            /*
           val startLines: ArrayList<Int> = ArrayList()
           for (i in 0..read_text.layout.lineCount) {
               startLines.add(read_text.layout.getLineStart(i))
           }
           return@post

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
           }*/

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
        }*/
    }
}