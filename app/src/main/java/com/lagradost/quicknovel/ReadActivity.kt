package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.Target
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.quicknovel.BookDownloader.getQuickChapter
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.providers.RedditProvider
import com.lagradost.quicknovel.receivers.BecomingNoisyReceiver
import com.lagradost.quicknovel.services.TTSPauseService
import com.lagradost.quicknovel.ui.OrientationType
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.UIHelper
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.requestAudioFocus
import com.lagradost.quicknovel.util.toDp
import com.lagradost.quicknovel.util.toPx
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.android.synthetic.main.read_main.*
import kotlinx.coroutines.*
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt


const val OVERFLOW_NEXT_CHAPTER_DELTA = 600
const val OVERFLOW_NEXT_CHAPTER_SHOW_PROCENTAGE = 10
const val OVERFLOW_NEXT_CHAPTER_NEXT = 90
const val OVERFLOW_NEXT_CHAPTER_SAFESPACE = 20
const val TOGGLE_DISTANCE = 20f

const val TTS_CHANNEL_ID = "QuickNovelTTS"
const val TTS_NOTIFICATION_ID = 133742

const val DEBUGGING = false

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
        wordToSpan.setSpan(
            android.text.Annotation("", "rounded"),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)

        return true
    } catch (e: Exception) {
        return false
    }
}

enum class TTSActionType {
    Pause,
    Resume,
    Stop,
}

