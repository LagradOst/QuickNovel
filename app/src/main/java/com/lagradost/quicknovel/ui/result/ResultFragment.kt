package com.lagradost.quicknovel.ui.result

import android.animation.ObjectAnimator
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat.getColor
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.createQuickStream
import com.lagradost.quicknovel.BookDownloader.hasEpub
import com.lagradost.quicknovel.BookDownloader.openEpub
import com.lagradost.quicknovel.BookDownloader.openQuickStream
import com.lagradost.quicknovel.BookDownloader.remove
import com.lagradost.quicknovel.BookDownloader.turnToEpub
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.MainActivity.Companion.backPressed
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.DownloadHelper
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.ui.search.SearchViewModel
import com.lagradost.quicknovel.util.Coroutines
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.toPx
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import android.annotation.SuppressLint as SuppressLint1

const val MAX_SYNO_LENGH = 300

class ResultFragment : Fragment() {
    fun newInstance(url: String, apiName: String, startAction: Int = 0) =
        ResultFragment().apply {
            arguments = Bundle().apply {
                //println(data)
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
            }
        }

    private lateinit var viewModel: ResultViewModel

    val api get() = viewModel.api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        viewModel =
            ViewModelProvider(this).get(ResultViewModel::class.java)
        /*activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )*/

        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    lateinit var apiName: String
    lateinit var url: String
    private var tid: Int = -1
    private var readState = ReadType.NONE

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("url")?.let {
            url = it
        }
        arguments?.getString("apiName")?.let {
            apiName = it
        }
    }

    private fun getReviews(data: ArrayList<UserReview>) {
        (result_reviews.adapter as ReviewAdapter).cardList = data
        (result_reviews.adapter as ReviewAdapter).notifyDataSetChanged()
        isLoadingReviews = false
    }

    private var isLoadingReviews = false
    private var isInReviews = false
    private fun loadReviews() {
        if (isLoadingReviews) return
        isLoadingReviews = true
        viewModel.loadMoreReviews(url)
    }

    private var generateEpub = false
    private var lastProgress: Int = 0

    @android.annotation.SuppressLint("SetTextI18n")
    private fun Context.updateGenerateBtt(progress: Int?) {
        if (viewModel.loadResponse.value != null) {
            generateEpub =
                getKey(DOWNLOAD_EPUB_SIZE, viewModel.id.value.toString(), 0) != (progress
                    ?: lastProgress)
            if (progress != null) lastProgress = progress
            if (result_download_generate_epub != null) {
                if (generateEpub) {
                    result_download_generate_epub.setIconResource(R.drawable.ic_baseline_create_24)
                    result_download_generate_epub.text = "Generate Epub"
                } else {
                    result_download_generate_epub.setIconResource(R.drawable.ic_baseline_menu_book_24)
                    result_download_generate_epub.text = "Read Epub"
                }
            }
        }
    }

    @SuppressLint1("SetTextI18n")
    fun updateDownloadInfo(info: BookDownloader.DownloadNotification?) {
        if (info == null) return
        if (result_download_progress_text != null) {
            val hasDownload = info.progress > 0
            download_delete_trash_from_result.visibility =
                if (hasDownload) View.VISIBLE else View.GONE
            download_delete_trash_from_result.isClickable = hasDownload

            result_download_progress_text.text = "${info.progress}/${info.total}"
            //  result_download_progress_bar.max = info.total
            // ANIMATION PROGRESSBAR
            result_download_progress_bar.max = info.total * 100

            if (result_download_progress_bar.progress != 0) {
                val animation: ObjectAnimator = ObjectAnimator.ofInt(
                    result_download_progress_bar,
                    "progress",
                    result_download_progress_bar.progress,
                    info.progress * 100
                )

                animation.duration = 500
                animation.setAutoCancel(true)
                animation.interpolator = DecelerateInterpolator()
                animation.start()
            } else {
                result_download_progress_bar.progress = info.progress * 100
            }

            result_download_progress_text_eta.text = info.ETA
            updateDownloadButtons(info.progress, info.total, info.state)
            requireContext().updateGenerateBtt(info.progress)
        }
    }

    private fun updateScrollHeight() {
        if (result_novel_holder == null) return
        val displayMetrics = requireContext().resources.displayMetrics
        val height = result_download_card.height
        val total = displayMetrics.heightPixels - height

        /*
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            displayMetrics.heightPixels
        )

        result_reviewsholder.layoutParams = layoutParams*/
        result_novel_holder.setPadding(
            result_novel_holder.paddingLeft,
            result_novel_holder.paddingTop,
            result_novel_holder.paddingRight,
            maxOf(0, total)
        )// - MainActivity.activity.nav_view.height
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        result_holder.post { // BUG FIX
            updateScrollHeight()
        }
    }

    private fun updateDownloadButtons(
        progress: Int,
        total: Int,
        state: BookDownloader.DownloadType
    ) {
        val ePubGeneration = progress > 0
        if (result_download_generate_epub.isClickable != ePubGeneration) {
            result_download_generate_epub.isClickable = ePubGeneration
            result_download_generate_epub.alpha = if (ePubGeneration) 1f else 0.5f
        }

        var loadlState = state
        val download = progress < total
        if (!download) {
            loadlState = BookDownloader.DownloadType.IsDone
        }
        if (result_download_btt.isClickable != download) {
            result_download_btt.isClickable = download
            result_download_btt.alpha = if (download) 1f else 0.5f
        }

        result_download_btt.text = when (loadlState) {
            BookDownloader.DownloadType.IsDone -> "Downloaded"
            BookDownloader.DownloadType.IsDownloading -> "Pause"
            BookDownloader.DownloadType.IsPaused -> "Resume"
            BookDownloader.DownloadType.IsFailed -> "Re-Download"
            BookDownloader.DownloadType.IsStopped -> "Download"
        }

        result_download_btt.iconSize = 30.toPx
        result_download_btt.setIconResource(
            when (loadlState) {
                BookDownloader.DownloadType.IsDownloading -> R.drawable.ic_baseline_pause_24
                BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
                BookDownloader.DownloadType.IsFailed -> R.drawable.ic_baseline_autorenew_24
                BookDownloader.DownloadType.IsDone -> R.drawable.ic_baseline_check_24
                else -> R.drawable.netflix_download
            }
        )
    }

    @SuppressLint1("CutPasteId", "SetTextI18n")
    fun newState(loadResponse: Resource<LoadResponse>) {
        activity?.window?.navigationBarColor =
            requireContext().colorFromAttribute(R.attr.bitDarkerGrayBackground)

        when (loadResponse) {
            is Resource.Failure -> {
                result_loading.visibility = View.GONE
                result_loading_error.visibility = View.VISIBLE
                result_holder.visibility = View.GONE
                result_error_text.text = loadResponse.errorString
            }
            is Resource.Loading -> {
                result_loading.visibility = View.VISIBLE
                result_loading_error.visibility = View.GONE
                result_holder.visibility = View.GONE
            }
            is Resource.Success -> {
                download_warning?.visibility =
                    if (api.rateLimitTime > 1000) View.VISIBLE else View.GONE

                val res = loadResponse.value

                // LOAD IMAGES FIRST TO GIVE IT A BIT OF TIME
                if (!res.posterUrl.isNullOrEmpty()) {
                    try {
                        val glideUrl =
                            GlideUrl(
                                res.posterUrl,
                                LazyHeaders.Builder().addHeader("Referer", api.mainUrl).build()
                            )
                        requireContext().let {
                            Glide.with(it)
                                .load(glideUrl)
                                .into(result_poster)

                            Glide.with(it)
                                .load(glideUrl)
                                .apply(bitmapTransform(BlurTransformation(100, 3)))
                                .into(result_poster_blur)
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                // SET TEXT
                result_title.text = res.name
                result_author.text = res.author ?: getString(R.string.no_author)

                result_openinbrower_text.text = apiName //""// resultUrl

                result_openinbrower.setOnClickListener {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    startActivity(i)
                }

                result_share.setOnClickListener {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_SUBJECT, res.name)
                    i.putExtra(Intent.EXTRA_TEXT, url)
                    startActivity(Intent.createChooser(i, res.name))
                }

                result_rating_voted_count.text = getString(R.string.no_data)

                if (res.rating != null) {
                    result_rating.text = requireContext().getRating(res.rating)
                    if (res.peopleVoted != null) {
                        result_rating_voted_count.text = "${res.peopleVoted} Votes"
                    }
                }

                result_views.text =
                    if (res.views != null) humanReadableByteCountSI(res.views) else getString(R.string.no_data)

                result_back.setColorFilter(Color.WHITE)

                // SET TABS
                result_tabs.removeAllTabs()
                result_tabs.visibility = if (api.hasReviews) View.VISIBLE else View.GONE
                if (api.hasReviews) {
                    result_tabs.addTab(result_tabs.newTab().setText("Novel"))
                    result_tabs.addTab(result_tabs.newTab().setText("Reviews"))
                    val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
                        ReviewAdapter(
                            it,
                            ArrayList(),
                            result_reviews,
                        )
                    }
                    result_reviews.adapter = adapter
                    result_reviews.layoutManager = GridLayoutManager(context, 1)
                }

                result_tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        val pos = tab?.position
                        viewModel.currentTabIndex.postValue(pos)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}

                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                })

                result_bookmark.setOnClickListener {
                    result_bookmark.popupMenu(
                        ReadType.values().map { Pair(it.prefValue, it.stringRes) },
                        selectedItemId = readState.prefValue
                    ) {
                        context?.let { ctx ->
                            ctx.setKey(
                                RESULT_BOOKMARK_STATE,
                                tid.toString(), itemId
                            )

                            ctx.setKey(
                                RESULT_BOOKMARK,
                                tid.toString(),
                                ResultCached(
                                    url,
                                    res.name,
                                    apiName,
                                    tid,
                                    res.author,
                                    res.posterUrl,
                                    res.tags,
                                    res.rating,
                                    res.data.size,
                                    System.currentTimeMillis()
                                )
                            )
                        }

                        viewModel.readState.postValue(ReadType.fromSpinner(itemId))
                    }
                }

                download_delete_trash_from_result.setOnClickListener {
                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    requireContext().remove(
                                        res.author,
                                        res.name,
                                        apiName
                                    )
                                    val currentValue = viewModel.downloadNotification.value!!

                                    viewModel.downloadNotification.postValue(
                                        BookDownloader.DownloadNotification(
                                            0,
                                            currentValue.total,
                                            currentValue.id,
                                            "",
                                            BookDownloader.DownloadType.IsStopped
                                        )
                                    )
                                }
                                DialogInterface.BUTTON_NEGATIVE -> {
                                }
                            }
                        }
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
                    builder.setMessage("This will permanently delete ${res.name}.\nAre you sure?")
                        .setTitle("Delete")
                        .setPositiveButton("Delete", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener)
                        .show()
                }

                fun addToHistory() {
                    context?.setKey(
                        HISTORY_FOLDER,
                        tid.toString(),
                        ResultCached(
                            url,
                            res.name,
                            apiName,
                            tid,
                            res.author,
                            res.posterUrl,
                            res.tags,
                            res.rating,
                            res.data.size,
                            System.currentTimeMillis()
                        )
                    )
                }

                result_quickstream.setOnClickListener {
                    if (res.data.size <= 0) {
                        Toast.makeText(context, R.string.no_chapters_found, Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }

                    addToHistory()
                    val uri = activity?.createQuickStream(
                        BookDownloader.QuickStreamData(
                            BookDownloader.QuickStreamMetaData(
                                res.author,
                                res.name,
                                api.name
                            ),
                            res.posterUrl,
                            res.data.toMutableList()
                        )
                    )
                    activity?.openQuickStream(uri)
                }


                result_download_generate_epub.setOnClickListener {
                    addToHistory()
                    if (generateEpub) {
                        Coroutines.main {
                            val done = withContext(Dispatchers.IO) {
                                requireActivity().turnToEpub(res.author, res.name, api.name)
                            }
                            context?.let { ctx ->
                                if (result_download_generate_epub != null) {
                                    ctx.updateGenerateBtt(null)
                                }

                                if (done) {
                                    Toast.makeText(ctx, "Created ${res.name}", Toast.LENGTH_LONG)
                                        .show()
                                } else {
                                    Toast.makeText(
                                        ctx,
                                        "Error creating the Epub",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        Coroutines.main {
                            withContext(Dispatchers.IO) {
                                if (!requireActivity().hasEpub(res.name)) {
                                    requireActivity().turnToEpub(res.author, res.name, api.name)
                                }
                            }
                            activity?.openEpub(res.name)
                        }
                    }
                }

                result_download_btt.setOnClickListener {
                    DownloadHelper.updateDownloadFromResult(
                        requireContext(),
                        res,
                        tid,
                        apiName,
                        true
                    )
                }


                // === TAGS ===
                result_status.text = when (res.status) {
                    1 -> "Ongoing"
                    2 -> "Completed"
                    3 -> "Paused"
                    4 -> "Dropped"
                    else -> ""
                } //+ if (res.data.size > 0) res.data.last().name else ""

                result_tag.removeAllViews()
                if (res.tags == null && (res.status == null || res.status <= 0)) {
                    result_tag_holder.visibility = View.GONE
                } else {
                    result_tag_holder.visibility = View.VISIBLE
                    var index = 0

                    /* if (res.status != null && res.status > 0) {


                       val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                       val mat = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                       mat.strokeColor = getColorStateList(requireContext(), R.color.colorOngoing)
                       mat.setTextColor(getColor(requireContext(), R.color.colorOngoing))
                       mat.rippleColor = getColorStateList(requireContext(), R.color.colorOngoing)
                       val status = when (res.status) {
                           1 -> "Ongoing"
                           2 -> "Completed"
                           3 -> "Paused"
                           4 -> "Dropped"
                           else -> "ERROR"
                       }
                       mat.text = status
                       result_tag.addView(viewBtt, index)
                       index++

                       for ((orderindex, apiOrder) in api.orderBys.withIndex()) {
                           if (apiOrder.first == status) {
                               mat.setOnClickListener {
                                   requireActivity().supportFragmentManager.beginTransaction()
                                       .setCustomAnimations(
                                           R.anim.enter_anim,
                                           R.anim.exit_anim,
                                           R.anim.pop_enter,
                                           R.anim.pop_exit)
                                       .add(R.id.homeRoot,
                                           MainPageFragment().newInstance(api.name, orderBy = orderindex))
                                       .commit()
                               }
                               break
                           }
                       }
                    }*/

                    if (res.tags != null) {
                        for (tag in res.tags) {
                            val viewBtt = layoutInflater.inflate(R.layout.result_tag, null)
                            val btt = viewBtt.findViewById<MaterialButton>(R.id.result_tag_card)
                            btt.text = tag

                            for ((tagIndex, apiTag) in api.tags.withIndex()) {
                                if (apiTag.first == tag) {
                                    btt.setOnClickListener {
                                        requireActivity().supportFragmentManager.beginTransaction()
                                            .setCustomAnimations(
                                                R.anim.enter_anim,
                                                R.anim.exit_anim,
                                                R.anim.pop_enter,
                                                R.anim.pop_exit
                                            )
                                            .add(
                                                R.id.homeRoot,
                                                MainPageFragment().newInstance(
                                                    api.name,
                                                    tag = tagIndex
                                                )
                                            )
                                            .commit()
                                    }
                                    break
                                }
                            }

                            result_tag.addView(viewBtt, index)
                            index++
                        }
                    }
                }

                if (res.synopsis != null) {
                    var syno = res.synopsis
                    if (syno.length > MAX_SYNO_LENGH) {
                        syno = syno.substring(0, MAX_SYNO_LENGH) + "..."
                    }
                    result_synopsis_text.setOnClickListener {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                        builder.setMessage(res.synopsis).setTitle("Synopsis")
                            .show()
                    }
                    result_synopsis_text.text = syno
                } else {
                    result_synopsis_text.text = "..."
                }

                if (res.data.size > 0) {
                    val last = res.data.last()
                    result_total_chapters.text = "Latest: " + last.name //+ " " + last.dateOfRelease
                } else {
                    result_total_chapters.text = getString(R.string.no_chapters)
                }

                val localId = viewModel.id.value!!

                requireContext().setKey(DOWNLOAD_TOTAL, localId.toString(), res.data.size)

                /*
                val start = BookDownloader.downloadInfo(res.author, res.name, res.data.size, api.name)
                result_download_progress_text_eta.text = ""
                if (start != null) {
                    updateGenerateBtt(start.progress)
                    result_download_progress_text.text = "${start.progress}/${start.total}"

                    result_download_progress_bar.max = start.total * 100
                    result_download_progress_bar.progress = start.progress * 100
                    val state =
                        if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped
                    updateDownloadButtons(start.progress, start.total, state!!)
                } else {
                    result_download_progress_bar.progress = 0
                }*/

                result_container.setBackgroundColor(
                    getColor(
                        requireContext(),
                        R.color.bitDarkerGrayBackground
                    )
                )

                result_loading.visibility = View.GONE
                result_loading_error.visibility = View.GONE
                result_holder.visibility = View.VISIBLE
                result_holder.post {
                    updateScrollHeight()
                }
            }
        }
    }

    private var currentTabIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()

        if (viewModel.loadResponse.value == null)
            viewModel.initState(requireContext(), apiName, url)

        result_reload_connectionerror.setOnClickListener {
            viewModel.initState(requireContext(), apiName, url)
        }

        result_reload_connection_open_in_browser.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }

        result_container.setBackgroundColor(requireContext().colorFromAttribute(R.attr.bitDarkerGrayBackground))

        observe(viewModel.downloadNotification, ::updateDownloadInfo)
        observe(viewModel.loadResponse, ::newState)
        observe(viewModel.reviews, ::getReviews)
        observe(viewModel.currentTabIndex) {
            currentTabIndex = it
        }

        observe(viewModel.id) {
            tid = it
            context?.setKey(DOWNLOAD_EPUB_LAST_ACCESS, tid.toString(), System.currentTimeMillis())
        }

        observe(viewModel.currentTabIndex) { pos ->
            fun setVis(v: View, lpos: Int) {
                v.visibility = if (lpos == pos) View.VISIBLE else View.GONE
            }
            setVis(result_novel_holder, 0)
            setVis(result_reviewsholder, 1)

            isInReviews = pos == 1
            reviews_fab.visibility = if (isInReviews) View.VISIBLE else View.GONE

            if (pos == 1 && (result_reviews.adapter as ReviewAdapter).cardList.size <= 0) {
                loadReviews()
            }
            if (pos != result_tabs.selectedTabPosition) {
                result_tabs.selectTab(result_tabs.getTabAt(pos))
            }
        }

        reviews_fab.setOnClickListener {
            result_reviews?.smoothScrollToPosition(0) // NEEDS THIS TO RESET VELOCITY
            result_mainscroll?.smoothScrollTo(0, 0)
        }

        result_mainscroll.setOnScrollChangeListener { v: NestedScrollView, _, scrollY, _, oldScrollY ->
            if (result_info_header == null) return@setOnScrollChangeListener // CRASH IF PERFECTLY TIMED

            if (isInReviews) {
                reviews_fab.alpha = scrollY / 50.toPx.toFloat()
            }

            val scrollFade = maxOf(0f, 1 - scrollY / 170.toPx.toFloat())
            result_info_header.alpha = scrollFade
            result_info_header.scaleX = 0.95f + scrollFade * 0.05f
            result_info_header.scaleY = 0.95f + scrollFade * 0.05f

            val crossFade = maxOf(0f, 1 - scrollY / 140.toPx.toFloat())
            result_back.alpha = crossFade
            result_back.isEnabled = crossFade > 0

            //REVIEWS
            val dy = scrollY - oldScrollY
            if (dy > 0) { //check for scroll down
                //TODO OBSERVE
                val max = (v.getChildAt(0).measuredHeight - v.measuredHeight)
                if (currentTabIndex == 1 &&
                    scrollY >= max
                ) {
                    loadReviews()
                }
            }
        }

        activity?.fixPaddingStatusbar(result_info_header)

        val parameter = result_empty_view.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(
            parameter.leftMargin,
            parameter.topMargin + requireActivity().getStatusBarHeight(),
            parameter.rightMargin,
            parameter.bottomMargin
        )
        result_empty_view.layoutParams = parameter

        observe(viewModel.readState) {
            readState = it
            result_bookmark.setImageResource(if (it == ReadType.NONE) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24)
        }

        val backParameter = result_back.layoutParams as CoordinatorLayout.LayoutParams
        backParameter.setMargins(
            backParameter.leftMargin,
            backParameter.topMargin + requireActivity().getStatusBarHeight(),
            backParameter.rightMargin,
            backParameter.bottomMargin
        )
        result_back.layoutParams = backParameter

        result_back.setOnClickListener {
            (requireActivity() as AppCompatActivity).backPressed()
        }
    }
}
