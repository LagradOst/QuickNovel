package com.lagradost.quicknovel.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.text.getSpans
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.ChapterLoadSpanned
import com.lagradost.quicknovel.ChapterOverscrollSpanned
import com.lagradost.quicknovel.ChapterStartSpanned
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.FailedSpanned
import com.lagradost.quicknovel.LoadingSpanned
import com.lagradost.quicknovel.MLException
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.SpanDisplay
import com.lagradost.quicknovel.TTSHelper
import com.lagradost.quicknovel.TextSpan
import com.lagradost.quicknovel.databinding.SingleFailedBinding
import com.lagradost.quicknovel.databinding.SingleFinishedChapterBinding
import com.lagradost.quicknovel.databinding.SingleImageBinding
import com.lagradost.quicknovel.databinding.SingleLoadBinding
import com.lagradost.quicknovel.databinding.SingleLoadingBinding
import com.lagradost.quicknovel.databinding.SingleOverscrollChapterBinding
import com.lagradost.quicknovel.databinding.SingleSeparatorBinding
import com.lagradost.quicknovel.databinding.SingleTextBinding
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.UIHelper
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.showImage
import com.lagradost.quicknovel.util.UIHelper.systemFonts
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import java.io.File


const val DRAW_DRAWABLE = 1
const val DRAW_TEXT = 0
const val DRAW_LOADING = 2
const val DRAW_FAILED = 3
const val DRAW_CHAPTER = 4
const val DRAW_LOAD = 5
const val DRAW_OVERSCROLL = 6
const val DRAW_SEPARATOR = 7


data class ScrollVisibilityItem(
    val adapterPosition: Int,
    val viewHolder: RecyclerView.ViewHolder?,
)

/*
data class ScrollVisibility(
    val firstVisible: ScrollVisibilityItem,
    val firstFullyVisible: ScrollVisibilityItem,
    val lastVisible: ScrollVisibilityItem,
    val lastFullyVisible: ScrollVisibilityItem,

    val screenTop: Int,
    val screenBottom: Int,
    val screenTopBar: Int,
)*/

/** a scroll index specifies exactly where you are inside a book SHOULD NOT BE SAVED
 * as ScrollIndex innerIndex should be derived from char
 *
 * index = chapter index
 * innerIndex = what text block, innerIndex *can* be derived from char
 * char = what character in index, not local
 */
data class ScrollIndex(
    val index: Int,
    val innerIndex: Int,
    val char: Int,
)

data class ScrollVisibilityIndex(
    // first in the recyclerview
    val firstInMemory: TextVisualLine,
    // last in the recyclerview
    val lastInMemory: TextVisualLine,

    // first line you can clearly see
    val firstFullyVisible: TextVisualLine?,
    // first line you can't clearly see
    val lastHalfVisible: TextVisualLine?,

    // first line after the bottom bar you can see clearly
    val firstFullyVisibleUnderLine: TextVisualLine?
)

/** this represents a single text line split by the layout, NOT newlines,
 * SHOULD not be stored in any way as layout change or scroll with invalidate the values */
data class TextVisualLine(
    // chars are in relation to index, not span
    val startChar: Int,
    val endChar: Int,

    val index: Int,
    val innerIndex: Int,

    val top: Int,
    val bottom: Int,
)

fun TextVisualLine.toScroll(): ScrollIndex {
    return ScrollIndex(index = this.index, innerIndex = this.innerIndex, char = this.startChar)
}

fun removeHighLightedText(tv: TextView) {
    val wordToSpan: Spannable = SpannableString(tv.text)

    val spans = wordToSpan.getSpans<android.text.Annotation>(0, tv.text.length)
    var shouldUpdate = false
    for (s in spans) {
        if (s.value == "rounded") {
            wordToSpan.removeSpan(s)
            shouldUpdate = true
        }
    }

    // no need to re render an untouched textview
    if (shouldUpdate) {
        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
    }
}