class ReadActivity : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        var markwon: Markwon? = null
        lateinit var readActivity: ReadActivity
        lateinit var images: ArrayList<ImageView>

        var defFont: Typeface? = null
        fun getAllFonts(): Array<File>? {
            val path = "/system/fonts"
            val file = File(path)
            return file.listFiles()
        }
    }

    var isFromEpub = true
    lateinit var book: Book
    lateinit var quickdata: BookDownloader.QuickStreamData

    private fun getBookSize(): Int {
        return if (isFromEpub) book.tableOfContents.tocReferences.size else quickdata.data.size
    }

    private fun getBookTitle(): String {
        return if (isFromEpub) book.title else quickdata.meta.name
    }

    private suspend fun getBookBitmap(): Bitmap? {
        if (bookCover == null) {
            var byteArray: ByteArray? = null

            if (isFromEpub) {
                if (book.coverImage != null && book.coverImage.data != null)
                    byteArray = book.coverImage.data
            } else {
                val poster = quickdata.poster
                if (poster != null) {
                    try {
                        byteArray = app.get(poster).okhttpResponse.body.bytes()
                    } catch (e: Exception) {
                        println("BITMAP ERROR: $e")
                    }
                }
            }

            if (byteArray != null)
                bookCover = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
        return bookCover
    }

    private fun getChapterName(index: Int): String {
        return if (isFromEpub) book.tableOfContents.tocReferences?.get(index)?.title
            ?: "Chapter ${index + 1}" else quickdata.data[index].name
    }

    private suspend fun Context.getChapterData(index: Int, forceReload: Boolean = false): String? {
        println("getChapterData $index")
        val text =
            (if (isFromEpub) book.tableOfContents.tocReferences[index].resource.reader.readText() else {
                loading_text?.text = quickdata.data[index].url

                getQuickChapter(
                    quickdata.meta,
                    quickdata.data[index],
                    index,
                    forceReload
                )?.html ?: return null
            })
        val document = Jsoup.parse(text)

        // REMOVE USELESS STUFF THAT WONT BE USED IN A NORMAL TXT
        document.select("style").remove()
        document.select("script").remove()

        for (a in document.allElements) {
            if (a != null && a.hasText() &&
                (a.text() == chapterName || (a.tagName() == "h3" && a.text()
                    .startsWith("Chapter ${index + 1}")))
            ) { // IDK, SOME MIGHT PREFER THIS SETTING??
                a.remove() // THIS REMOVES THE TITLE
                break
            }
        }

        return document.html()
            .replace("<tr>", "<div style=\"text-align: center\">")
            .replace("</tr>", "</div>")
            .replace("<td>", "")
            .replace("</td>", " ")
            //.replace("\n\n", "\n") // REMOVES EMPTY SPACE
            .replace("...", "…") // MAKES EASIER TO WORK WITH
            .replace(
                "<p>.*<strong>Translator:.*?Editor:.*>".toRegex(),
                ""
            ) // FUCK THIS, LEGIT IN EVERY CHAPTER
            .replace(
                "<.*?Translator:.*?Editor:.*?>".toRegex(),
                ""
            )
    }


    private fun TextView.setFont(file: File?) {
        if (file == null) {
            this.typeface = defFont
        } else {
            this.typeface = Typeface.createFromFile(file)
        }
    }

    private fun setReadTextFont(file: File?, nameCallback: ((String) -> Unit)? = null) {
        if (defFont == null) defFont = read_text?.typeface
        setKey(EPUB_FONT, file?.name ?: "")
        read_text?.setFont(file)
        read_title_text?.setFont(file)
        read_title_text?.setTypeface(read_title_text?.typeface, Typeface.BOLD)
        nameCallback?.invoke(UIHelper.parseFontFileName(file?.name))
    }

    private fun showFonts(nameCallback: (String) -> Unit) {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.font_bottom_sheet)
        val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!

        val fonts = getAllFonts() ?: return
        val items = fonts.toMutableList() as ArrayList<File?>
        items.add(0, null)

        val currentName = getKey(EPUB_FONT) ?: ""
        val sotringIndex = items.indexOfFirst { it?.name ?: "" == currentName }

        /* val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
         arrayAdapter.addAll(sortingMethods.toMutableList())
         res.choiceMode = AbsListView.CHOICE_MODE_SINGLE
         res.adapter = arrayAdapter
         res.setItemChecked(sotringIndex, true)*/
        val adapter = FontAdapter(this, sotringIndex, items)

        res.adapter = adapter
        res.setOnItemClickListener { _, _, which, _ ->
            setReadTextFont(items[which], nameCallback)
            stopTTS()
            loadTextLines()
            globalTTSLines.clear()
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }


    fun callOnPause(): Boolean {
        if (!isTTSPaused) {
            isTTSPaused = true
            return true
        }
        return false
    }

    fun callOnPlay(): Boolean {
        if (isTTSPaused) {
            isTTSPaused = false
            return true
        }
        return false
    }

    fun callOnStop(): Boolean {
        if (isTTSRunning) {
            stopTTS()
            return true
        }
        return false
    }

    fun callOnNext(): Boolean {
        if (isTTSRunning) {
            nextTTSLine()
            return true
        } else if (isTTSPaused) {
            isTTSRunning = true
            nextTTSLine()
            return true
        }
        return false
    }

    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val myNoisyAudioStreamReceiver = BecomingNoisyReceiver()

    private var tts: TextToSpeech? = null

    private var bookCover: Bitmap? = null

    override fun onColorSelected(dialog: Int, color: Int) {
        when (dialog) {
            0 -> setBackgroundColor(color)
            1 -> setTextColor(color)
        }
    }

    override fun onDialogDismissed(dialog: Int) {
        updateImages()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val wasRunningTTS = isTTSRunning
        stopTTS()
        globalTTSLines.clear()

        if (isHidden) {
            hideSystemUI()
        } else {
            showSystemUI()
        }

        read_text.post {
            loadTextLines()
            if (wasRunningTTS) {
                startTTS(readFromIndex)
            }
        }
    }

    private fun getSpeakIdFromIndex(id: Int, startIndex: Int, endIndex: Int): String {
        return "$speakId|$startIndex:$endIndex"
    }

    private fun getSpeakIdFromLine(id: Int, line: TTSLine): String {
        return getSpeakIdFromIndex(id, line.startIndex, line.endIndex)
    }

    // USING Queue system because it is faster by about 0.2s
    private var currentTTSQueue: String? = null
    private fun speakOut(
        ttsLine: TTSLine,
        ttsLineQueue: TTSLine?,
    ) {
        canSpeak = false
        if (ttsLine.speakOutMsg.isEmpty() || ttsLine.speakOutMsg.isBlank()) {
            showMessage("No data")
            return
        }
        if (tts != null) {
            if (currentTTSQueue != ttsLine.speakOutMsg) {
                speakId++
                tts!!.speak(
                    ttsLine.speakOutMsg,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    getSpeakIdFromLine(speakId, ttsLine)
                )
            }
            if (ttsLineQueue != null) {
                tts!!.speak(
                    ttsLineQueue.speakOutMsg,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    getSpeakIdFromLine(speakId + 1, ttsLineQueue)
                )
                currentTTSQueue = ttsLineQueue.speakOutMsg
            }
        }
    }

    private fun getCurrentTTSLineScroll(): Int? {
        if (ttsStatus == TTSStatus.IsRunning || ttsStatus == TTSStatus.IsPaused) {
            try {
                if (readFromIndex >= 0 && readFromIndex < globalTTSLines.size) {
                    val line = globalTTSLines[readFromIndex]
                    val textLine = getMinMax(line.startIndex, line.endIndex)
                    if (textLine != null) {
                        return textLine.max + getLineOffset() - (read_toolbar_holder?.height
                            ?: 0) + (reader_lin_container?.paddingTop
                            ?: 0)//dimensionFromAttribute(R.attr.actionBarSize))
                    }
                }
            } catch (e: Exception) {
                //IDK SMTH HAPPEND
            }
        }
        return null
    }

    public override fun onDestroy() {
        val scroll = getCurrentTTSLineScroll()
        if (scroll != null) {
            setKey(
                EPUB_CURRENT_POSITION_SCROLL,
                getBookTitle(), scroll
            )
        }

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            cancel(TTS_NOTIFICATION_ID)
        }

        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        mMediaSessionCompat.release()

        super.onDestroy()
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    lateinit var path: String

    var canSpeak = true
    private var speakId = 0

    enum class TTSStatus {
        IsRunning,
        IsPaused,
        IsStopped,
    }

    private var _ttsStatus = TTSStatus.IsStopped

    private val myAudioFocusListener =
        AudioManager.OnAudioFocusChangeListener {
            val pause =
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            if (pause && isTTSRunning) {
                isTTSPaused = true
            }
        }
    var focusRequest: AudioFocusRequest? = null

    private fun initTTSSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(myAudioFocusListener)
                build()
            }
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Text To Speech"//getString(R.string.channel_name)
            val descriptionText =
                "The TTS notification channel" //getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(TTS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    var ttsStatus: TTSStatus
        get() = _ttsStatus
        set(value) {
            _ttsStatus = value
            if (value == TTSStatus.IsRunning) {
                //   mediaSession?.isActive = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requestAudioFocus(focusRequest)
                }
                try {
                    registerReceiver(myNoisyAudioStreamReceiver, intentFilter)
                } catch (e: Exception) {
                    println(e)
                }
            } else if (value == TTSStatus.IsStopped) {
                // mediaSession?.isActive = false
                try {
                    unregisterReceiver(myNoisyAudioStreamReceiver)
                } catch (e: Exception) {
                    println(e)
                }
            }

            if (value == TTSStatus.IsStopped) {
                with(NotificationManagerCompat.from(this)) {
                    // notificationId is a unique int for each notification that you must define
                    cancel(TTS_NOTIFICATION_ID)
                }
            } else {
                main {
                    val builder = NotificationCompat.Builder(this, TTS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_baseline_volume_up_24) //TODO NICE ICON
                        .setContentTitle(getBookTitle())
                        .setContentText(chapterName)

                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(false)
                        .setOngoing(true)

                    val icon = withContext(Dispatchers.IO) { getBookBitmap() }
                    if (icon != null) builder.setLargeIcon(icon)

                    builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle())
                    // .setMediaSession(mediaSession?.sessionToken))

                    val actionTypes: MutableList<TTSActionType> = ArrayList()

                    if (value == TTSStatus.IsPaused) {
                        actionTypes.add(TTSActionType.Resume)
                    } else if (value == TTSStatus.IsRunning) {
                        actionTypes.add(TTSActionType.Pause)
                    }
                    actionTypes.add(TTSActionType.Stop)

                    for ((index, i) in actionTypes.withIndex()) {
                        val resultIntent = Intent(this, TTSPauseService::class.java)
                        resultIntent.putExtra("id", i.ordinal)

                        val pending: PendingIntent = PendingIntent.getService(
                            this, 3337 + index,
                            resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )

                        builder.addAction(
                            NotificationCompat.Action(
                                when (i) {
                                    TTSActionType.Resume -> R.drawable.ic_baseline_play_arrow_24
                                    TTSActionType.Pause -> R.drawable.ic_baseline_pause_24
                                    TTSActionType.Stop -> R.drawable.ic_baseline_stop_24
                                }, when (i) {
                                    TTSActionType.Resume -> "Resume"
                                    TTSActionType.Pause -> "Pause"
                                    TTSActionType.Stop -> "Stop"
                                }, pending
                            )
                        )
                    }


                    with(NotificationManagerCompat.from(this)) {
                        // notificationId is a unique int for each notification that you must define
                        notify(TTS_NOTIFICATION_ID, builder.build())
                    }
                }
            }

            reader_bottom_view_tts?.visibility =
                if (isTTSRunning && !isHidden) View.VISIBLE else View.GONE
            reader_bottom_view?.visibility =
                if (!isTTSRunning && !isHidden) View.VISIBLE else View.GONE

            tts_action_pause_play?.setImageResource(
                when (value) {
                    TTSStatus.IsPaused -> R.drawable.ic_baseline_play_arrow_24
                    TTSStatus.IsRunning -> R.drawable.ic_baseline_pause_24
                    else -> { // IDK SHOULD BE AN INVALID STATE
                        R.drawable.ic_baseline_play_arrow_24
                    }
                }
            )
        }

    private var isTTSRunning: Boolean
        get() = ttsStatus != TTSStatus.IsStopped
        set(running) {
            ttsStatus = if (running) TTSStatus.IsRunning else TTSStatus.IsStopped
        }

    var isTTSPaused: Boolean
        get() = ttsStatus == TTSStatus.IsPaused
        set(paused) {
            ttsStatus = if (paused) TTSStatus.IsPaused else TTSStatus.IsRunning
            if (paused) {
                readFromIndex--
                interruptTTS()
            } else {
                playDummySound() // FUCK ANDROID
            }
        }

    private var lockTTS = true
    private val lockTTSOnPaused = false
    private var scrollWithVol = true
    var minScroll: Int? = 0
    var maxScroll: Int? = 0

    var isHidden = true

    private fun hideSystemUI() {
        isHidden = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, reader_container).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        fun lowerBottomNav(v: View) {
            v.translationY = 0f
            ObjectAnimator.ofFloat(v, "translationY", v.height.toFloat()).apply {
                duration = 200
                start()
            }.doOnEnd {
                v.visibility = View.GONE
            }
        }

        lowerBottomNav(reader_bottom_view)
        lowerBottomNav(reader_bottom_view_tts)

        read_toolbar_holder.translationY = 0f
        ObjectAnimator.ofFloat(
            read_toolbar_holder,
            "translationY",
            -read_toolbar_holder.height.toFloat()
        ).apply {
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(
            window,
            reader_container
        ).show(WindowInsetsCompat.Type.systemBars())

        read_toolbar_holder.visibility = View.VISIBLE

        reader_bottom_view.visibility = if (isTTSRunning) View.GONE else View.VISIBLE
        reader_bottom_view_tts.visibility = if (isTTSRunning) View.VISIBLE else View.GONE

        fun higherBottomNavView(v: View) {
            v.translationY = v.height.toFloat()
            ObjectAnimator.ofFloat(v, "translationY", 0f).apply {
                duration = 200
                start()
            }
        }

        higherBottomNavView(reader_bottom_view)
        higherBottomNavView(reader_bottom_view_tts)

        read_toolbar_holder.translationY = -read_toolbar_holder.height.toFloat()

        ObjectAnimator.ofFloat(read_toolbar_holder, "translationY", 0f).apply {
            duration = 200
            start()
        }
    }

    private fun Context.updateTimeText() {
        val string = if (this.updateTwelveHourTime()) "KK:mm a" else "HH:mm"

        val currentTime: String = SimpleDateFormat(string, Locale.getDefault()).format(Date())
        if (read_time != null) {
            read_time.text = currentTime
            read_time.postDelayed({ -> updateTimeText() }, 1000)
        }
    }


    private var hasTriedToFillNextChapter = false
    private val reddit = RedditProvider()
    private fun fillNextChapter(): Boolean {
        if (hasTriedToFillNextChapter || isFromEpub) {
            return false
        }
        hasTriedToFillNextChapter = true

        try {
            val elements =
                Jsoup.parse(currentHtmlText).allElements?.filterNotNull() ?: return false

            for (element in elements) {
                val href = element.attr("href") ?: continue

                val text =
                    element.ownText().replace(Regex("[\\[\\]().,|{}<>]"), "")?.trim() ?: continue
                if (text.equals("next", true) || text.equals(
                        "next chapter",
                        true
                    ) || text.equals("next part", true)
                ) {
                    val name = reddit.getName(href) ?: "Next"
                    quickdata.data.add(ChapterData(name, href, null, null))
                    chapterTitles.add(getChapterName(maxChapter))
                    maxChapter += 1
                    return true
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return false
    }

    private fun loadNextChapter(): Boolean {
        return if (currentChapter >= maxChapter - 1) {
            if (fillNextChapter()) {
                loadNextChapter()
            } else {
                Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            ioSafe {
                loadChapter(currentChapter + 1, true)
                read_scroll.smoothScrollTo(0, 0)
            }

            true
        }
    }

    private fun loadPrevChapter(): Boolean {
        return if (currentChapter <= 0) {
            false
            //Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
        } else {
            ioSafe {
                loadChapter(currentChapter - 1, false)
            }
            true
        }
    }

    private fun View.fixLine(offset: Int) {
        // this.setPadding(0, 200, 0, 0)
        val layoutParams =
            this.layoutParams as FrameLayout.LayoutParams// FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,offset)
        layoutParams.setMargins(0, offset, 0, 0)
        this.layoutParams = layoutParams
    }

    private fun getLineOffset(): Int {
        return (read_title_text?.height ?: 0) + (read_text?.paddingTop ?: 0)
    }

    var lastChange: TextLine? = null

    private fun createTempBottomPadding(size: Int) {
        val parms = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size)
        parms.gravity = Gravity.BOTTOM
        read_temp_bottom_margin?.visibility = View.VISIBLE
        read_temp_bottom_margin?.layoutParams = parms
    }

    private fun changeLine(line: TextLine) {//, moveToTextBottom: Boolean) {
        val offset = getLineOffset()

        read_scroll?.let {
            it.scrollTo(0, line.topPosition + offset)
            for (tLine in textLines!!) {
                if (tLine.bottomPosition + offset > mainScrollY + it.height) {
                    val size =
                        (mainScrollY + it.height) - (tLine.topPosition + offset) + (read_overlay?.height
                            ?: 0)
                    createTempBottomPadding(size)
                    if (DEBUGGING) {
                        read_temp_bottom_margin?.setBackgroundResource(R.color.colorPrimary)
                        line_top_extra.fixLine(tLine.topPosition + offset)
                    }
                    break
                }
            }
        }

        if (DEBUGGING) {
            line_top.visibility = View.VISIBLE
            line_bottom.visibility = View.VISIBLE
            line_top_extra.visibility = View.VISIBLE

            line_top.fixLine(line.topPosition + offset)
            line_bottom.fixLine(line.bottomPosition + offset)

            if (lastChange != null) {
                setHighLightedText(read_text, lastChange!!.startIndex, lastChange!!.endIndex)
            } else {
                setHighLightedText(read_text, line.startIndex, line.endIndex)
            }
            lastChange = line
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        kill()
    }

    private fun kill() {
        with(NotificationManagerCompat.from(this)) { // KILLS NOTIFICATION
            cancel(TTS_NOTIFICATION_ID)
        }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            kill()
            return true
        }
        if (scrollWithVol && isHidden && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            val offset = getLineOffset()
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (isTTSRunning) {
                        nextTTSLine()
                        return true
                    }

                    if (read_scroll.scrollY >= getScrollRange()) {
                        loadNextChapter()
                        return true
                    }
                    for (t in textLines!!) {
                        if (t.bottomPosition + offset > mainScrollY + read_scroll.height) {
                            val str = try {
                                read_text?.text?.substring(t.startIndex, t.endIndex) ?: "valid"
                            } catch (e: Exception) {
                                "valid"
                            }
                            if (str.isBlank()) { // skips black areas
                                continue
                            }
                            changeLine(t)
                            read_scroll.fling(0)
                            return true
                        }
                    }
                    loadNextChapter()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (isTTSRunning) {
                        prevTTSLine()
                        return true
                    }

                    if (read_scroll.scrollY <= 0) {
                        loadPrevChapter()
                        return true
                    }
                    for ((index, textLine) in textLines!!.withIndex()) {
                        if (textLine.topPosition + offset >= mainScrollY) { // finds current top
                            if (index == 0) {
                                loadPrevChapter()
                                return true
                            }
                            for (returnIndex in index downTo 0) {
                                val returnLine = textLines!![returnIndex]
                                if (textLine.bottomPosition - returnLine.topPosition > read_scroll.height) {
                                    changeLine(returnLine)
                                    read_scroll.fling(0)
                                    return true
                                }
                            }
                        }
                    }
                    loadPrevChapter()
                    return true
                }
            }
        }
        return false
    }

    data class TextLine(
        val startIndex: Int,
        val endIndex: Int,
        val topPosition: Int,
        val bottomPosition: Int,
        val lineIndex: Int,
    )

    private lateinit var chapterTitles: ArrayList<String>
    private var maxChapter: Int = 0

    private var currentChapter = 0
    private var textLines: ArrayList<TextLine>? = null
    private var mainScrollY = 0
    private var scrollYOverflow = 0f

    private var startY: Float? = null
    private var scrollStartY: Float = 0f
    private var scrollStartX: Float = 0f
    private var scrollDistance: Float = 0f

    private var overflowDown: Boolean = true
    private var chapterName: String? = null

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

    fun String.replaceAfterIndex(
        oldValue: String,
        newValue: String,
        ignoreCase: Boolean = false,
        startIndex: Int = 0
    ): String {
        run {
            var occurrenceIndex: Int = indexOf(oldValue, startIndex, ignoreCase)
            // FAST PATH: no match
            if (occurrenceIndex < 0) return this

            val oldValueLength = oldValue.length
            val searchStep = oldValueLength.coerceAtLeast(1)
            val newLengthHint = length - oldValueLength + newValue.length
            if (newLengthHint < 0) throw OutOfMemoryError()
            val stringBuilder = StringBuilder(newLengthHint)

            var i = 0
            do {
                stringBuilder.append(this, i, occurrenceIndex).append(newValue)
                i = occurrenceIndex + oldValueLength
                if (occurrenceIndex >= length) break
                occurrenceIndex = indexOf(oldValue, occurrenceIndex + searchStep, ignoreCase)
            } while (occurrenceIndex > 0)
            return stringBuilder.append(this, i, length).toString()
        }
    }

    private var currentText = ""
    private var currentHtmlText = ""
    private suspend fun Context.loadChapter(
        chapterIndex: Int,
        scrollToTop: Boolean,
        scrollToRemember: Boolean = false,
        forceReload: Boolean = false
    ) {
        if (chapterIndex > maxChapter - 1) {
            if (isFromEpub) {
                loadChapter(maxChapter - 1, scrollToTop, scrollToRemember, forceReload)
                return
            } else {
                for (i in maxChapter - 1 until chapterIndex) {
                    hasTriedToFillNextChapter = false
                    currentHtmlText = getChapterData(i, false) ?: break
                    if (!fillNextChapter()) {
                        break
                    }
                }
                loadChapter(maxChapter - 1, scrollToTop, scrollToRemember, forceReload)
                return
            }
        }

        main {
            setKey(EPUB_CURRENT_POSITION, getBookTitle(), chapterIndex)
            val txt = if (isFromEpub) {
                getChapterData(chapterIndex, forceReload)
            } else {
                read_loading?.visibility = View.VISIBLE
                read_normal_layout?.alpha = 0f
                withContext(Dispatchers.IO) {
                    getChapterData(chapterIndex, forceReload)
                }
            }

            if (!isFromEpub)
                fadeInText()

            if (txt == null) {
                Toast.makeText(this, "Error loading chapter", Toast.LENGTH_SHORT).show()
                if (!isFromEpub && !forceReload) {
                    loadChapter(chapterIndex, scrollToTop, scrollToRemember, true)
                }
                return@main // TODO FIX REAL INTERACT BUTTON
            }

            fun scroll() {
                if (scrollToRemember) {
                    val scrollToY = getKey<Int>(EPUB_CURRENT_POSITION_SCROLL, getBookTitle(), null)
                    if (scrollToY != null) {
                        read_scroll.scrollTo(0, scrollToY)
                        read_scroll.fling(0)
                        return
                    }
                }

                val scrollToY = if (scrollToTop) 0 else getScrollRange()
                read_scroll.scrollTo(0, scrollToY)
                read_scroll.fling(0)
                updateChapterName(scrollToY)
            }
            read_text?.alpha = 0f

            chapterName = getChapterName(chapterIndex)

            currentChapter = chapterIndex
            hasTriedToFillNextChapter = false
            read_toolbar?.title = getBookTitle()
            read_toolbar?.subtitle = chapterName
            read_title_text?.text = chapterName

            updateChapterName(0)
            markwon =
                markwon ?: Markwon.builder(readActivity) // automatically create Glide instance
                    //.usePlugin(GlideImagesPlugin.create(context)) // use supplied Glide instance
                    //.usePlugin(GlideImagesPlugin.create(Glide.with(context))) // if you need more control
                    .usePlugin(HtmlPlugin.create { plugin -> plugin.excludeDefaults(false) })
                    .usePlugin(GlideImagesPlugin.create(object : GlideImagesPlugin.GlideStore {
                        @NonNull
                        override fun load(@NonNull drawable: AsyncDrawable): RequestBuilder<Drawable> {
                            return try {
                                val newUrl = drawable.destination.substringAfter("&url=")
                                val url =
                                    if (newUrl.length > 8) { // we assume that it is not a stub url by length > 8
                                        URLDecoder.decode(newUrl)
                                    } else {
                                        drawable.destination
                                    }
                                Glide.with(readActivity).load(url)
                            } catch (e: Exception) {
                                logError(e)
                                Glide.with(readActivity).load(R.drawable.books_emoji) // might crash :)
                            }
                        }

                        override fun cancel(target: Target<*>) {
                            try {
                                Glide.with(readActivity).clear(target)
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }
                    }))
                    .usePlugin(object :
                        AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.imageSizeResolver(object : ImageSizeResolver() {
                                override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                                    //val imageSize = drawable.imageSize
                                    return drawable.result.bounds
                                }
                            })
                        }
                    })
                    .usePlugin(SoftBreakAddsNewLinePlugin.create())
                    .build()

            val index = txt.indexOf("<body>")
            markwon?.setMarkdown(
                read_text,
                txt.replaceAfterIndex( // because markwon is fucked we have to replace newlines with breaklines and becausse I dont want 3 br on top I start after body
                    "\n",
                    "<br>",
                    startIndex = index + 7
                )//.replaceFirst(Regex("""[\\s*<br>\\s*\\n*]*"""), "")
            ) ?: run {
                val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(
                        txt,
                        Html.FROM_HTML_MODE_LEGACY,
                        null,
                        null
                    ) as Spannable
                } else {
                    Html.fromHtml(txt, null, null) as Spannable
                }
                read_text?.text = spanned
            }


            //println("TEXT:" + document.html())
            //read_text?.text = spanned
            currentText = read_text?.text.toString()
            currentHtmlText = txt

            read_text?.post {
                loadTextLines()
                scroll()
                read_text?.alpha = 1f

                globalTTSLines.clear()
                interruptTTS()
                if (isTTSRunning || isTTSPaused) { // or Paused because it will fuck up otherwise
                    startTTS(true)
                }
            }
        }
    }

    private fun loadTextLines() {
        textLines = ArrayList()
        val lay = read_text.layout ?: return
        for (i in 0..lay.lineCount) {
            try {
                textLines?.add(
                    TextLine(
                        lay.getLineStart(i),
                        lay.getLineEnd(i),
                        lay.getLineTop(i),
                        lay.getLineBottom(i),
                        i,
                    )
                )
            } catch (e: Exception) {
                println("EX: $e")
            }
        }
    }

    private fun interruptTTS() {
        currentTTSQueue = null
        if (tts != null) {
            tts!!.stop()
        }
        canSpeak = true
    }

    private fun nextTTSLine() {
        //readFromIndex++
        interruptTTS()
    }

    private fun prevTTSLine() {
        readFromIndex -= 2
        interruptTTS()
    }

    fun stopTTS() {
        runOnUiThread {
            val scroll = getCurrentTTSLineScroll()
            if (scroll != null) {
                read_scroll?.scrollTo(0, scroll)
            }
            clearTextViewOfSpans(read_text)
            interruptTTS()
            ttsStatus = TTSStatus.IsStopped
        }
    }

    data class TTSLine(
        val speakOutMsg: String,
        val startIndex: Int,
        val endIndex: Int,
    )

    var globalTTSLines = ArrayList<TTSLine>()

    private fun prepareTTS(text: String, callback: (Boolean) -> Unit) {
        val job = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + job)
        uiScope.launch {
            // CLEAN TEXT IS JUST TO MAKE SURE THAT THE TTS SPEAKER DOES NOT SPEAK WRONG, MUST BE SAME LENGTH
            val cleanText = text
                .replace("\\.([A-z])".toRegex(), ",$1")//\.([A-z]) \.([^-\s])
                .replace("([0-9])([.:])([0-9])".toRegex(), "$1,$3") // GOOD FOR DECIMALS
                .replace(
                    "([ \"“‘'])(Dr|Mr|Mrs)\\. ([A-Z])".toRegex(),
                    "$1$2, $3"
                ) // Doctor or Mister

            val ttsLines = ArrayList<TTSLine>()

            var index = 0
            while (true) {
                if (index >= text.length) {
                    globalTTSLines = ttsLines
                    callback.invoke(true)
                    return@launch
                }

                val invalidStartChars =
                    arrayOf(
                        ' ', '.', ',', '\n', '\"',
                        '\'', '’', '‘', '“', '”', '«', '»', '「', '」', '…'
                    )
                while (invalidStartChars.contains(text[index])) {
                    index++
                    if (index >= text.length) {
                        globalTTSLines = ttsLines
                        callback.invoke(true)
                        return@launch
                    }
                }

                var endIndex = Int.MAX_VALUE
                for (a in arrayOf(".", "\n", ";", "?", ":")) {
                    val indexEnd = cleanText.indexOf(a, index)

                    if (indexEnd == -1) continue

                    if (indexEnd < endIndex) {
                        endIndex = indexEnd + 1
                    }
                }


                if (endIndex > text.length) {
                    endIndex = text.length
                }
                if (index >= text.length) {
                    globalTTSLines = ttsLines
                    callback.invoke(true)
                    return@launch
                }

                val invalidEndChars =
                    arrayOf('\n')
                while (true) {
                    var containsInvalidEndChar = false
                    for (a in invalidEndChars) {
                        if (endIndex <= 0 || endIndex > text.length) break
                        if (text[endIndex - 1] == a) {
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
                    val message = text.substring(index, endIndex)
                    var msg = message//Regex("\\p{L}").replace(message,"")
                    val invalidChars =
                        arrayOf(
                            "-",
                            "<",
                            ">",
                            "_",
                            "^",
                            "«",
                            "»",
                            "「",
                            "」",
                            "—",
                            "–",
                            "¿",
                            "*",
                            "~"
                        ) // "\'", //Don't ect
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
                        if (isValidSpeakOutMsg(msg)) {
                            ttsLines.add(TTSLine(msg, index, endIndex))
                        }
                        if (textLines == null)
                            return@launch
                    }
                } catch (e: Exception) {
                    println(e)
                    return@launch
                }
                index = endIndex + 1
            }
        }
    }

    data class ScrollLine(val min: Int, val max: Int)

    private fun getMinMax(startIndex: Int, endIndex: Int): ScrollLine? {
        if (textLines == null || textLines?.size == 0) {
            loadTextLines()
        }
        val text = textLines ?: return null

        var max: Int? = null
        var min: Int? = null
        for (t in text) {
            if (t.endIndex > startIndex && max == null) {
                max = t.topPosition
            }
            if (t.endIndex > endIndex && min == null) {
                min = t.bottomPosition
            }
            if (max != null && min != null) return ScrollLine(min, max)
        }
        return null
    }

    private fun isValidSpeakOutMsg(msg: String): Boolean {
        return msg.isNotEmpty() && msg.isNotBlank() && msg.contains("[A-z0-9]".toRegex())
    }

    private var readFromIndex = 0
    private var currentTTSRangeStartIndex = 0
    private var currentTTSRangeEndIndex = 0
    private fun runTTS(index: Int? = null) {
        isTTSRunning = true

        playDummySound() // FUCK ANDROID

        val job = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + job)
        uiScope.launch {
            while (tts == null) {
                if (!isTTSRunning) return@launch
                delay(50)
            }
            if (index != null) {
                readFromIndex = index
            } else {
                val offset = getLineOffset()
                val topPadding = reader_lin_container?.paddingTop ?: 0
                val height = read_toolbar_holder?.height ?: 0

                for ((startIndex, line) in globalTTSLines.withIndex()) {
                    if (read_scroll.scrollY <= (getMinMax(line.startIndex, line.endIndex)?.max
                            ?: 0) + offset - height + topPadding
                    ) {
                        readFromIndex = startIndex
                        break
                    }
                }
            }

            while (true) {
                try {
                    if (!isTTSRunning) return@launch
                    while (isTTSPaused) {
                        delay(50)
                    }
                    if (!isTTSRunning) return@launch
                    if (globalTTSLines.size == 0) return@launch
                    if (readFromIndex < 0) {
                        if (!loadPrevChapter()) {
                            stopTTS()
                        }
                        return@launch
                    } else if (readFromIndex >= globalTTSLines.size) {
                        if (!loadNextChapter()) {
                            stopTTS()
                        }
                        return@launch
                    }

                    val line = globalTTSLines[readFromIndex]
                    val nextLine =
                        if (readFromIndex + 1 >= globalTTSLines.size) null else globalTTSLines[readFromIndex + 1]

                    currentTTSRangeStartIndex = line.startIndex
                    currentTTSRangeEndIndex = line.endIndex
                    if (read_scroll != null) {
                        val textLine = getMinMax(line.startIndex, line.endIndex)
                        minScroll = textLine?.min
                        maxScroll = textLine?.max
                        checkTTSRange(read_scroll.scrollY, true)
                    }

                    if (isValidSpeakOutMsg(line.speakOutMsg)) {
                        read_text?.let {
                            setHighLightedText(it, line.startIndex, line.endIndex)
                        }

                        speakOut(line, nextLine)
                    }

                    while (!canSpeak) {
                        delay(10)
                        if (!isTTSRunning) return@launch
                    }

                    readFromIndex++
                    if (speakId == latestTTSSpeakOutId) {
                        readFromIndex++
                    }
                } catch (e: Exception) {
                    println(e)
                    return@launch
                }
            }
        }
    }

    private fun startTTS(fromStart: Boolean = false) {
        startTTS(if (fromStart) 0 else null)
    }

    private fun startTTS(fromIndex: Int?) {
        if (globalTTSLines.size <= 0) {
            prepareTTS(currentText) {
                if (it) {
                    runTTS(fromIndex)
                } else {
                    Toast.makeText(this, "Error parsing text", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runTTS(fromIndex)
        }
    }

    private fun checkTTSRange(scrollY: Int, scrollToTop: Boolean = false) {
        try {
            if (!lockTTSOnPaused && isTTSPaused) return
            val min = minScroll
            val max = maxScroll
            if (min == null || max == null) return
            val offset = getLineOffset()

            if (lockTTS && isTTSRunning) {
                if (read_scroll.height + scrollY - offset - 0 <= min) { // FOR WHEN THE TEXT IS ON THE BOTTOM OF THE SCREEN
                    if (scrollToTop) {
                        read_scroll.scrollTo(0, max + offset)
                    } else {
                        read_scroll.scrollTo(0, min - read_scroll.height + offset + 0)
                    }
                    read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
                } else if (scrollY - offset >= max) { // WHEN TEXT IS ON TOP
                    read_scroll.scrollTo(0, max + offset)
                    read_scroll.fling(0) // FIX WACK INCONSISTENCY, RESETS VELOCITY
                }
            }
        } catch (e: Exception) {
            println("WHAT THE FUCK HAPPENED HERE? : $e")
        }
    }

    override fun onResume() {
        super.onResume()

        main {
            if (read_scroll == null) return@main
            if (!lockTTSOnPaused && isTTSPaused) return@main
            if (!lockTTS || !isTTSRunning) return@main

            val textLine = getMinMax(currentTTSRangeStartIndex, currentTTSRangeEndIndex)
            minScroll = textLine?.min
            maxScroll = textLine?.max
            val scroll = read_scroll?.scrollY
            if (scroll != null) { // JUST TO BE 100% SURE THAT ANDROID DOES NOT FUCK YOU OVER
                checkTTSRange(scroll, true)
            }
        }
        hideSystemUI()
    }

    private fun selectChapter() {
        val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
        //builderSingle.setIcon(R.drawable.ic_launcher)
        builderSingle.setTitle(chapterTitles[currentChapter]) //  "Select Chapter"

        val arrayAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)

        arrayAdapter.addAll(chapterTitles)

        builderSingle.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builderSingle.setAdapter(arrayAdapter) { _, which ->
            ioSafe {
                loadChapter(which, true)
            }
        }

        val dialog = builderSingle.create()
        dialog.show()

        dialog.listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        dialog.listView.setSelection(currentChapter)
        dialog.listView.setItemChecked(currentChapter, true)
    }

    private fun getScrollRange(): Int {
        var scrollRange = 0
        if (read_scroll.childCount > 0) {
            val child: View = read_scroll.getChildAt(0)
            scrollRange = max(
                0,
                child.height - (read_scroll.height - read_scroll.paddingBottom - read_scroll.paddingTop)
            )
        }
        return scrollRange
    }

    private var orientationType: Int = OrientationType.DEFAULT.prefValue

    private lateinit var mMediaSessionCompat: MediaSessionCompat
    private val mMediaSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                val keyEvent =
                    mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent?
                if (keyEvent != null) {
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) { // NO DOUBLE SKIP
                        val consumed = when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> callOnPause()
                            KeyEvent.KEYCODE_MEDIA_PLAY -> callOnPlay()
                            KeyEvent.KEYCODE_MEDIA_STOP -> callOnStop()
                            KeyEvent.KEYCODE_MEDIA_NEXT -> callOnNext()
                            else -> false
                        }
                        if (consumed) return true
                    }
                }

                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }

    // FUCK ANDROID WITH ALL MY HEART
    // SEE https://stackoverflow.com/questions/45960265/android-o-oreo-8-and-higher-media-buttons-issue WHY
    private fun playDummySound() {
        val mMediaPlayer: MediaPlayer = MediaPlayer.create(this, R.raw.dummy_sound_500ms)
        mMediaPlayer.setOnCompletionListener { mMediaPlayer.release() }
        mMediaPlayer.start()
    }

    private fun Context.initMediaSession() {
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
        mMediaSessionCompat = MediaSessionCompat(this, "TTS", mediaButtonReceiver, null)
        mMediaSessionCompat.setCallback(mMediaSessionCallback)
        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
    }

    fun Context.setTextFontSize(size: Int) {
        setKey(EPUB_TEXT_SIZE, size)
        read_text?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        read_title_text?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat() + 2f)
    }

    private fun Context.getTextFontSize(): Int {
        return getKey(EPUB_TEXT_SIZE, 14)!!
    }

    private fun Context.getScrollWithVol(): Boolean {
        scrollWithVol = getKey(EPUB_SCROLL_VOL, true)!!
        return scrollWithVol
    }

    private fun Context.setScrollWithVol(scroll: Boolean) {
        scrollWithVol = scroll
        setKey(EPUB_SCROLL_VOL, scroll)
    }


    private fun Context.getLockTTS(): Boolean {
        lockTTS = getKey(EPUB_SCROLL_VOL, true)!!
        return lockTTS
    }

    private fun Context.setLockTTS(scroll: Boolean) {
        lockTTS = scroll
        setKey(EPUB_SCROLL_VOL, scroll)
    }

    private fun Context.setBackgroundColor(color: Int) {
        reader_container?.setBackgroundColor(color)
        read_temp_bottom_margin?.setBackgroundColor(color)
        setKey(EPUB_BG_COLOR, color)
    }

    private fun Context.setTextColor(color: Int) {
        read_text?.setTextColor(color)
        read_battery?.setTextColor(color)
        read_time?.setTextColor(color)
        read_title_text?.setTextColor(color)
        setKey(EPUB_TEXT_COLOR, color)
    }

    private fun Context.updateHasBattery(status: Boolean? = null): Boolean {
        val set = if (status != null) {
            setKey(EPUB_HAS_BATTERY, status)
            status
        } else {
            getKey(EPUB_HAS_BATTERY, true)!!
        }
        read_battery?.visibility = if (set) View.VISIBLE else View.GONE
        return set
    }

    private fun Context.updateKeepScreen(status: Boolean? = null): Boolean {
        val set = if (status != null) {
            setKey(EPUB_KEEP_SCREEN_ACTIVE, status)
            status
        } else {
            getKey(EPUB_KEEP_SCREEN_ACTIVE, true)!!
        }
        if (set) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        return set
    }

    private fun Context.updateTwelveHourTime(status: Boolean? = null): Boolean {
        return if (status != null) {
            this.setKey(EPUB_TWELVE_HOUR_TIME, status)
            status
        } else {
            this.getKey(EPUB_TWELVE_HOUR_TIME, false)!!
        }
    }


    private fun Context.updateHasTime(status: Boolean? = null): Boolean {
        val set = if (status != null) {
            setKey(EPUB_HAS_TIME, status)
            status
        } else {
            getKey(EPUB_HAS_TIME, true)!!
        }
        read_time?.visibility = if (set) View.VISIBLE else View.GONE
        return set
    }

    private fun Context.getTextColor(): Int {
        val color = getKey(EPUB_TEXT_COLOR, ContextCompat.getColor(this, R.color.readerTextColor))!!
        read_text?.setTextColor(color)
        read_battery?.setTextColor(color)
        read_time?.setTextColor(color)
        read_title_text?.setTextColor(color)
        return color
    }

    /** In DP **/
    private fun Context.getTextPadding(): Int {
        return getKey(EPUB_TEXT_PADDING, 20)!!
    }

    /** In DP **/
    private fun Context.getTextPaddingTop(): Int {
        return getKey(EPUB_TEXT_PADDING_TOP, 0)!!
    }

    /** In DP **/
    private fun Context.setTextPaddingTop(padding: Int) {
        setKey(EPUB_TEXT_PADDING_TOP, padding)
        reader_lin_container?.setPadding(
            reader_lin_container?.paddingLeft ?: 0,
            padding.toPx,
            reader_lin_container?.paddingRight ?: 0,
            0,//padding.toPx,
        )
    }

    /** In DP **/
    private fun Context.setTextPadding(padding: Int) {
        setKey(EPUB_TEXT_PADDING, padding)
        read_text?.setPadding(
            padding.toPx,
            read_text?.paddingTop ?: 25.toPx,
            padding.toPx,
            read_text?.paddingBottom ?: 25.toPx
        )
    }

    private fun Context.getBackgroundColor(): Int {
        val color = getKey(EPUB_BG_COLOR, ContextCompat.getColor(this, R.color.readerBackground))!!
        setBackgroundColor(color)
        return color
    }

    private fun updateImages() {
        val bgColors = resources.getIntArray(R.array.readerBgColors)
        val textColors = resources.getIntArray(R.array.readerTextColors)
        val color = getBackgroundColor()
        val colorPrimary = colorFromAttribute(R.attr.colorPrimary)
        val colorPrim = ColorStateList.valueOf(colorPrimary)
        val colorTrans = ColorStateList.valueOf(Color.TRANSPARENT)
        var foundCurrentColor = false
        val fullAlpha = 200
        val fadedAlpha = 50

        for ((index, img) in images.withIndex()) {
            if (index == bgColors.size) { // CUSTOM COLOR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorPrim
                    img.foreground = ContextCompat.getDrawable(
                        this,
                        if (foundCurrentColor) R.drawable.ic_baseline_add_24 else R.drawable.ic_baseline_check_24
                    )
                }
                img.imageAlpha = if (foundCurrentColor) fadedAlpha else fullAlpha
                img.backgroundTintList =
                    ColorStateList.valueOf(if (foundCurrentColor) Color.parseColor("#161616") else color)
                continue
            }

            if ((color == bgColors[index] && getTextColor() == textColors[index])) {
                foundCurrentColor = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorPrim
                }
                img.imageAlpha = fullAlpha
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorTrans
                }
                img.imageAlpha = fadedAlpha
            }
        }
    }

    private fun fadeInText() {
        if (read_loading != null) { // IDK, android might be weird and kill before load, not tested tho
            read_loading?.visibility = View.GONE
            read_normal_layout?.alpha = 0.01f

            ObjectAnimator.ofFloat(read_normal_layout, "alpha", 1f).apply {
                duration = 300
                start()
            }
        }
    }

    var latestTTSSpeakOutId = Int.MIN_VALUE

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTheme = when (settingsManager.getString("theme", "Dark")) {
            "Black" -> R.style.AppTheme
            "Dark" -> R.style.DarkAlternative
            "Light" -> R.style.LightMode
            else -> R.style.AppTheme
        }

        val currentOverlayTheme = when (settingsManager.getString("color_theme", "Blue")) {
            "Normal" -> R.style.OverlayPrimaryColorNormal
            "Blue" -> R.style.OverlayPrimaryColorBlue
            "Purple" -> R.style.OverlayPrimaryColorPurple
            "Green" -> R.style.OverlayPrimaryColorGreen
            "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
            "Red" -> R.style.OverlayPrimaryColorRed
            else -> R.style.OverlayPrimaryColorNormal
        }
        //val isLightTheme = themeName == "Light"

        theme.applyStyle(
            currentTheme,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW
        theme.applyStyle(currentOverlayTheme, true)

        super.onCreate(savedInstanceState)

        val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(ctxt: Context?, intent: Intent) {
                val batteryPct: Float = intent.let { intent ->
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level * 100 / scale.toFloat()
                }
                read_battery?.text = "${batteryPct.toInt()}%"
            }
        }
        IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(mBatInfoReceiver, ifilter)
        }

        val data = intent.data

        if (data == null) {
            kill()
            return
        }

        // THIS WAY YOU CAN OPEN FROM FILE OR FROM APP
        val input = contentResolver.openInputStream(data)
        if (input == null) {
            kill()
            return
        }

        isFromEpub = intent.type == "application/epub+zip"

        initMediaSession()
        setContentView(R.layout.read_main)
        setTextFontSize(getTextFontSize())
        setTextPadding(getTextPadding())
        setTextPaddingTop(getTextPaddingTop())
        initTTSSession()
        getLockTTS()
        getScrollWithVol()
        getBackgroundColor()
        getTextColor()
        updateHasTime()
        updateTwelveHourTime()
        updateHasBattery()
        updateKeepScreen()

        val fonts = getAllFonts()
        if (fonts == null) {
            setReadTextFont(null)
        } else {
            val index = fonts.map { it.name }.indexOf(getKey(EPUB_FONT) ?: "")
            setReadTextFont(if (index > 0) fonts[index] else null, null)
        }


        createNotificationChannel()
        read_title_text.minHeight = read_toolbar.height

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
                setKey(EPUB_LOCK_ROTATION, itemId)
                setRot(org)
            }
        }
        val colorPrimary =
            colorFromAttribute(R.attr.colorPrimary)//   getColor(R.color.colorPrimary)

        read_action_settings.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(this)

            bottomSheetDialog.setContentView(R.layout.read_bottom_settings)
            val readSettingsTextSize =
                bottomSheetDialog.findViewById<SeekBar>(R.id.read_settings_text_size)!!
            val readSettingsTextPadding =
                bottomSheetDialog.findViewById<SeekBar>(R.id.read_settings_text_padding)!!
            val readSettingsTextPaddingTop =
                bottomSheetDialog.findViewById<SeekBar>(R.id.read_settings_text_padding_top)!!

            val readSettingsScrollVol =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_scroll_vol)!!
            val readSettingsLockTts =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_lock_tts)!!
            val showTime =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_show_time)!!
            val twelvehourFormat =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_twelve_hour_time)!!
            val showBattery =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_show_battery)!!
            val keepScreenActive =
                bottomSheetDialog.findViewById<MaterialCheckBox>(R.id.read_settings_keep_screen_active)!!
            val readSettingsTextPaddingText =
                bottomSheetDialog.findViewById<TextView>(R.id.read_settings_text_padding_text)!!
            val readSettingsTextPaddingTextTop =
                bottomSheetDialog.findViewById<TextView>(R.id.read_settings_text_padding_text_top)!!
            val readSettingsTextSizeText =
                bottomSheetDialog.findViewById<TextView>(R.id.read_settings_text_size_text)!!
            val readSettingsTextFontText =
                bottomSheetDialog.findViewById<TextView>(R.id.read_settings_text_font_text)!!
            val hardResetStream =
                bottomSheetDialog.findViewById<MaterialButton>(R.id.hard_reset_stream)!!

            hardResetStream.visibility = if (isFromEpub) View.GONE else View.VISIBLE
            hardResetStream.setOnClickListener {
                ioSafe {
                    loadChapter(
                        currentChapter,
                        scrollToTop = false,
                        scrollToRemember = true,
                        forceReload = true
                    )
                }
            }

            //val root = bottomSheetDialog.findViewById<LinearLayout>(R.id.read_settings_root)!!
            val horizontalColors =
                bottomSheetDialog.findViewById<LinearLayout>(R.id.read_settings_colors)!!

            val readShowFonts = bottomSheetDialog.findViewById<MaterialButton>(R.id.read_show_fonts)
            readShowFonts?.text = UIHelper.parseFontFileName(getKey(EPUB_FONT))

            readShowFonts?.setOnClickListener {
                showFonts {
                    readShowFonts.text = it
                }
            }

            readSettingsScrollVol.isChecked = scrollWithVol
            readSettingsScrollVol.setOnCheckedChangeListener { _, checked ->
                setScrollWithVol(checked)
            }

            readSettingsLockTts.isChecked = lockTTS
            readSettingsLockTts.setOnCheckedChangeListener { _, checked ->
                setLockTTS(checked)
            }


            twelvehourFormat.isChecked = updateTwelveHourTime()
            twelvehourFormat.setOnCheckedChangeListener { _, checked ->
                updateTwelveHourTime(checked)
            }

            showTime.isChecked = updateHasTime()
            showTime.setOnCheckedChangeListener { _, checked ->
                updateHasTime(checked)
            }

            showBattery.isChecked = updateHasBattery()
            showBattery.setOnCheckedChangeListener { _, checked ->
                updateHasBattery(checked)
            }

            keepScreenActive.isChecked = updateKeepScreen()
            keepScreenActive.setOnCheckedChangeListener { _, checked ->
                updateKeepScreen(checked)
            }

            val bgColors = resources.getIntArray(R.array.readerBgColors)
            val textColors = resources.getIntArray(R.array.readerTextColors)

            images = ArrayList()

            for ((index, backgroundColor) in bgColors.withIndex()) {
                val textColor = textColors[index]

                val imageHolder = layoutInflater.inflate(
                    R.layout.color_round_checkmark,
                    null
                ) //color_round_checkmark
                val image = imageHolder.findViewById<ImageView>(R.id.image1)
                image.backgroundTintList = ColorStateList.valueOf(backgroundColor)
                image.setOnClickListener {
                    setBackgroundColor(backgroundColor)
                    setTextColor(textColor)
                    updateImages()
                }
                images.add(image)
                horizontalColors.addView(imageHolder)
                //  image.backgroundTintList = ColorStateList.valueOf(c)// ContextCompat.getColorStateList(this, c)
            }

            val imageHolder = layoutInflater.inflate(R.layout.color_round_checkmark, null)
            val image = imageHolder.findViewById<ImageView>(R.id.image1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                image.foreground = ContextCompat.getDrawable(this, R.drawable.ic_baseline_add_24)
            }
            image.setOnClickListener {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.reading_color))

                val colorAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)
                val array = arrayListOf(
                    getString(R.string.background_color),
                    getString(R.string.text_color)
                )
                colorAdapter.addAll(array)

                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    updateImages()
                }

                builder.setAdapter(colorAdapter) { _, which ->
                    ColorPickerDialog.newBuilder()
                        .setDialogId(which)
                        .setColor(
                            when (which) {
                                0 -> getBackgroundColor()
                                1 -> getTextColor()
                                else -> 0
                            }
                        )
                        .show(this)
                }

                builder.show()
                updateImages()
            }

            images.add(image)
            horizontalColors.addView(imageHolder)
            updateImages()

            readSettingsTextSize.max = 20
            val offsetSize = 10
            var updateAllTextOnDismiss = false
            readSettingsTextSize.progress = getTextFontSize() - offsetSize
            readSettingsTextSize.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    setTextFontSize(progress + offsetSize)
                    stopTTS()

                    updateAllTextOnDismiss = true
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            readSettingsTextPadding.max = 50
            readSettingsTextPadding.progress = getTextPadding()
            readSettingsTextPadding.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    setTextPadding(progress)
                    stopTTS()

                    updateAllTextOnDismiss = true
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            readSettingsTextPaddingTop.max = 50
            readSettingsTextPaddingTop.progress = getTextPaddingTop()
            readSettingsTextPaddingTop.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    setTextPaddingTop(progress)
                    stopTTS()

                    updateAllTextOnDismiss = true
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            readSettingsTextPaddingTextTop.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        it.context?.removeKey(EPUB_TEXT_PADDING_TOP)
                        readSettingsTextPaddingTop.progress = getTextPaddingTop()
                    }
                }
            }

            readSettingsTextPaddingText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        it.context?.removeKey(EPUB_TEXT_PADDING)
                        readSettingsTextPadding.progress = getTextPadding()
                    }
                }
            }

            readSettingsTextSizeText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        it.context?.removeKey(EPUB_TEXT_SIZE)
                        readSettingsTextSize.progress = getTextFontSize() - offsetSize
                    }
                }
            }

            readSettingsTextFontText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        setReadTextFont(null) { fileName ->
                            readShowFonts?.text = fileName
                        }
                        stopTTS()
                        updateAllTextOnDismiss = true
                    }
                }
            }

            bottomSheetDialog.setOnDismissListener {
                if (updateAllTextOnDismiss) {
                    loadTextLines()
                    globalTTSLines.clear()
                }
            }
            bottomSheetDialog.show()
        }

        setRot(
            OrientationType.fromSpinner(
                getKey(
                    EPUB_LOCK_ROTATION,
                    OrientationType.DEFAULT.prefValue
                )
            )
        )
        //</editor-fold>

        read_action_chapters.setOnClickListener {
            selectChapter()
        }

        tts_action_stop.setOnClickListener {
            stopTTS()
        }
        tts_action_pause_play.setOnClickListener {
            when (ttsStatus) {
                TTSStatus.IsRunning -> isTTSPaused = true
                TTSStatus.IsPaused -> isTTSPaused = false
                else -> {
                    // DO NOTHING
                }
            }
        }

        tts_action_forward.setOnClickListener {
            nextTTSLine()
        }

        tts_action_back.setOnClickListener {
            prevTTSLine()
        }

        read_action_tts.setOnClickListener {
            /* fun readTTSClick() {
                 when (ttsStatus) {
                     TTSStatus.IsStopped -> startTTS()
                     TTSStatus.IsRunning -> stopTTS()
                     TTSStatus.IsPaused -> isTTSPaused = false
                 }
             }*/

            // DON'T INIT TTS UNTIL IT IS NECESSARY
            if (tts == null) {
                tts = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts!!.setLanguage(Locale.US)

                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            showMessage("This Language is not supported")
                        } else {
                            tts!!.setOnUtteranceProgressListener(object :
                                UtteranceProgressListener() {
                                //MIGHT BE INTERESTING https://stackoverflow.com/questions/44461533/android-o-new-texttospeech-onrangestart-callback
                                override fun onDone(utteranceId: String) {
                                    canSpeak = true
                                    //  println("ENDMS: " + System.currentTimeMillis())
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    canSpeak = true
                                }

                                override fun onError(utteranceId: String) {
                                    canSpeak = true
                                }

                                override fun onStart(utteranceId: String) {
                                    val highlightResult =
                                        Regex("([0-9]*)|([0-9]*):([0-9]*)").matchEntire(utteranceId)
                                    if (highlightResult == null || (highlightResult.groupValues.size < 4)) return
                                    try {
                                        latestTTSSpeakOutId =
                                            highlightResult.groupValues[1].toIntOrNull() ?: return
                                        val startIndex =
                                            highlightResult.groupValues[2].toIntOrNull() ?: return
                                        val endIndex =
                                            highlightResult.groupValues[3].toIntOrNull() ?: return
                                        runOnUiThread {
                                            read_text?.let {
                                                setHighLightedText(it, startIndex, endIndex)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            })
                            startTTS()
                            //readTTSClick()
                        }
                    } else {
                        val errorMSG = when (status) {
                            TextToSpeech.ERROR -> "ERROR"
                            TextToSpeech.ERROR_INVALID_REQUEST -> "ERROR_INVALID_REQUEST"
                            TextToSpeech.ERROR_NETWORK -> "ERROR_NETWORK"
                            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "ERROR_NOT_INSTALLED_YET"
                            TextToSpeech.ERROR_OUTPUT -> "ERROR_OUTPUT"
                            TextToSpeech.ERROR_SYNTHESIS -> "ERROR_SYNTHESIS"
                            TextToSpeech.ERROR_SERVICE -> "ERROR_SERVICE"
                            else -> status.toString()
                        }

                        showMessage("Initialization Failed! Error $errorMSG")
                        tts = null
                    }
                }
            } else {
                startTTS()
                // readTTSClick()
            }
        }

        hideSystemUI()

        read_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        read_toolbar.setNavigationOnClickListener {
            kill() // KILLS ACTIVITY
        }
        read_overflow_progress.max = OVERFLOW_NEXT_CHAPTER_DELTA

        readActivity = this

        fixPaddingStatusbar(read_topmargin)

        window.navigationBarColor =
            colorFromAttribute(R.attr.grayBackground) //getColor(R.color.readerHightlightedMetaInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            read_scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                checkTTSRange(scrollY)
                read_temp_bottom_margin?.visibility = View.GONE

                setKey(EPUB_CURRENT_POSITION_SCROLL, getBookTitle(), scrollY)

                mainScrollY = scrollY
                updateChapterName(scrollY)
            }
        }

        fun toggleShow() {
            if (isHidden) {
                showSystemUI()
            } else {
                hideSystemUI()
            }
        }

        val touchListener = View.OnTouchListener { view, event ->
            val height = getScrollRange()
            if (view != null && view == reader_lin_container && event.action == MotionEvent.ACTION_DOWN) {
                toggleShow()
                return@OnTouchListener true
            }
            if (event == null) return@OnTouchListener true
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

                            read_text.translationY = (if (overflowDown) -1f else 1f) * sqrt(
                                minOf(
                                    scrollYOverflow,
                                    OVERFLOW_NEXT_CHAPTER_DELTA.toFloat()
                                )
                            ) * 4 // *4 is the amount the page moves when you overload it

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
            false
        }

        read_scroll?.setOnTouchListener(touchListener)
        reader_lin_container?.setOnTouchListener(touchListener)
        read_normal_layout?.setOnTouchListener(touchListener)
        read_text?.setOnTouchListener(touchListener)

