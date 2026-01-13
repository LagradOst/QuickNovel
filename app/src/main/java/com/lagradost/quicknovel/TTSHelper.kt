package com.lagradost.quicknovel

import android.content.Context
import android.content.IntentFilter
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.mvvm.debugAssert
import com.lagradost.quicknovel.receivers.BecomingNoisyReceiver
import com.lagradost.quicknovel.ui.UiText
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.UIHelper.requestAudioFocus
import io.noties.markwon.Markwon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.util.Locale
import java.util.Stack
import kotlin.math.roundToInt


class TTSSession(val context: Context, event: (TTSHelper.TTSActionType) -> Boolean) {
    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val myNoisyAudioStreamReceiver = BecomingNoisyReceiver()

    //private var mediaSession: MediaSessionCompat
    private var focusRequest: AudioFocusRequest? = null
    private val myAudioFocusListener =
        AudioManager.OnAudioFocusChangeListener {
            val pause =
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            if (pause) {
                event(TTSHelper.TTSActionType.Pause)
            }
        }
    private var isRegistered = false
    private var tts: TextToSpeech? = null
    private var TTSQueue: Pair<TTSHelper.TTSLine, Int>? = null

    private var TTSQueueId = 0
    private var TTSStartSpeakId = 0
    private var TTSEndSpeakId = 0

    private var speed: Float = 1.0f
    private var pitch: Float = 1.0f

    fun isValidTTS(): Boolean {
        return tts != null
    }

    private fun clearTTS(tts: TextToSpeech) {
        tts.stop()
        TTSQueue = null
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
        tts?.setSpeechRate(speed)
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
        tts?.setPitch(pitch)
    }

    fun setLanguage(locale: Locale?) {
        val realLocale = locale ?: Locale.US
        setKey(EPUB_LANG, realLocale.displayName)
        val tts = tts ?: return
        clearTTS(tts)
        tts.language = realLocale
    }

    fun setVoice(voice: Voice?) {
        if (voice == null) {
            removeKey(EPUB_VOICE)
        } else {
            setKey(EPUB_VOICE, voice.name)
        }
        val tts = tts ?: return
        clearTTS(tts)
        tts.voice = voice ?: tts.defaultVoice
    }

    fun interruptTTS() {
        // we don't actually want to initialize tts here
        tts?.let { tts ->
            clearTTS(tts)
        }
    }

    fun ttsInitialized(): Boolean {
        return tts != null
    }

    suspend fun speak(
        line: TTSHelper.TTSLine,
        next: TTSHelper.TTSLine?,
        action: () -> Boolean
    ): Int? {
        return requireTTS({ tts ->
            val ret: Int
            val queue = TTSQueue
            ret = if (queue?.first == line) {
                queue.second
            } else {
                TTSQueueId++
                tts.speak(line.speakOutMsg, TextToSpeech.QUEUE_FLUSH, null, TTSQueueId.toString())
                TTSQueueId
            }

            if (next != null) {
                TTSQueueId++
                TTSQueue = next to TTSQueueId
                tts.speak(next.speakOutMsg, TextToSpeech.QUEUE_ADD, null, TTSQueueId.toString())
            }
            ret
        }, action)
    }

    /** waits for sentence to be finished or action to be true, if action is true then
     * break early and interrupt TTS */
    suspend fun waitForOr(id: Int?, action: () -> Boolean, then: () -> Unit) {
        if (id == null) return
        while (id > TTSEndSpeakId) {
            delay(50)
            if (action()) {
                interruptTTS()
                then()
                break
            }
        }
    }

