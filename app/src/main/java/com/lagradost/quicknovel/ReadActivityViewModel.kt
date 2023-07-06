package com.lagradost.quicknovel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.target.Target
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2Helper.getQuickChapter
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.TTSHelper.parseTextToSpans
import com.lagradost.quicknovel.TTSHelper.preParseHtml
import com.lagradost.quicknovel.TTSHelper.ttsParseText
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.letInner
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.map
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.providers.RedditProvider
import com.lagradost.quicknovel.ui.ScrollIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityIndex
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.runOnMainThread
import com.lagradost.quicknovel.util.amap
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
    val spans: List<TextSpan>,
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


    private val _chapterData: MutableLiveData<ArrayList<SpanDisplay>> =
        MutableLiveData<ArrayList<SpanDisplay>>(null)
    val chapter: LiveData<ArrayList<SpanDisplay>> = _chapterData

    // we use bool as we cant construct Nothing, does not represent anything
    private val _loadingStatus: MutableLiveData<Resource<Boolean>> =
        MutableLiveData<Resource<Boolean>>(null)
    val loadingStatus: LiveData<Resource<Boolean>> = _loadingStatus

    private val _chaptersTitles: MutableLiveData<List<String>> =
        MutableLiveData<List<String>>(null)
    val chaptersTitles: LiveData<List<String>> = _chaptersTitles

    private val _title: MutableLiveData<String> =
        MutableLiveData<String>(null)
    val title: LiveData<String> = _title

    private val _chapterTile: MutableLiveData<String> =
        MutableLiveData<String>(null)
    val chapterTile: LiveData<String> = _chapterTile

    private val _bottomVisibility: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>(false)
    val bottomVisibility: LiveData<Boolean> = _bottomVisibility

    private val _ttsStatus: MutableLiveData<TTSHelper.TTSStatus> =
        MutableLiveData<TTSHelper.TTSStatus>(TTSHelper.TTSStatus.IsStopped)
    val ttsStatus: LiveData<TTSHelper.TTSStatus> = _ttsStatus

    fun switchVisibility() {
        _bottomVisibility.postValue(!(_bottomVisibility.value ?: false))
    }

    private var chaptersTitlesInternal: ArrayList<String> = arrayListOf()

    lateinit var desiredIndex: ScrollIndex

    private fun updateChapters() {
        for (idx in chaptersTitlesInternal.size until book.size()) {
            chaptersTitlesInternal.add(book.getChapterTitle(idx))
        }
        _chaptersTitles.postValue(chaptersTitlesInternal)
    }

    private val chapterMutex = Mutex()
    private val chapterExpandMutex = Mutex()

    private val requested: HashSet<Int> = hashSetOf()

    private val loading: HashSet<Int> = hashSetOf()
    private val chapterData: HashMap<Int, Resource<LiveChapterData>> = hashMapOf()
    private val hasExpanded: HashSet<Int> = hashSetOf()

    private var currentIndex = Int.MIN_VALUE

    /**
     * Preloads this much in both directions
     * */
    private var chapterPadding: Int = 1
    private suspend fun updateIndexAsync(
        index: Int,
        notify: Boolean = true
    ) {
        for (idx in index - chapterPadding..index + chapterPadding) {
            requested += index
            loadIndividualChapter(idx, false, notify)
        }
    }

    private fun updateIndex(index: Int) {
        var alreadyRequested = false
        for (idx in index - chapterPadding..index + chapterPadding) {
            if (!requested.contains(index)) {
                alreadyRequested = true
            }
            requested += index
        }

        if (alreadyRequested) return

        ioSafe {
            updateIndexAsync(index)
        }
    }

    private fun updateReadArea() {
        val cIndex = currentIndex
        val chapters = ArrayList<SpanDisplay>()
        for (idx in cIndex - chapterPadding..cIndex + chapterPadding) {
            val append: List<SpanDisplay> = when (val data = chapterData[idx]) {
                null -> emptyList()
                is Resource.Loading -> {
                    listOf<SpanDisplay>(LoadingSpanned(data.url, cIndex))
                }

                is Resource.Success -> {
                    data.value.spans
                }

                is Resource.Failure -> listOf<SpanDisplay>(
                    FailedSpanned(
                        data.errorString,
                        cIndex
                    )
                )
            }
            chapters.addAll(append)
            _chapterData.postValue(chapters)
        }
    }

    private fun notifyChapterUpdate(index: Int) {
        val cIndex = currentIndex
        if (cIndex - chapterPadding <= index && index <= cIndex + chapterPadding) {
            updateReadArea()
        }
    }

    private val markwonMutex = Mutex()

    @WorkerThread
    private suspend fun loadIndividualChapter(
        index: Int,
        reload: Boolean = false,
        notify: Boolean = true
    ) {
        if (index < 0) return

        // set loading and return early if already loading or return cache
        chapterMutex.withLock {
            if (loading.contains(index)) return
            if (!reload) {
                if (chapterData.contains(index)) {
                    return
                }
            }

            loading += index
            chapterData[index] = Resource.Loading(null)
            if (notify) notifyChapterUpdate(index)
        }

        // we check for out of bounds and if it is out of bounds then try to expand it (Reddit next)
        // we lock it here to prevent duplicate loading when init
        chapterExpandMutex.withLock {
            val preSize = book.size()
            while (index >= book.size()) {
                // will only expand once per session per chapter
                if (hasExpanded.contains(book.size())) break
                hasExpanded += book.size()

                try {
                    // we assume that the text is cached
                    book.expand(book.getChapterData(book.size() - 1, reload = false))
                } catch (t: Throwable) {
                    logError(t)
                }
            }
            if (preSize != book.size()) updateChapters()
        }

        // if we are still out of bounds then return no more chapters
        if (index >= book.size()) {
            chapterMutex.withLock {
                chapterData[index] =
                    Resource.Failure(false, null, null, "No more chapters")
                loading -= index
                if (notify) notifyChapterUpdate(index)
            }
            return
        }

        // we have verified we are within bounds, then set the loading to the index url
        chapterMutex.withLock {
            chapterData[index] = Resource.Loading(book.getLoadingStatus(index))
            if (notify) notifyChapterUpdate(index)
        }

        // load the data and precalculate everything needed
        val data = safeApiCall {
            book.getChapterData(index, reload)
        }.map { text ->
            val rawText = preParseHtml(text)
            LiveChapterData(
                rawText = rawText,
                spans = markwonMutex.withLock { parseTextToSpans(rawText, markwon, index) },
                title = book.getChapterTitle(index),
                ttsLines = ttsParseText(rawText)
            )
        }

        // set the data and return
        chapterMutex.withLock {
            chapterData[index] = data
            loading -= index
            if (notify) notifyChapterUpdate(index)
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

            if (epub.size() <= 0) {
                throw ErrorLoadingException("Empty book, failed to parse ${intent.type}")
            }
            epub
        }

        when (loadedBook) {
            is Resource.Success -> {
                init(loadedBook.value, context)

                // cant assume we know a chapter max as it can expand
                val loadedChapter =
                    maxOf(getKey(EPUB_CURRENT_POSITION, book.title()) ?: 0, 0)


                // we the current loaded thing here, but because loadedChapter can be >= book.size (expand) we have to check
                if (loadedChapter < book.size()) {
                    _loadingStatus.postValue(Resource.Loading(book.getLoadingStatus(loadedChapter)))
                }

                changeIndex(loadedChapter, updateArea = false)

                updateIndexAsync(loadedChapter, notify = false)

                val char = getKey(
                    EPUB_CURRENT_POSITION_SCROLL_CHAR, book.title()
                ) ?: 0

                val innerIndex = innerCharToIndex(currentIndex, char) ?: 0

                changeIndex(ScrollIndex(currentIndex, innerIndex), updateArea = false)

                // notify once because initial load is 3 chapters I don't care about 10 notifications when the user cant see it
                notifyChapterUpdate(index = loadedChapter)

                _loadingStatus.postValue(
                    Resource.Success(true)
                )
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

        markwon = Markwon.builder(context) // automatically create Glide instance
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
        runOnMainThread {
            ttsSession = TTSSession(context, ::parseAction)
        }
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

    private fun innerIndexToChar(index: Int, innerIndex: Int?): Int? {
        return chapterData[index]?.letInner { live ->
            innerIndex?.let { live.spans.getOrNull(innerIndex)?.start }
        }
    }

    private fun innerCharToIndex(index: Int, char: Int): Int? {
        return chapterData[index]?.letInner { live ->
            live.spans.firstOrNull { it.start >= char }?.innerIndex
        }
    }

    private fun changeIndex(index: ScrollIndex, updateArea: Boolean = true) {
        desiredIndex = index
        changeIndex(index.index, updateArea)
        innerIndexToChar(index.index, index.innerIndex)?.let { char ->
            setKey(EPUB_CURRENT_POSITION_SCROLL_CHAR, book.title(), char)
        }
    }

    private fun changeIndex(index: Int, updateArea: Boolean = true) {
        val realNewIndex = minOf(index, book.size() - 1)
        if (currentIndex == realNewIndex) return
        setKey(EPUB_CURRENT_POSITION, book.title(), realNewIndex)
        currentIndex = realNewIndex
        if (updateArea) updateReadArea()
        _chapterTile.postValue(chaptersTitlesInternal[realNewIndex])
    }

    fun onScroll(visibility: ScrollVisibilityIndex?) {
        if (visibility == null) return

        // dynamically increase padding in case of very small chapters with a maximum of 10 chapters
        chapterPadding = minOf(
            maxOf(
                chapterPadding,
                kotlin.math.abs(visibility.lastVisible.index - visibility.firstVisible.index)
            ), 5
        )

        changeIndex(visibility.firstVisible)
        updateIndex(visibility.firstVisible.index)
        updateIndex(visibility.lastVisible.index)
    }

    override fun onCleared() {
        ttsSession.unregister(context)
        super.onCleared()
    }
}