package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.Voice
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.databinding.ReadBottomSettingsBinding
import com.lagradost.quicknovel.databinding.ReadMainBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.ui.OrientationType
import com.lagradost.quicknovel.ui.ScrollIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityItem
import com.lagradost.quicknovel.ui.TextAdapter
import com.lagradost.quicknovel.ui.TextVisualLine
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.systemFonts
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.properties.Delegates

class ReadActivity2 : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        private var _readActivity: WeakReference<ReadActivity2>? = null
        var readActivity
            get() = _readActivity?.get()
            private set(value) {
                _readActivity = WeakReference(value)
            }
    }

    private fun hideSystemUI() {

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.readerContainer).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                v.isVisible = false
            }
        }

        lowerBottomNav(binding.readerBottomViewHolder)

        binding.readToolbarHolder.translationY = 0f
        ObjectAnimator.ofFloat(
            binding.readToolbarHolder,
            "translationY",
            -binding.readToolbarHolder.height.toFloat()
        ).apply {
            duration = 200
            start()
        }.doOnEnd {
            binding.readToolbarHolder.isVisible = false
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(
            window,
            binding.readerContainer
        ).show(WindowInsetsCompat.Type.systemBars())

        binding.readToolbarHolder.isVisible = true

        fun higherBottomNavView(v: View) {
            v.isVisible = true
            v.translationY = v.height.toFloat()
            ObjectAnimator.ofFloat(v, "translationY", 0f).apply {
                duration = 200
                start()
            }
        }

        higherBottomNavView(binding.readerBottomViewHolder)

        binding.readToolbarHolder.translationY = -binding.readToolbarHolder.height.toFloat()

        ObjectAnimator.ofFloat(binding.readToolbarHolder, "translationY", 0f).apply {
            duration = 200
            start()
        }
    }

    lateinit var binding: ReadMainBinding
    private val viewModel: ReadActivityViewModel by viewModels()

    override fun onColorSelected(dialog: Int, color: Int) {
        when (dialog) {
            0 -> setBackgroundColor(color)
            1 -> setTextColor(color)
        }
    }

    private fun setBackgroundColor(color: Int) {
        viewModel.backgroundColor = color
    }

    private fun setTextColor(color: Int) {
        viewModel.textColor = color
    }

    override fun onDialogDismissed(dialog: Int) {
        updateImages()
    }

    private fun updateImages() {

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            kill()
            return true
        }
        if ((keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) return false

        // if we have the bottom bar up then we ignore the override functionality
        if (viewModel.bottomVisibility.isInitialized && viewModel.bottomVisibility.value == true) return false

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.forwardsTTS()
                    return true
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.backwardsTTS()
                    return true
                }
            }
        }

        return false
    }

    private fun kill() {
        with(NotificationManagerCompat.from(this)) { // KILLS NOTIFICATION
            cancel(TTS_NOTIFICATION_ID)
        }
        finish()
    }

    private fun registerBattery() {
        val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent) {
                val batteryPct: Float = run {
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level * 100 / scale.toFloat()
                }
                binding.readBattery.text =
                    getString(R.string.battery_format).format(batteryPct.toInt())
            }
        }
        this.registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun parseAction(input: TTSHelper.TTSActionType): Boolean {
        return viewModel.parseAction(input)
    }

    private lateinit var textAdapter: TextAdapter
    private lateinit var textLayoutManager: LinearLayoutManager

    private fun transformIndexToScrollVisibilityItem(adapterPosition: Int): ScrollVisibilityItem {
        return ScrollVisibilityItem(
            adapterPosition = adapterPosition,
            viewHolder = binding.realText.findViewHolderForAdapterPosition(adapterPosition),
        )
    }

    private fun getTopY(): Int {
        val outLocation = IntArray(2)
        binding.readTopItem.getLocationInWindow(outLocation)
        val (_, topY) = outLocation
        return topY
    }

    private fun getBottomY(): Int {
        val outLocation = IntArray(2)
        binding.readBottomItem.getLocationInWindow(outLocation)
        val (_, bottomY) = outLocation
        return bottomY
    }

    /**

    ________________
    [ hello ]
    -- screen cut --
    [ world ]
    [ ! ]
    [ From kotlin ]
    ________________

    here the first index of "world" would be stored as it is the first whole line visible,
    while "hello" would be stored as the first invisible line, this is used to scroll the exact char
    you are on. so while rotating you would rotate to the first line that contains "world"

    This is also used for TTS because TTS *must* start at the first visible whole sentence,
    so in this case it would start at "From kotlin" because hello is not visible.
     */

    private fun getAllLines(): ArrayList<TextVisualLine> {
        val lines: ArrayList<TextVisualLine> = arrayListOf()

        for (i in textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
            lines.addAll(textAdapter.getLines(transformIndexToScrollVisibilityItem(i)))
        }
        return lines
    }

    private fun postLines(lines: ArrayList<TextVisualLine>) {
        if (lines.isEmpty()) {
            return
        }

        viewModel.onScroll(
            ScrollVisibilityIndex(
                firstInMemory = lines.first(),
                lastInMemory = lines.last(),
                firstFullyVisible = lines.firstOrNull {
                    it.top >= 0
                },
                firstFullyVisibleUnderLine = lines.firstOrNull {
                    it.top >= topBarHeight
                },
                lastHalfVisible = lines.firstOrNull {
                    it.bottom >= getBottomY()
                },
            )
        )

        /*val desired = viewModel.desiredIndex

        lines.firstOrNull {
            it.startChar >= desired.char
        }?.let { line ->
            binding.tmpTtsStart.fixLine(line.top)
            binding.tmpTtsEnd.fixLine(line.bottom)
        }*/

    }

    fun onScroll() {
        val lines = getAllLines()
        postLines(lines)


        /*val topY = getTopY()
        val bottomY = getBottomY()

        val visibility = ScrollVisibility(
            firstVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findFirstVisibleItemPosition()),
            firstFullyVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findFirstCompletelyVisibleItemPosition()),
            lastVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findLastVisibleItemPosition()),
            lastFullyVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findLastCompletelyVisibleItemPosition()),
            screenTop = topY,
            screenBottom = bottomY,
            screenTopBar = binding.readToolbarHolder.height
        )

        viewModel.onScroll(textAdapter.getIndex(visibility))*/
    }

    private var cachedChapter: List<SpanDisplay> = emptyList()
    private fun scrollToDesired() {
        val desired: ScrollIndex = viewModel.desiredIndex

        val adapterPosition =
            cachedChapter.indexOfFirst { display -> display.index == desired.index && display.innerIndex == desired.innerIndex }
        if (adapterPosition == -1) return

        //val offset = 7.toPx
        textLayoutManager.scrollToPositionWithOffset(adapterPosition, 1)

        // don't inner-seek if zero because that is chapter break
        if (desired.innerIndex == 0) return

        binding.realText.post {
            getAllLines().also { postLines(it) }.firstOrNull { line ->
                line.index == desired.index && line.endChar >= desired.char
            }?.let { line ->
                //binding.tmpTtsStart2.fixLine(line.top)
                //binding.tmpTtsEnd2.fixLine(line.bottom)
                binding.realText.scrollBy(0, line.top)
            }
        }

        /*desired.firstVisibleChar?.let { visible ->
                binding.realText.post {
                    binding.realText.scrollBy(
                        0,
                        (textAdapter.getViewOffset(
                            transformIndexToScrollVisibilityItem(adapterPosition),
                            visible
                        ) ?: 0) + offset
                    )
                }
            }*/

    }

    private fun View.fixLine(offset: Int) {
        // this.setPadding(0, 200, 0, 0)
        val layoutParams =
            this.layoutParams as FrameLayout.LayoutParams// FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,offset)
        layoutParams.setMargins(0, offset, 0, 0)
        this.layoutParams = layoutParams
    }

    var lockTop: Int? = null
    var lockBottom: Int? = null
    var currentScroll: Int = 0

    private fun updateTTSLine(line: TTSHelper.TTSLine?) {
        // update the visual component
        textAdapter.updateTTSLine(line)
        for (position in textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
            val viewHolder = binding.realText.findViewHolderForAdapterPosition(position)
            if (viewHolder !is TextAdapter.TextAdapterHolder) continue
            viewHolder.updateTTSLine(line)
        }

        // update the lock area
        if (line == null || !viewModel.ttsLock) {
            lockTop = null
            lockBottom = null
            return
        }

        val lines = getAllLines()
        postLines(lines)

        val top = lines.firstOrNull { it.index == line.index && it.endChar > line.startChar }
        val bottom =
            lines.firstOrNull { it.index == line.index && it.startChar <= line.endChar && line.endChar <= it.endChar }

        if (top == null || bottom == null) {
            lockTop = null
            lockBottom = null

            // this should never happened as tts line must be valid
            val innerIndex = viewModel.innerCharToIndex(line.index, line.startChar) ?: return

            // scroll to the top of that line, first search the adapter
            val adapterPosition =
                cachedChapter.indexOfFirst { display -> display.index == line.index && display.innerIndex == innerIndex }

            // if we tts out of bounds somehow? we scroll to that and refresh everything
            if (adapterPosition == -1) {
                viewModel.scrollToDesired(
                    ScrollIndex(
                        index = line.index,
                        innerIndex = innerIndex,
                        line.startChar
                    )
                )
                return
            }

            textLayoutManager.scrollToPositionWithOffset(adapterPosition, 1)
            textLayoutManager.postOnAnimation {
                updateTTSLine(line)
            }
            return
        }

        val topScroll = top.top - getTopY()
        lockTop = currentScroll + topScroll
        val bottomScroll = bottom.bottom - getBottomY() + binding.readOverlay.height
        lockBottom = currentScroll + bottomScroll

        // binding.tmpTtsStart.fixLine(top.top)
        // binding.tmpTtsEnd.fixLine(bottom.bottom)

        // we have reached the end, scroll to the top
        if (bottomScroll > 0) {
            binding.realText.scrollBy(0, topScroll)
        }
        // we have scrolled up while being on top
        else if (topScroll < 0) {
            binding.realText.scrollBy(0, topScroll)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // we save this just in case the user fucks it up somehow
        postDesired(binding.realText)
        super.onConfigurationChanged(newConfig)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateTextAdapterConfig() {
        // this did not work so I just rebind everything, it does not happend often so idc
        textAdapter.notifyDataSetChanged()

        /* binding.realText.apply {
             for (idx in 0..childCount) {//textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
                 val viewHolder = getChildViewHolder(getChildAt(idx) ?: continue) ?: continue
                 if (viewHolder !is TextAdapter.TextAdapterHolder) continue
                 viewHolder.setConfig(textAdapter.config)
             }
         }*/
    }

    private fun postDesired(view: View) {
        val currentDesired = viewModel.desiredIndex
        view.post {
            viewModel.desiredIndex = currentDesired
            scrollToDesired()
            updateTTSLine(viewModel.ttsLine.value)
        }
    }

    /*private fun pendingPost() {
        binding.readToolbarHolder.post {
            val height = binding.readToolbarHolder.height
            // height cant be 0
            if(height == 0) {
                pendingPost()
                return@post
            }

            if(textAdapter.changeHeight(binding.readToolbarHolder.height + getStatusBarHeight())) {
                updateTextAdapterConfig()
            }
        }
    }*/
    private fun showFonts() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.font_bottom_sheet)
        val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!

        val fonts = systemFonts
        val items = fonts.toMutableList() as java.util.ArrayList<File?>
        items.add(0, null)

        val currentName = getKey(EPUB_FONT) ?: ""
        val storingIndex = items.indexOfFirst { (it?.name ?: "") == currentName }

        /* val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
         arrayAdapter.addAll(sortingMethods.toMutableList())
         res.choiceMode = AbsListView.CHOICE_MODE_SINGLE
         res.adapter = arrayAdapter
         res.setItemChecked(sotringIndex, true)*/
        val adapter = FontAdapter(this, storingIndex, items)

        res.adapter = adapter
        res.setOnItemClickListener { _, _, which, _ ->
            viewModel.textFont = items[which]?.name ?: ""
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }

    private var topBarHeight by Delegates.notNull<Int>()
    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        readActivity = this
        binding = ReadMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerBattery()

        viewModel.init(intent, this)
        topBarHeight = binding.readToolbarHolder.minimumHeight + getStatusBarHeight()
        binding.readToolbarHolder.minimumHeight = topBarHeight
        textAdapter = TextAdapter(
            viewModel,
            viewModel.textConfigInit.copy(
                toolbarHeight = topBarHeight,
                defaultFont = binding.readText.typeface
            )
        ).apply {
            setHasStableIds(true)
        }

        fixPaddingStatusbar(binding.readToolbarHolder)
        //fixPaddingStatusbar(binding.readTopmargin)

        //pendingPost()


        observe(viewModel.textSizeLive) { size ->
            if (textAdapter.changeSize(size)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.textColorLive) { color ->
            if (textAdapter.changeColor(color)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.textFontLive) { font ->
            if (textAdapter.changeFont(font)) {
                updateTextAdapterConfig()
            }
        }

        textLayoutManager = LinearLayoutManager(binding.realText.context)

        binding.ttsActionPausePlay.setOnClickListener {
            viewModel.pausePlayTTS()
        }

        binding.ttsActionStop.setOnClickListener {
            viewModel.stopTTS()
        }

        binding.readActionTts.setOnClickListener {
            //scrollToDesired()
            viewModel.startTTS()
        }

        binding.ttsActionForward.setOnClickListener {
            viewModel.forwardsTTS()
        }

        binding.ttsActionBack.setOnClickListener {
            viewModel.backwardsTTS()
        }

        observe(viewModel.orientationLive) { position ->
            val org = OrientationType.fromSpinner(position)
            requestedOrientation = org.flag
            binding.readActionRotate.setImageResource(org.iconRes)

            binding.readActionRotate.apply {
                setOnClickListener {
                    popupMenu(
                        items = OrientationType.values().map { it.prefValue to it.stringRes },
                        selectedItemId = org.prefValue
                    ) {
                        viewModel.orientation = itemId
                    }
                }
            }
        }

        observeNullable(viewModel.ttsLine) { line ->
            updateTTSLine(line)
        }

        observe(viewModel.title) { title ->
            binding.readToolbar.title = title
        }

        observe(viewModel.chapterTile) { title ->
            binding.readToolbar.subtitle = title
        }

        observe(viewModel.chaptersTitles) { titles ->
            binding.readActionChapters.setOnClickListener {
                val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
                //builderSingle.setIcon(R.drawable.ic_launcher)
                val currentChapter = viewModel.desiredIndex.index
                // cant be too safe here
                val validChapter = currentChapter >= 0 && currentChapter < titles.size
                if (validChapter) {
                    builderSingle.setTitle(titles[currentChapter]) //  "Select Chapter"
                } else {
                    builderSingle.setTitle(R.string.select_chapter)
                }

                val arrayAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)

                arrayAdapter.addAll(titles)

                builderSingle.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }

                builderSingle.setAdapter(arrayAdapter) { _, which ->
                    viewModel.seekToChapter(which)
                }

                val dialog = builderSingle.create()
                dialog.show()

                dialog.listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                if (validChapter) {
                    dialog.listView.setSelection(currentChapter)
                    dialog.listView.setItemChecked(currentChapter, true)
                }
            }
        }

        observe(viewModel.ttsStatus) { status ->
            val isTTSRunning = status != TTSHelper.TTSStatus.IsStopped

            binding.readerBottomView.isGone = isTTSRunning
            binding.readerBottomViewTts.isVisible = isTTSRunning
            binding.ttsActionPausePlay.setImageResource(
                when (status) {
                    TTSHelper.TTSStatus.IsPaused -> R.drawable.ic_baseline_play_arrow_24
                    TTSHelper.TTSStatus.IsRunning -> R.drawable.ic_baseline_pause_24
                    TTSHelper.TTSStatus.IsStopped -> R.drawable.ic_baseline_play_arrow_24
                }
            )
        }

        binding.apply {
            realText.setOnClickListener {
                viewModel.switchVisibility()
            }
            readToolbar.setOnClickListener {
                viewModel.switchVisibility()
            }
            readerLinContainer.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        observe(viewModel.bottomVisibility) { visibility ->
            if (visibility)
                showSystemUI()
            else
                hideSystemUI()
        }

        observe(viewModel.loadingStatus) { loading ->
            when (loading) {
                is Resource.Success -> {
                    binding.readLoading.isVisible = false
                    binding.readFail.isVisible = false

                    binding.readNormalLayout.isVisible = true
                    binding.readNormalLayout.alpha = 0.01f

                    ObjectAnimator.ofFloat(binding.readNormalLayout, "alpha", 1f).apply {
                        duration = 300
                        start()
                    }
                }

                is Resource.Loading -> {
                    binding.readNormalLayout.isVisible = false
                    binding.readFail.isVisible = false
                    binding.readLoading.isVisible = true
                    binding.loadingText.apply {
                        isGone = loading.url.isNullOrBlank()
                        text = loading.url ?: ""
                    }
                }

                is Resource.Failure -> {
                    binding.readLoading.isVisible = false
                    binding.readFail.isVisible = true
                    binding.failText.text = loading.errorString
                    binding.readNormalLayout.isVisible = false
                }
            }
        }


        binding.realText.apply {
            layoutManager = textLayoutManager
            adapter = textAdapter
            itemAnimator = null

            addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    var rdy = dy

                    onScroll()
                    lockTop?.let { lock ->
                        if (currentScroll + rdy > lock) {
                            rdy = lock - currentScroll
                            fling(0, 0)
                        }
                    }

                    lockBottom?.let { lock ->
                        if (currentScroll + rdy < lock) {
                            rdy = lock - currentScroll
                            fling(0, 0)
                        }
                    }

                    currentScroll += dy
                    val delta = rdy - dy
                    if (delta != 0) scrollBy(0, delta)
                    super.onScrolled(recyclerView, dx, dx)

                    // binding.tmpTtsEnd.fixLine((getBottomY()- remainingBottom) + 7.toPx)
                    // binding.tmpTtsStart.fixLine(remainingTop + 7.toPx)
                }
            })
        }

        observe(viewModel.chapter) { chapter ->
            cachedChapter = chapter.data
            textAdapter.submitList(chapter.data) {
                if (chapter.seekToDesired) {
                    scrollToDesired()
                }
                onScroll()
            }
        }

        binding.readActionSettings.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(this)

            val binding = ReadBottomSettingsBinding.inflate(layoutInflater, null, false)
            bottomSheetDialog.setContentView(binding.root)

            val fontSizeProgressOffset = 10

            binding.readSettingsTextSizeText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.textSize = DEF_FONT_SIZE
                        binding.readSettingsTextSize.progress =
                            DEF_FONT_SIZE - fontSizeProgressOffset
                    }
                }
            }

            binding.readSettingsTextSize.apply {
                max = 20
                progress = viewModel.textSize - fontSizeProgressOffset
                setOnSeekBarChangeListener(object :
                    SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        viewModel.textSize = progress + fontSizeProgressOffset
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            binding.readShowFonts.apply {
                //text = UIHelper.parseFontFileName(getKey(EPUB_FONT))
                setOnClickListener {
                    showFonts()
                }
            }

            binding.readSettingsTextFontText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.textFont = ""
                    }
                }
            }

            binding.readLanguage.setOnClickListener { _ ->
                ioSafe {
                    viewModel.ttsSession.requireTTS { tts ->
                        runOnUiThread {
                            val languages = mutableListOf<Locale?>(null).apply {
                                addAll(tts.availableLanguages?.filterNotNull() ?: emptySet())
                            }
                            val ctx = binding.readLanguage.context ?: return@runOnUiThread
                            ctx.showBottomDialog(
                                languages.map {
                                    it?.displayName ?: ctx.getString(R.string.default_text)
                                },
                                languages.indexOf(tts.voice?.locale),
                                ctx.getString(R.string.tts_locale), false, {}
                            ) { index ->
                                viewModel.setTTSLanguage(languages.getOrNull(index))
                            }
                        }
                    }
                }
            }

            binding.readLanguage.setOnLongClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.setTTSLanguage(null)
                    }
                }

                return@setOnLongClickListener true
            }

            binding.readVoice.setOnLongClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.setTTSVoice(null)
                    }
                }

                return@setOnLongClickListener true
            }

            binding.readVoice.setOnClickListener {
                ioSafe {
                    viewModel.ttsSession.requireTTS { tts ->
                        runOnUiThread {
                            val matchAgainst = tts.voice.locale.language
                            val voices = mutableListOf<Voice?>(null).apply {
                                addAll(tts.voices.filter { it != null && it.locale.language == matchAgainst })
                            }
                            val ctx = binding.readLanguage.context ?: return@runOnUiThread

                            ctx.showBottomDialog(
                                voices.map { it?.name ?: ctx.getString(R.string.default_text) },
                                voices.indexOf(tts.voice),
                                ctx.getString(R.string.tts_locale), false, {}
                            ) { index ->
                                viewModel.setTTSVoice(voices.getOrNull(index))
                            }
                        }
                    }
                }
            }

            binding.apply {
                hardResetStream.isVisible = viewModel.canReload()
                hardResetStream.setOnClickListener {
                    showToast(getString(R.string.reload_chapter_format).format(""))
                    viewModel.reloadChapter()
                }

                readSettingsScrollVol.isChecked = viewModel.scrollWithVolume
                readSettingsScrollVol.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.scrollWithVolume = isChecked
                }

                readSettingsLockTts.isChecked = viewModel.ttsLock
                readSettingsLockTts.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.ttsLock = isChecked
                }

                readSettingsShowTime.isChecked = viewModel.showTime
                readSettingsShowTime.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.showTime = isChecked
                }

                readSettingsTwelveHourTime.isChecked = viewModel.time12H
                readSettingsTwelveHourTime.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.time12H = isChecked
                }

                readSettingsShowBattery.isChecked = viewModel.showBattery
                readSettingsShowBattery.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.showBattery = isChecked
                }

                readSettingsKeepScreenActive.isChecked = viewModel.screenAwake
                readSettingsKeepScreenActive.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.screenAwake = isChecked
                }
            }

            /*binding.readLanguage.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    requireTTS { tts ->
                        val languages = mutableListOf<Locale?>(null).apply {
                            addAll(tts.availableLanguages?.filterNotNull() ?: emptySet())
                        }
                        ctx.showBottomDialog(
                            languages.map {
                                it?.displayName ?: ctx.getString(R.string.default_text)
                            },
                            languages.indexOf(tts.voice?.locale),
                            ctx.getString(R.string.tts_locale), false, {}
                        ) { index ->
                            stopTTS()
                            val lang = languages[index] ?: defaultTTSLanguage
                            setKey(EPUB_LANG, lang.displayName)
                            tts.language = lang
                        }
                    }
                }
            }

            binding.readVoice.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    requireTTS { tts ->
                        val matchAgainst = tts.voice.locale.language
                        val voices = mutableListOf<Voice?>(null).apply {
                            addAll(tts.voices.filter { it != null && it.locale.language == matchAgainst })
                        }

                        ctx.showBottomDialog(
                            voices.map { it?.name ?: ctx.getString(R.string.default_text) },
                            voices.indexOf(tts.voice),
                            ctx.getString(R.string.tts_locale), false, {}
                        ) { index ->
                            stopTTS()
                            setKey(EPUB_VOICE, voices[index]?.name)
                            tts.voice = voices[index] ?: tts.defaultVoice
                        }
                    }
                }
            }

            //val root = bottomSheetDialog.findViewById<LinearLayout>(R.id.read_settings_root)!!
            val horizontalColors =
                bottomSheetDialog.findViewById<LinearLayout>(R.id.read_settings_colors)!!

            binding.readShowFonts.apply {
                text = UIHelper.parseFontFileName(getKey(EPUB_FONT))
                setOnClickListener {
                    showFonts {
                        text = it
                    }
                }
            }

            binding.readSettingsScrollVol.apply {
                isChecked = scrollWithVol
                setOnCheckedChangeListener { _, checked ->
                    setScrollWithVol(checked)
                }
            }

            binding.readSettingsLockTts.apply {
                isChecked = lockTTS
                setOnCheckedChangeListener { _, checked ->
                    setLockTTS(checked)
                }
            }

            binding.readSettingsTwelveHourTime.apply {
                isChecked = updateTwelveHourTime()
                setOnCheckedChangeListener { _, checked ->
                    updateTwelveHourTime(checked)
                }
            }

            binding.readSettingsShowTime.apply {
                isChecked = updateHasTime()
                setOnCheckedChangeListener { _, checked ->
                    updateHasTime(checked)
                }
            }

            binding.readSettingsShowBattery.apply {
                isChecked = updateHasBattery()
                setOnCheckedChangeListener { _, checked ->
                    updateHasBattery(checked)
                }
            }

            binding.readSettingsKeepScreenActive.apply {
                isChecked = updateKeepScreen()
                setOnCheckedChangeListener { _, checked ->
                    updateKeepScreen(checked)
                }
            }

            val bgColors = resources.getIntArray(R.array.readerBgColors)
            val textColors = resources.getIntArray(R.array.readerTextColors)

            ReadActivity.images = java.util.ArrayList()

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
                ReadActivity.images.add(image)
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

            ReadActivity.images.add(image)
            horizontalColors.addView(imageHolder)
            updateImages()

            var updateAllTextOnDismiss = false
            val offsetSize = 10
            binding.readSettingsTextSize.apply {
                max = 20
                progress = getTextFontSize() - offsetSize
                setOnSeekBarChangeListener(object :
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
            }

            binding.readSettingsTextPadding.apply {
                max = 50
                progress = getTextPadding()
                setOnSeekBarChangeListener(object :
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
            }

            binding.readSettingsTextPaddingTop.apply {
                max = 50
                progress = getTextPaddingTop()
                setOnSeekBarChangeListener(object :
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
            }

            binding.readSettingsTextPaddingTextTop.setOnClickListener {
                it.popupMenu(
                    items = listOf(Pair(1, R.string.reset_value)),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        it.context?.removeKey(EPUB_TEXT_PADDING_TOP)
                        binding.readSettingsTextPaddingTop.progress = getTextPaddingTop()
                    }
                }
            }


            binding.readSettingsTextPaddingText.apply {
                setOnClickListener {
                    it.popupMenu(
                        items = listOf(Pair(1, R.string.reset_value)),
                        selectedItemId = null
                    ) {
                        if (itemId == 1) {
                            it.context?.removeKey(EPUB_TEXT_PADDING)
                            binding.readSettingsTextPadding.progress = getTextPadding()
                        }
                    }
                }
            }

            binding.readSettingsTextSizeText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        it.context?.removeKey(EPUB_TEXT_SIZE)
                        binding.readSettingsTextSize.progress = getTextFontSize() - offsetSize
                    }
                }
            }


            binding.readSettingsTextFontText.setOnClickListener {
                it.popupMenu(items = listOf(Pair(1, R.string.reset_value)), selectedItemId = null) {
                    if (itemId == 1) {
                        setReadTextFont(null) { fileName ->
                            binding.readShowFonts.text = fileName
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
            }*/
            bottomSheetDialog.show()
        }

    }
}