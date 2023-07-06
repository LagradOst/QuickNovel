package com.lagradost.quicknovel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spanned
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.target.Target
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BookDownloader2Helper.getQuickChapter
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.TTSHelper.parseTextToSpans
import com.lagradost.quicknovel.TTSHelper.preParseHtml
import com.lagradost.quicknovel.TTSHelper.ttsParseText
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.map
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.providers.RedditProvider
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.ArrayList
import java.util.Locale

abstract class AbstractBook {
    open fun resolveUrl(url: String): String {
        return url
    }

    abstract fun size(): Int
    abstract fun title(): String
    abstract fun getChapterTitle(index: Int): String
    abstract fun getLoadingStatus(index: Int): String?

    @WorkerThread
    @Throws
    abstract suspend fun getChapterData(index: Int, reload: Boolean): String

    abstract fun expand(last: String): Boolean

    @WorkerThread
    @Throws
    protected abstract suspend fun posterBytes(): ByteArray?

    private var poster: Bitmap? = null

    init {
        ioSafe {
            poster = posterBytes()?.let { byteArray ->
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }
        }
    }

    fun poster(): Bitmap? {
        return poster
    }
}

class QuickBook(val data: QuickStreamData) : AbstractBook() {
    override fun resolveUrl(url: String): String {
        return Apis.getApiFromNameNull(data.meta.apiName)?.fixUrl(url) ?: url
    }

    override fun size(): Int {
        return data.data.size
    }

    override fun title(): String {
        return data.meta.name
    }

    override fun getChapterTitle(index: Int): String {
        return data.data[index].name
    }

    override fun getLoadingStatus(index: Int): String {
        return data.data[index].url
    }

    override suspend fun getChapterData(index: Int, reload: Boolean): String {
        val ctx = context ?: throw ErrorLoadingException("Invalid context")
        return ctx.getQuickChapter(
            data.meta,
            data.data[index],
            index,
            reload
        )?.html ?: throw ErrorLoadingException("Error loading chapter")
    }