fun setHighLightedText(tv: TextView, start: Int, end: Int) {
    try {
        val wordToSpan: Spannable = SpannableString(tv.text)
        val length = tv.text.length
        val spans = wordToSpan.getSpans<android.text.Annotation>(0, length)

        // remove previous HighLighted text
        for (s in spans) {
            if (s.value == "rounded")
                wordToSpan.removeSpan(s)
        }

        wordToSpan.setSpan(
            android.text.Annotation("", "rounded"),
            minOf(maxOf(start, 0), length),
            minOf(maxOf(end, 0), length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
    } catch (t: Throwable) {
        logError(t)
    }
}

const val CONFIG_COLOR = 1 shl 0
const val CONFIG_FONT = 1 shl 1
const val CONFIG_SIZE = 1 shl 2
const val CONFIG_FONT_BOLD = 1 shl 3
const val CONFIG_FONT_ITALIC = 1 shl 4
const val CONFIG_BG_COLOR = 1 shl 5

// this uses val to make it explicit copy because of lazy properties
data class TextConfig(
    val toolbarHeight: Int,
    val textColor: Int,
    val textSize: Int,
    val textFont: String,
    val defaultFont: Typeface,
    val backgroundColor: Int,
    val bionicReading: Boolean,
    val isTextSelectable: Boolean,
) {
    private val fontFile: File? by lazy {
        if (textFont == "") null else systemFonts.firstOrNull { it.name == textFont }
    }

    private val cachedFont: Typeface by lazy {
        fontFile?.let { file -> Typeface.createFromFile(file) } ?: defaultFont
    }

    private fun setTextFont(textView: TextView, flags: Int) {
        textView.setTypeface(cachedFont, flags)
    }
    /*private fun setTextFont(textView: TextView) {
        if (cachedFont != null) textView.typeface = cachedFont

        val file = fontFile
        cachedFont = if (file == null) {
            defaultFont
            // ResourcesCompat.getFont(textView.context, R.font.google_sans)
        } else {
            Typeface.createFromFile(file)
        }
        textView.typeface = cachedFont
    }*/

    private fun setTextSize(textView: TextView) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
    }

    private fun setTextColor(textView: TextView) {
        textView.setTextColor(textColor)
    }

    private fun setBgTextColor(textView: TextView) {
        textView.setTextColor(backgroundColor)
    }

    fun setArgs(progressBar: ProgressBar) {
        progressBar.progressTintList = ColorStateList.valueOf(textColor)
        progressBar.indeterminateTintList = ColorStateList.valueOf(textColor)
    }

    fun setArgs(textView: TextView, args: Int) {
        if ((args and CONFIG_COLOR) != 0) {
            setTextColor(textView)
        }
        if ((args and CONFIG_BG_COLOR) != 0) {
            setBgTextColor(textView)
        }
        if ((args and CONFIG_FONT) != 0) {
            val bold = (args and CONFIG_FONT_BOLD) != 0
            val italic = (args and CONFIG_FONT_ITALIC) != 0
            val textType = when (bold to italic) {
                false to false -> Typeface.NORMAL
                true to false -> Typeface.BOLD
                false to true -> Typeface.ITALIC
                true to true -> Typeface.BOLD_ITALIC
                else -> throw NotImplementedError()
            }
            setTextFont(
                textView, textType
            )
        }
        if ((args and CONFIG_SIZE) != 0) {
            setTextSize(textView)
        }
    }
}

class TextAdapter(
    private val viewModel: ReadActivityViewModel,
    var config: TextConfig
) :
    NoStateAdapter<SpanDisplay>(DiffCallback()) {
    private var currentTTSLine: TTSHelper.TTSLine? = null

    fun changeHeight(height: Int): Boolean {
        if (config.toolbarHeight == height) return false
        config = config.copy(toolbarHeight = height)
        return true
    }

    fun changeBionicReading(to: Boolean): Boolean {
        if (config.bionicReading == to) return false
        config = config.copy(bionicReading = to)
        return true
    }

    fun changeColor(color: Int): Boolean {
        if (config.textColor == color) return false
        config = config.copy(textColor = color)
        return true
    }

    fun changeSize(size: Int): Boolean {
        if (config.textSize == size) return false
        config = config.copy(textSize = size)
        return true
    }

    fun changeFont(font: String): Boolean {
        if (config.textFont == font) return false
        config = config.copy(textFont = font)
        return true
    }

    fun changeBackgroundColor(color: Int): Boolean {
        if (config.backgroundColor == color) return false
        config = config.copy(backgroundColor = color)
        return true
    }

    fun changeTextSelectable(isTextSelectable: Boolean): Boolean {
        if (config.isTextSelectable == isTextSelectable) return false
        config = config.copy(isTextSelectable = isTextSelectable)
        return true
    }

    override fun onCreateCustomContent(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ViewBinding = when (viewType) {
            DRAW_TEXT -> SingleTextBinding.inflate(inflater, parent, false)
            DRAW_DRAWABLE -> SingleImageBinding.inflate(inflater, parent, false)
            DRAW_LOADING -> SingleLoadingBinding.inflate(inflater, parent, false)
            DRAW_FAILED -> SingleFailedBinding.inflate(inflater, parent, false)
            DRAW_CHAPTER -> SingleFinishedChapterBinding.inflate(inflater, parent, false)
            DRAW_LOAD -> SingleLoadBinding.inflate(inflater, parent, false)
            DRAW_OVERSCROLL -> SingleOverscrollChapterBinding.inflate(inflater, parent, false)
            DRAW_SEPARATOR -> SingleSeparatorBinding.inflate(inflater, parent, false)
            else -> throw NotImplementedError()
        }

        return ViewHolderState(binding)
    }

    /** updates new onbind calls, but not current */
    fun updateTTSLine(line: TTSHelper.TTSLine?) {
        currentTTSLine = line
    }

    /*fun getViewOffset(scrollVisibility: ScrollVisibilityItem, char: Int): Int? {
        try {
            if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return null
            //val item = getItem(scrollVisibility.index)
            val viewHolder = scrollVisibility.viewHolder
            if (viewHolder !is TextAdapterHolder) return null
            val binding = viewHolder.binding
            if (binding !is SingleTextBinding) return null
            val outLocation = IntArray(2)
            binding.root.getLocationInWindow(outLocation)
            binding.root.layout.apply {
                for (i in 0 until lineCount) {
                    if (getLineEnd(i) >= char) {
                        //binding.root.getLocationInWindow(outLocation)
                        //val (_, y) = outLocation

                        return getLineTop(i)
                    }
                }
            }
        } catch (_ : Throwable) { }
        return null
    }*/

    /* private fun transformIndexToScrollIndex(
         scrollVisibility: ScrollVisibilityItem,
         screenTop: Int,
         screenBottom: Int
     ): ScrollIndex? {
         try {
             if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return null
             val item = getItem(scrollVisibility.adapterPosition)
             val viewHolder = scrollVisibility.viewHolder

             var firstVisibleChar: Int? = null
             var firstInvisibleChar: Int? = null
             if (viewHolder is TextAdapterHolder) {
                 val binding = viewHolder.binding
                 if (binding is SingleTextBinding) {
                     val outLocation = IntArray(2)
                     binding.root.getLocationInWindow(outLocation)
                     val (_, y) = outLocation
                     binding.root.layout.apply {
                         for (i in 0 until lineCount) {
                             val top = y + getLineTop(i)
                             val bottom = y + getLineBottom(i)
                             if (top < screenBottom && top > screenTop && bottom < screenBottom && bottom > screenTop) {
                                 if (firstVisibleChar == null) firstVisibleChar = getLineStart(i)
                                 if (firstInvisibleChar != null) break
                             } else {
                                 if (firstInvisibleChar == null) firstInvisibleChar = getLineStart(i)
                                 if (firstVisibleChar != null) break
                             }
                         }
                     }
                 }
             }

             return ScrollIndex(
                 index = item.index,
                 innerIndex = item.innerIndex,
                 firstVisibleChar = firstVisibleChar,
                 firstInvisibleChar = firstInvisibleChar
             )
         } catch (_ : Throwable) {
             return null
         }
     }*/


    fun getLines(scrollVisibility: ScrollVisibilityItem): List<TextVisualLine> {
        try {
            if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return emptyList()
            val viewHolder = scrollVisibility.viewHolder
            if (viewHolder !is ViewHolderState<*>) return emptyList()
            val binding = viewHolder.view
            if (binding !is SingleTextBinding) return emptyList()
            val span = getItem(scrollVisibility.adapterPosition)
            if (span !is TextSpan) return emptyList()

            val outLocation = IntArray(2)
            binding.root.getLocationInWindow(outLocation)
            val y = outLocation[1] + binding.root.paddingTop

            //val paddingTop =
            //val paddingBottom = binding.root.paddingBottom


            val list = arrayListOf<TextVisualLine>()
            binding.root.layout.apply {
                for (i in 0 until lineCount) {
                    list.add(
                        TextVisualLine(
                            startChar = span.start + getLineStart(i),
                            endChar = span.start + getLineEnd(i),
                            innerIndex = span.innerIndex,
                            index = span.index,
                            top = getLineTop(i) + y,//+paddingTop,
                            bottom = getLineBottom(i) + y//+paddingBottom
                        )
                    )
                }
            }
            return list
        } catch (t: Throwable) {
            return emptyList()
        }
    }

    /*fun getIndex(data: ScrollVisibility): ScrollVisibilityIndex? {
        return ScrollVisibilityIndex(
            firstVisible = transformIndexToScrollIndex(
                data.firstVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            firstFullyVisible = transformIndexToScrollIndex(
                data.firstFullyVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            lastFullyVisible = transformIndexToScrollIndex(
                data.lastFullyVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            lastVisible = transformIndexToScrollIndex(
                data.lastVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
        )
    }*/

    override fun onBindContent(holder: ViewHolderState<Any>, item: SpanDisplay, position: Int) {
        val binding = holder.view

        when (item) {
            is TextSpan -> {
                this.bindText(binding, item, config)
                // because we bind text here we know that it will be cleared and thus
                // we do not have to update it with null
                if (currentTTSLine != null)
                    this.updateTTSLine(binding, item, currentTTSLine)
            }

            is LoadingSpanned -> {
                this.bindLoading(binding, item)
            }

            is FailedSpanned -> {
                this.bindFailed(binding, item)
            }

            is ChapterStartSpanned -> {
                this.bindChapter(binding, item)
            }

            is ChapterLoadSpanned -> {
                this.bindLoadChapter(binding, item)
            }

            is ChapterOverscrollSpanned -> {
                this.bindOverscrollChapter(binding, item)
            }

            else -> throw NotImplementedError()
        }
        setConfig(binding, config)
    }

    // a full line of these characters is often used as a SEPARATOR
    val separatorRegex = Regex("[=\\-_\\s━*]*")

    override fun customContentViewType(item: SpanDisplay): Int {
        return when (item) {
            is TextSpan -> {
                if (item.text.matches(separatorRegex)) {
                    DRAW_SEPARATOR
                } else if (item.text.getSpans<AsyncDrawableSpan>(0, item.text.length)
                        .isNotEmpty()
                ) {
                    DRAW_DRAWABLE
                } else {
                    DRAW_TEXT
                }
            }

            is LoadingSpanned -> {
                DRAW_LOADING
            }

            is FailedSpanned -> {
                DRAW_FAILED
            }

            is ChapterStartSpanned -> {
                DRAW_CHAPTER
            }

            is ChapterLoadSpanned -> {
                DRAW_LOAD
            }

            is ChapterOverscrollSpanned -> {
                DRAW_OVERSCROLL
            }

            else -> throw NotImplementedError()
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    private fun bindLoading(binding: ViewBinding, obj: LoadingSpanned) {
        if (binding !is SingleLoadingBinding) return
        binding.text.setText(obj.text)
        binding.root.setOnClickListener {
            viewModel.switchVisibility()
        }
    }

    private fun bindFailed(binding: ViewBinding, obj: FailedSpanned) {
        if (binding !is SingleFailedBinding) return

        binding.root.setText(obj.reason)

        binding.root.setOnClickListener {
            if (obj.cause == null) {
                viewModel.switchVisibility()
                return@setOnClickListener
            }

            showToast(txt(R.string.reload_chapter_format, (obj.index + 1).toString()))
            if (obj.cause is MLException) {
                viewModel.reTranslateChapter(obj.index)
            } else {
                viewModel.reloadChapter(obj.index)
            }
        }
    }

    private fun bindImage(binding: ViewBinding, img: AsyncDrawable) {
        if (binding !is SingleImageBinding) return
        val url = img.destination
        if (binding.root.url == url) return
        binding.root.url = url // don't reload if already set
        UIHelper.bindImage(binding.root, img)
    }

    private fun bindText(binding: ViewBinding, obj: TextSpan, config: TextConfig) {
        when (binding) {
            is SingleSeparatorBinding -> {

            }

            is SingleImageBinding -> {
                val img = obj.text.getSpans<AsyncDrawableSpan>(0, obj.text.length)[0]
                bindImage(binding, img.drawable)

                binding.root.setOnClickListener { root ->
                    if (root !is TextImageView) {
                        return@setOnClickListener
                    }
                    showImage(root.context, img.drawable)
                }

                /*val size = 300.toPx

                binding.root.layoutParams = binding.root.layoutParams.apply {
                    height = size
                }

                binding.root.setOnClickListener { root ->
                    if (root !is TextImageView) {
                        return@setOnClickListener
                    }
                    root.layoutParams = root.layoutParams.apply {
                        height = if (height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                            size
                        } else {
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                    root.requestLayout()
                }
                bindImage(binding,img)*/
            }

            is SingleTextBinding -> {
                binding.root.apply {
                    // this is set to fix the nonclick https://stackoverflow.com/questions/8641343/android-clickablespan-not-calling-onclick
                    text = if (config.bionicReading) {
                        obj.bionicText
                    } else {
                        obj.text
                    }

                    setTextIsSelectable(false) // this is so retarded

                    // https://stackoverflow.com/questions/36801486/androidtextisselectable-true-not-working-for-textview-in-recyclerview
                    if (config.isTextSelectable) {
                        post {
                            setTextIsSelectable(true)
                            movementMethod = LinkMovementMethod.getInstance()
                            setOnClickListener {
                                viewModel.switchVisibility()
                            }
                        }
                    } else {
                        movementMethod = LinkMovementMethod.getInstance()
                        setOnClickListener {
                            viewModel.switchVisibility()
                        }
                    }
                    //val links = obj.text.getSpans<io.noties.markwon.core.spans.LinkSpan>()
                    //if (links.isNotEmpty()) {
                    //   println("URLS: ${links.size} : ${links.map { it.url }}")
                    //}
                }
            }

            else -> {}
        }
    }

    fun updateTTSLine(binding: ViewBinding, span: TextSpan, line: TTSHelper.TTSLine?) {
        if (binding !is SingleTextBinding) return
        // if the line does not apply
        if (line == null || line.index != span.index ||
            (line.startChar < span.start && line.endChar < span.start)
            || (line.startChar > span.end && line.endChar > span.end)
        ) {
            removeHighLightedText(binding.root)
            return
        }

        val length = binding.root.length()
        val start = minOf(maxOf(line.startChar - span.start, 0), length)
        val end = minOf(maxOf(line.endChar - span.start, 0), length)

        setHighLightedText(
            binding.root,
            start,
            end
        )
    }

    private fun setConfig(binding: ViewBinding, config: TextConfig) {
        when (binding) {
            is SingleTextBinding -> {
                config.setArgs(
                    binding.root,
                    CONFIG_SIZE or CONFIG_COLOR or CONFIG_FONT
                )
            }

            is SingleSeparatorBinding -> {
                binding.root.setBackgroundColor(config.textColor)
            }

            is SingleLoadingBinding -> {
                config.setArgs(binding.text, CONFIG_COLOR or CONFIG_FONT or CONFIG_FONT_BOLD)
                config.setArgs(binding.loadingBar)
                binding.root.minimumHeight = config.toolbarHeight
            }

            is SingleFailedBinding -> {
                config.setArgs(binding.root, CONFIG_COLOR or CONFIG_FONT or CONFIG_FONT_BOLD)
                binding.root.minHeight = config.toolbarHeight
            }

            is SingleFinishedChapterBinding -> {
                config.setArgs(binding.root, CONFIG_COLOR or CONFIG_FONT or CONFIG_FONT_BOLD)
                binding.root.minHeight = config.toolbarHeight
            }

            is SingleLoadBinding -> {
                config.setArgs(binding.root, CONFIG_BG_COLOR or CONFIG_FONT or CONFIG_FONT_BOLD)
                binding.root.backgroundTintList = ColorStateList.valueOf(config.textColor)
            }

            is SingleOverscrollChapterBinding -> {
                config.setArgs(binding.text, CONFIG_COLOR or CONFIG_FONT or CONFIG_FONT_BOLD)
                binding.progress.progressTintList = ColorStateList.valueOf(config.textColor)
            }

            else -> {}
        }
    }

    private fun bindLoadChapter(binding: ViewBinding, obj: ChapterLoadSpanned) {
        if (binding !is SingleLoadBinding) return
        binding.root.setText(obj.name)
        binding.root.setOnClickListener {
            viewModel.seekToChapter(obj.loadIndex)
        }
    }

    private fun bindOverscrollChapter(
        binding: ViewBinding,
        obj: ChapterOverscrollSpanned
    ) {
        if (binding !is SingleOverscrollChapterBinding) return

        //binding.text.setText(obj.name)
        binding.text.isVisible = false
        binding.progress.progress = 0
        //binding.root.setOnClickListener {
        //    viewModel.seekToChapter(obj.loadIndex)
        //}
    }

    private fun bindChapter(binding: ViewBinding, obj: ChapterStartSpanned) {
        if (binding !is SingleFinishedChapterBinding) return

        binding.root.setText(obj.name)
        binding.root.setOnClickListener {
            viewModel.switchVisibility()
        }
        binding.root.setOnLongClickListener {
            if (!obj.canReload) {
                return@setOnLongClickListener true
            }
            it?.popupMenu(
                items = listOf(1 to R.string.reload_chapter),
                selectedItemId = -1
            ) {
                if (itemId == 1) {
                    showToast(
                        txt(R.string.reload_chapter_format, (obj.index + 1).toString())
                    )
                    viewModel.reloadChapter(obj.index)
                }
            }
            return@setOnLongClickListener true
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SpanDisplay>() {
        override fun areItemsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean {
            return when (oldItem) {
                is TextSpan -> {
                    if (newItem !is TextSpan) return false
                    // don't check the span content as that does not change
                    return newItem.end == oldItem.end && newItem.start == oldItem.start && newItem.index != oldItem.index
                }

                is LoadingSpanned -> {
                    if (newItem !is LoadingSpanned) return false

                    newItem.id == oldItem.id && newItem.url == oldItem.url
                }

                is FailedSpanned -> {
                    if (newItem !is FailedSpanned) return false

                    newItem.id == oldItem.id && newItem.reason == oldItem.reason
                }

                is ChapterStartSpanned -> {
                    if (newItem !is ChapterStartSpanned) return false

                    newItem.id == oldItem.id && oldItem.name == newItem.name
                }

                is ChapterLoadSpanned -> {
                    if (newItem !is ChapterLoadSpanned) return false
                    newItem.id == oldItem.id && oldItem.name == newItem.name
                }

                is ChapterOverscrollSpanned -> {
                    if (newItem !is ChapterOverscrollSpanned) return false
                    newItem.id == oldItem.id && oldItem.name == newItem.name
                }

                else -> throw NotImplementedError()
            }
        }
    }
}