    private val mutex: Mutex = Mutex() // no duplicate tts
    suspend fun <T> requireTTS(callback: (TextToSpeech) -> T, action: () -> Boolean): T? =
        mutex.withLock {
            coroutineScope {
                return@coroutineScope tts?.let(callback) ?: run {
                    var waiting = true
                    var success = false
                    val pendingTTS = TextToSpeech(context) { status ->
                        success = status == TextToSpeech.SUCCESS
                        waiting = false
                        if (status == TextToSpeech.SUCCESS) {
                            return@TextToSpeech
                        }
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

                        CommonActivity.showToast("Initialization Failed! Error $errorMSG")
                        return@TextToSpeech
                    }

                    while (waiting && isActive) {
                        if (action()) {
                            return@coroutineScope null
                        }
                        delay(100)
                    }

                    if (!success) return@coroutineScope null

                    val voiceName = BaseApplication.getKey<String>(EPUB_VOICE)
                    val langName = BaseApplication.getKey<String>(EPUB_LANG)

                    val canSetLanguage = pendingTTS.setLanguage(
                        pendingTTS.availableLanguages.firstOrNull { it.displayName == langName }
                            ?: Locale.US)
                    pendingTTS.voice =
                        pendingTTS.voices.firstOrNull { it.name == voiceName }
                            ?: pendingTTS.defaultVoice

                    if (canSetLanguage == TextToSpeech.LANG_MISSING_DATA || canSetLanguage == TextToSpeech.LANG_NOT_SUPPORTED) {
                        CommonActivity.showToast("Unable to initialize TTS, download the language")
                        return@coroutineScope null
                    }

                    pendingTTS.setOnUtteranceProgressListener(object :
                        UtteranceProgressListener() {
                        //MIGHT BE INTERESTING https://stackoverflow.com/questions/44461533/android-o-new-texttospeech-onrangestart-callback
                        override fun onDone(utteranceId: String) {
                            utteranceId.toIntOrNull()?.let { id ->
                                TTSEndSpeakId = maxOf(TTSEndSpeakId, id)
                            }
                        }

                        @Deprecated("Deprecated")
                        override fun onError(utteranceId: String?) {
                            utteranceId?.toIntOrNull()?.let { id ->
                                TTSEndSpeakId = maxOf(TTSEndSpeakId, id)
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            utteranceId?.toIntOrNull()?.let { id ->
                                TTSEndSpeakId = maxOf(TTSEndSpeakId, id)
                            }
                        }

                        override fun onStart(utteranceId: String) {
                            utteranceId.toIntOrNull()?.let { id ->
                                TTSStartSpeakId = maxOf(TTSStartSpeakId, id)
                            }
                        }
                    })

                    pendingTTS.setPitch(pitch)
                    pendingTTS.setSpeechRate(speed)

                    tts = pendingTTS
                    tts?.let(callback)
                }
            }
        }


    fun register() {
        if (isRegistered) return
        isRegistered = true
        context.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.requestAudioFocus(focusRequest)
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null

        unregister()
    }

    fun unregister() {
        if (!isRegistered) return
        isRegistered = false
        context.unregisterReceiver(myNoisyAudioStreamReceiver)
    }

    init {
        // mediaSession = TTSHelper.initMediaSession(context, event)

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
}

fun generateId(type: Long, index: Int, start: Int, end: Int): Long {
    // 4b bits for type, max 16
    // 16b for chapter, max 65536
    // 22b for start, max 4 194 304
    // 22b for end, max 4 194 304

    val typeBits = type and ((1 shl 5) - 1)
    val indexBits = index.toLong() and ((1 shl 17) - 1)
    val startBits = start.toLong() and ((1 shl 23) - 1)
    val endBits = end.toLong() and ((1 shl 23) - 1)

    return typeBits or (indexBits shl 4) or (startBits shl 20) or (endBits shl 42)
}

data class TextSpan(
    val text: Spanned,
    val start: Int,
    val end: Int,
    override val index: Int,
    override var innerIndex: Int,
) : SpanDisplay() {
    val bionicText: Spanned by lazy {
        val wordToSpan: Spannable = SpannableString(text)
        val length = wordToSpan.length
        Regex("([a-zà-ýA-ZÀ-ÝåäöÅÄÖ].*?)[^a-zà-ýA-ZÀ-ÝåäöÅÄÖ'’]").findAll(text).forEach { match ->
            val range = match.groups[1]!!.range
            // https://github.com/gBloxy/Bionic-Reader/blob/main/bionic-reader.py#L167
            val correctLength = when (val rangeLength = range.last + 1 - range.first) {
                0 -> return@forEach // this should never happened
                1, 2, 3 -> 1
                4 -> 2
                else -> {
                    (rangeLength.toFloat() * 0.4).roundToInt()
                }
            }
            wordToSpan.setSpan(
                StyleSpan(Typeface.BOLD),
                minOf(maxOf(match.range.first, 0), length),
                minOf(maxOf(match.range.first + correctLength, 0), length),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        wordToSpan
    }

    override fun id(): Long {
        return generateId(0, index, start, end)
    }
}

abstract class SpanDisplay {
    val id by lazy { id() }

    abstract val index: Int
    abstract val innerIndex: Int

    protected abstract fun id(): Long
}

// uses the last text inner index
data class ChapterStartSpanned(
    override val index: Int,
    override val innerIndex: Int,
    val name: UiText,
    val canReload: Boolean,
) : SpanDisplay() {
    override fun id(): Long {
        return generateId(1, index, 0, 0)
    }
}

data class LoadingSpanned(val url: String?, override val index: Int) : SpanDisplay() {
    override val innerIndex: Int = 0
    override fun id(): Long {
        return generateId(2, index, 0, 0)
    }

    val text get() = url?.let { txt(R.string.loading_format, it) } ?: txt(R.string.loading)
}

data class FailedSpanned(
    val reason: UiText,
    override val index: Int,
    val cause: Throwable?
) :
    SpanDisplay() {
    override val innerIndex: Int = 0
    override fun id(): Long {
        return generateId(3, index, 0, 0)
    }
}

data class ChapterLoadSpanned(
    override val index: Int,
    override val innerIndex: Int,
    val loadIndex: Int,
    val name: UiText
) : SpanDisplay() {
    override fun id(): Long {
        return generateId(4, loadIndex, 0, 0)
    }
}

data class ChapterOverscrollSpanned(
    override val index: Int,
    override val innerIndex: Int,
    val loadIndex: Int,
    val name: UiText
) : SpanDisplay() {
    override fun id(): Long {
        return generateId(5, loadIndex, 0, 0)
    }
}

object TTSHelper {
    data class TTSLine(
        val speakOutMsg: String,
        val startChar: Int,
        val endChar: Int,
        val index: Int,
    )

    enum class TTSStatus {
        IsRunning,
        IsPaused,
        IsStopped,
    }

    enum class TTSActionType {
        Pause,
        Resume,
        Stop,
        Next,
    }


    /*fun initMediaSession(context: Context, event: (TTSActionType) -> Boolean): MediaSessionCompat {
        val mediaButtonReceiver = ComponentName(context, MediaButtonReceiver::class.java)
        return MediaSessionCompat(context, "TTS", mediaButtonReceiver, null).apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                        val keyEvent =
                            mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent?

                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) { // NO DOUBLE SKIP
                            return when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> event(TTSActionType.Pause)
                                KeyEvent.KEYCODE_MEDIA_PLAY -> event(TTSActionType.Resume)
                                KeyEvent.KEYCODE_MEDIA_STOP -> event(TTSActionType.Stop)
                                KeyEvent.KEYCODE_MEDIA_NEXT -> event(TTSActionType.Next)
                                else -> false
                            }
                        }

                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                }
            )
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
    }*/

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

    private fun parseSpan(
        unsegmented: Spanned,
        index: Int,
    ): ArrayList<TextSpan> {
        val spans: ArrayList<TextSpan> = ArrayList()

        var currentOffset = 0
        var innerIndex = 0
        var nextIndex = unsegmented.indexOf('\n')

        while (nextIndex != -1) {
            // don't include duplicate newlines
            if (currentOffset != nextIndex) {
                // Do not include blank text
                val text = unsegmented.subSequence(currentOffset, nextIndex) as Spanned
                if (!text.isBlank()) {
                    spans.add(
                        TextSpan(
                            text,
                            currentOffset,
                            nextIndex,
                            index,
                            innerIndex
                        )
                    )
                    innerIndex++
                }
            }

            currentOffset = nextIndex + 1
            nextIndex = unsegmented.indexOf('\n', currentOffset)
        }

        val text = unsegmented.subSequence(currentOffset, unsegmented.length) as Spanned
        if (currentOffset != unsegmented.length && !text.isBlank())
            spans.add(
                TextSpan(
                    text,
                    currentOffset,
                    unsegmented.length,
                    index,
                    innerIndex
                )
            )

        return spans
    }

    private fun getNewLineLocations(unsegmented: Spanned): Stack<Int> {
        val loc = Stack<Int>()
        val string = unsegmented.toString()


        var next = string.indexOf('\n')
        while (next > 0) {
            //avoid chains of newline characters
            next = if (string[next - 1] != '\n') {
                loc.push(next)
                string.indexOf('\n', loc.peek() + 1)
            } else {
                string.indexOf('\n', next + 1)
            }
            if (next >= string.length) next = -1
        }
        return loc
    }

    fun preParseHtml(text: String, authorNotes: Boolean): String {
        val document = Jsoup.parse(text)

        // REMOVE USELESS STUFF THAT WONT BE USED IN A NORMAL TXT
        document.select("style").remove()
        document.select("script").remove()

        if (!authorNotes) {
            document.select("div.qnauthornotecontainer").remove()
        }

        return document.html()
            // this makes tables readable, more or less places a newline between rows
            // and space between columns
            .replace("</td>", " </td>")
            .replace("</tr>", "<br/></tr>")

            // MAKES EASIER TO WORK WITH
            .replace("...", "…")
            // FUCK THIS, LEGIT IN EVERY CHAPTER
            .replace(
                "<p>.*<strong>Translator:.*?Editor:.*>".toRegex(),
                ""
            )
            .replace(
                "<.*?Translator:.*?Editor:.*?>".toRegex(),
                ""
            )
    }

    fun render(html: String, markwon: Markwon): Spanned {
        return markwon.render(
            markwon.parse(
                html
                /*.replaceAfterIndex( // because markwon is fucked we have to replace newlines with breaklines and becausse I dont want 3 br on top I start after body
                "\n",
                "<br>",
                startIndex = index + 7
            )*/
            )
        )
    }

    fun parseTextToSpans(
        render: Spanned,
        index: Int
    ): ArrayList<TextSpan> {
        return parseSpan(render, index)
    }

    private fun isValidSpeakOutMsg(msg: String): Boolean {
        return msg.isNotEmpty() && msg.isNotBlank() && msg.contains("[A-z0-9]".toRegex())
    }

    fun ttsParseText(text: String, tag: Int): ArrayList<TTSLine> {
        val cleanText = text
            .replace("\\.([A-z])".toRegex(), ",$1")//\.([A-z]) \.([^-\s])
            .replace("([.:])([0-9])".toRegex(), ",$2") // GOOD FOR DECIMALS
            .replace(
                "(^|[ \"“‘'])(Dr|Mr|Mrs)\\. ([A-Z])".toRegex(),
                "$1$2, $3"
            )

        //println("SIZE: ${text.length}")

        debugAssert({ cleanText.length != text.length }) {
            "TTS requires same length"
        }

        val ttsLines = ArrayList<TTSLine>()


        val invalidStartChars =
            arrayOf(
                ' ', '.', ',', '\n', '\"',
                '\'', '’', '‘', '“', '”', '«', '»', '「', '」', '…', '[', ']'
            )
        val endingCharacters = arrayOf(".", "\n", ";", "?", ":")
        var index = 0
        while (true) {
            if (index >= text.length) {
                break
            }
            while (invalidStartChars.contains(text[index])) {
                index++
                if (index >= text.length) {
                    break
                }
            }

            var endIndex = Int.MAX_VALUE
            for (a in endingCharacters) {
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
                break
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
                var msg = message
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
                        "~",
                        "\u200c" // Zero width joiner
                    ) // "\'", //Don't ect
                for (c in invalidChars) {
                    msg = msg.replace(c, " ")
                }
                msg = msg.replace("...", " ")
                if (msg
                        .replace("\n", "")
                        .replace("\t", "")
                        .replace(".", "").isNotEmpty()
                ) {
                    if (isValidSpeakOutMsg(msg)) {
                        ttsLines.add(TTSLine(msg, index, endIndex, index = tag))
                    }
                }
            } catch (t: Throwable) {
                break
            }
            index = endIndex
            if (text.getOrNull(index)?.isWhitespace() == true) {
                index++
            }
        }

        return ttsLines
    }
}