//        read_overlay.setOnClickListener {
//            selectChapter()
//        }

        main { // THIS IS USED FOR INSTANT LOAD
            read_loading.postDelayed({
                if (!this::chapterTitles.isInitialized) {
                    read_loading?.visibility = View.VISIBLE
                }
            }, 200) // I DON'T WANT TO SHOW THIS IN THE BEGINNING, IN CASE IF SMALL LOAD TIME

            withContext(Dispatchers.IO) {
                if (isFromEpub) {
                    val epubReader = EpubReader()
                    book = epubReader.readEpub(input)
                } else {
                    quickdata = mapper.readValue(input.reader().readText())
                }
            }

            if (!isFromEpub && quickdata.data.isEmpty()) {
                Toast.makeText(this, R.string.no_chapters_found, Toast.LENGTH_SHORT).show()
                kill()
                return@main
            }

            maxChapter = getBookSize()

            chapterTitles = ArrayList()
            for (i in 0 until maxChapter) {
                chapterTitles.add(getChapterName(i))
            }
            loadChapter(
                //minOf(
                getKey(EPUB_CURRENT_POSITION, getBookTitle()) ?: 0,
                //    maxChapter - 1
                // ), // CRASH FIX IF YOU SOMEHOW TRY TO LOAD ANOTHER EPUB WITH THE SAME NAME
                scrollToTop = true,
                scrollToRemember = true
            )
            updateTimeText()

            fadeInText()
        }
    }
}