    override fun expand(last: String): Boolean {
        try {
            val elements =
                Jsoup.parse(last).allElements.filterNotNull()

            for (element in elements) {
                val href = element.attr("href") ?: continue

                val text =
                    element.ownText().replace(Regex("[\\[\\]().,|{}<>]"), "").trim()
                if (text.equals("next", true) || text.equals(
                        "next chapter",
                        true
                    ) || text.equals("next part", true)
                ) {
                    val name = RedditProvider.getName(href) ?: "Next"
                    data.data.add(ChapterData(name, href, null, null))
                    return true
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return false
    }

    override suspend fun posterBytes(): ByteArray? {
        val poster = data.poster
        if (poster != null) {
            try {
                return MainActivity.app.get(poster).okhttpResponse.body.bytes()
            } catch (t: Throwable) {
                logError(t)
            }
        }
        return null
    }
}

class RegularBook(val data: Book) : AbstractBook() {
    override fun size(): Int {
        return data.tableOfContents.tocReferences.size
    }

    override fun title(): String {
        return data.title
    }

    override fun getChapterTitle(index: Int): String {
        return data.tableOfContents.tocReferences?.get(index)?.title ?: "Chapter ${index + 1}"
    }

    override fun getLoadingStatus(index: Int): String? {
        return null
    }

    override suspend fun getChapterData(index: Int, reload: Boolean): String {
        return data.tableOfContents.tocReferences[index].resource.reader.readText()
    }

    override fun expand(last: String): Boolean {
        return false
    }

    override suspend fun posterBytes(): ByteArray? {
        return data.coverImage?.data
    }
}


data class LiveChapterData(
    val spans: List<Spanned>,
    val title: String,
    val rawText: String,
    val ttsLines: List<TTSHelper.TTSLine>
)

class ReadActivityViewModel : ViewModel() {
    private lateinit var book: AbstractBook
    private lateinit var markwon: Markwon

    private var _context: WeakReference<ReadActivity2>? = null
    var context
        get() = _context?.get()
        private set(value) {
            _context = WeakReference(value)
        }

    /*
        private val _chapter: MutableLiveData<Resource<LiveChapterData>> =
            MutableLiveData<Resource<LiveChapterData>>(null)
        val chapter: LiveData<Resource<LiveChapterData>> = _chapter*/

    private val _loadingStatus: MutableLiveData<Resource<Nothing>> =
        MutableLiveData<Resource<Nothing>>(null)
    val chapter: LiveData<Resource<Nothing>> = _loadingStatus

    private val _chaptersTitles: MutableLiveData<List<String>> =
        MutableLiveData<List<String>>(null)
    val chaptersTitles: LiveData<List<String>> = _chaptersTitles

    private val _title: MutableLiveData<String> =
        MutableLiveData<String>(null)
    val title: LiveData<String> = _title

    var chapters: ArrayList<String> = arrayListOf()

    private fun updateChapters() {
        for (idx in chapters.size until book.size()) {
            chapters.add(book.getChapterTitle(idx))
        }
        _chaptersTitles.postValue(chapters)
    }

    private val chapterMutex = Mutex()
    private val loading: HashSet<Int> = hashSetOf()
    private val chapterData: HashMap<Int, Resource<LiveChapterData>> = hashMapOf()
    private val hasExpanded: HashSet<Int> = hashSetOf()

    private var currentIndex = Int.MIN_VALUE

    fun updateIndex(index: Int) {
        if (currentIndex == index) return
        currentIndex = index
        viewModelScope.launchSafe {
            for (i in listOf(index,index+1,index-1)) {
                if (index <= 0) continue

                val exists = chapterMutex.withLock {
                    chapterData.contains(i) || loading.contains(i)
                }

                if (!exists) {
                    ioSafe { loadIndividualChapter(i, false) }
                }
            }
        }
    }

    private fun notifyChapterUpdate() {

    }

    @WorkerThread
    private suspend fun loadIndividualChapter(index: Int, reload: Boolean = false) {
        require(index >= 0)
        chapterMutex.withLock {
            if (loading.contains(index)) return
            loading += index
            if (!reload) {
                if (chapterData.contains(index)) {
                    return
                }
            }
        }

        _loadingStatus.postValue(Resource.Loading(null))

        if (index >= book.size() && !hasExpanded.contains(index)) {
            hasExpanded += index
            try {
                // we assume that the text is cached
                book.expand(book.getChapterData(book.size() - 1, reload = false))
            } catch (t: Throwable) {
                logError(t)
            }
            updateChapters()
        }

        if (index >= book.size()) {
            chapterMutex.withLock {
                chapterData[index] = Resource.Failure(false, null, null, "No more chapters")
                loading -= index
                notifyChapterUpdate()
            }
            return
        }
        _loadingStatus.postValue(Resource.Loading(book.getLoadingStatus(index)))

        val data = safeApiCall {
            book.getChapterData(index, reload)
        }.map { text ->
            val rawText = preParseHtml(text)
            LiveChapterData(
                rawText = rawText,
                spans = parseTextToSpans(rawText, markwon),
                title = book.getChapterTitle(index),
                ttsLines = ttsParseText(rawText)
            )
        }
        chapterMutex.withLock {
            chapterData[index] = data
            loading -= index
            notifyChapterUpdate()
        }
    }

    /*private var hasTriedExpand: Boolean = false
    fun loadChapter(index: Int, reload: Boolean = false) = ioSafe {
        if (index >= book.size()) {
            try {
                // we assume that the text is cached
                book.expand(book.getChapterData(book.size() - 1, reload = false))
            } catch (t: Throwable) {
                logError(t)
            }

            hasTriedExpand = true
        }

        if (index >= book.size()) {
            showToast("No more chapters", Toast.LENGTH_SHORT)
            return@ioSafe
        }

        updateChapters()

        _chapter.postValue(Resource.Loading(book.getLoadingStatus(index)))

        val result = safeApiCall {
            book.getChapterData(index, reload)
        }

        result.map { text ->
            val rawText = preParseHtml(text)
            LiveChapterData(
                rawText = rawText,
                spans = parseTextToSpans(rawText, markwon),
                title = book.getChapterTitle(index),
                ttsLines = ttsParseText(rawText)
            )
        }.let { newChapter ->
            _chapter.postValue(newChapter)
        }
        hasTriedExpand = false
    }*/


    fun init(intent: Intent?, context: ReadActivity2) = ioSafe {
        _loadingStatus.postValue(Resource.Loading())
        this@ReadActivityViewModel.context = context
        initTTSSession(context)

        val loadedBook = safeApiCall {
            if (intent == null) throw ErrorLoadingException("No intent")

            val data = intent.data ?: throw ErrorLoadingException("Empty intent")
            val input = context.contentResolver.openInputStream(data)
                ?: throw ErrorLoadingException("Empty data")
            val isFromEpub = intent.type == "application/epub+zip"

            val epub = if (isFromEpub) {
                val epubReader = EpubReader()
                val book = epubReader.readEpub(input)
                RegularBook(book)
            } else {
                QuickBook(DataStore.mapper.readValue<QuickStreamData>(input.reader().readText()))
            }

            if (epub.size() == 0) {
                throw ErrorLoadingException("Empty book, failed to parse ${intent.type}")
            }
            epub
        }
        when (loadedBook) {
            is Resource.Success -> {
                init(loadedBook.value, context)
            }

            is Resource.Failure -> {
                _loadingStatus.postValue(
                    Resource.Failure(
                        loadedBook.isNetworkError,
                        loadedBook.errorCode,
                        loadedBook.errorResponse,
                        loadedBook.errorString
                    )
                )
            }

            else -> throw NotImplementedError()
        }
    }

    fun init(book: AbstractBook, context: Context) {
        this.book = book
        _title.postValue(book.title())

        updateChapters()

        Markwon.builder(context) // automatically create Glide instance
            //.usePlugin(GlideImagesPlugin.create(context)) // use supplied Glide instance
            //.usePlugin(GlideImagesPlugin.create(Glide.with(context))) // if you need more control
            .usePlugin(HtmlPlugin.create { plugin -> plugin.excludeDefaults(false) })
            .usePlugin(GlideImagesPlugin.create(object : GlideImagesPlugin.GlideStore {
                override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> {
                    return try {
                        val newUrl = drawable.destination.substringAfter("&url=")
                        val url =
                            book.resolveUrl(
                                if (newUrl.length > 8) { // we assume that it is not a stub url by length > 8
                                    URLDecoder.decode(newUrl)
                                } else {
                                    drawable.destination
                                }
                            )

                        Glide.with(context)
                            .load(GlideUrl(url) { mapOf("user-agent" to USER_AGENT) })
                    } catch (e: Exception) {
                        logError(e)
                        Glide.with(context)
                            .load(R.drawable.books_emoji) // might crash :)
                    }
                }

                override fun cancel(target: Target<*>) {
                    try {
                        Glide.with(context).clear(target)
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
                            return drawable.result.bounds
                        }
                    })
                }
            })
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .build()
    }

    // ========================================  TTS STUFF ========================================

    lateinit var ttsSession: TTSSession
    private var tts: TextToSpeech? = null

    private fun requireTTS(callback: (TextToSpeech) -> Unit) {
        tts?.let(callback) ?: run {
            val pendingTTS = TextToSpeech(context ?: return) { status ->
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

                showToast("Initialization Failed! Error $errorMSG")
                return@TextToSpeech
            }

            val voiceName = getKey<String>(EPUB_VOICE)
            val langName = getKey<String>(EPUB_LANG)

            val canSetLanguage = pendingTTS.setLanguage(
                pendingTTS.availableLanguages.firstOrNull { it.displayName == langName }
                    ?: Locale.US)
            pendingTTS.voice =
                pendingTTS.voices.firstOrNull { it.name == voiceName } ?: pendingTTS.defaultVoice

            if (canSetLanguage == TextToSpeech.LANG_MISSING_DATA || canSetLanguage == TextToSpeech.LANG_NOT_SUPPORTED) {
                showToast("Unable to initialize TTS, download the language")
                return
            }

            pendingTTS.setOnUtteranceProgressListener(object :
                UtteranceProgressListener() {
                //MIGHT BE INTERESTING https://stackoverflow.com/questions/44461533/android-o-new-texttospeech-onrangestart-callback
                override fun onDone(utteranceId: String) {

                }

                @Deprecated("Deprecated")
                override fun onError(utteranceId: String?) {
                }

                override fun onError(utteranceId: String?, errorCode: Int) {

                }

                override fun onStart(utteranceId: String) {

                }
            })
        }
    }

    private fun initTTSSession(context: Context) {
        ttsSession = TTSSession(context, ::parseAction)
    }

    fun startTTS() {
        ttsSession.register(context)
    }

    fun stopTTS() {
        ttsSession.unregister(context)
    }

    fun parseAction(input: TTSHelper.TTSActionType): Boolean {
        return true
    }

    override fun onCleared() {
        ttsSession.unregister(context)
        super.onCleared()
    }
}