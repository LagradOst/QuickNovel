package com.lagradost.quicknovel.ui.result

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity.Companion.backPressed
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.databinding.FragmentResultBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.debugException
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import android.annotation.SuppressLint as SuppressLint1

const val MAX_SYNO_LENGH = 300

class ResultFragment : Fragment() {
    lateinit var binding: FragmentResultBinding
    private val viewModel: ResultViewModel by viewModels()

    fun newInstance(url: String, apiName: String, startAction: Int = 0) =
        ResultFragment().apply {
            arguments = Bundle().apply {
                //println(data)
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
            }
        }

    //private lateinit var viewModel: ResultViewModel

    val api get() = viewModel.api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentResultBinding.inflate(inflater)
        return binding.root
        //viewModel =
        //    ViewModelProvider(this).get(ResultViewModel::class.java)
        /*activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )*/

        //return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onResume() {
        super.onResume()

        activity?.apply {
            window?.navigationBarColor =
                colorFromAttribute(R.attr.primaryBlackBackground)
        }
    }

    private fun updateScrollHeight() {
        val displayMetrics = context?.resources?.displayMetrics ?: return
        val height = binding.resultDownloadCard.height
        val total = displayMetrics.heightPixels - height

        binding.resultNovelHolder.apply {
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                maxOf(0, total)
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.resultHolder.post { // BUG FIX
            updateScrollHeight()
        }
    }

    fun newState(loadResponse: Resource<LoadResponse>?) {
        if (loadResponse == null) return
        //activity?.window?.navigationBarColor =
        //    requireContext().colorFromAttribute(R.attr.bitDarkerGrayBackground)

        when (loadResponse) {
            is Resource.Failure -> {
                binding.apply {
                    resultLoading.isVisible = false
                    resultLoadingError.isVisible = true
                    resultHolder.isVisible = false
                    resultErrorText.text = loadResponse.errorString
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Loading -> {
                binding.apply {
                    resultLoading.isVisible = true
                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = false
                    resultPosterBlur.isVisible = false
                }
            }

            is Resource.Success -> {
                val res = loadResponse.value

                binding.apply {
                    downloadWarning.isVisible = api.rateLimitTime > 1000

                    resultPoster.setImage(res.image)
                    resultPosterBlur.setImage(res.image, radius = 100, sample = 3)

                    resultTitle.text = res.name
                    resultAuthor.text = res.author ?: getString(R.string.no_author)

                    resultRatingVotedCount.text = getString(R.string.no_data)
                    res.rating?.let { rating ->
                        resultRating.text = context?.getRating(rating)
                        val votes = res.peopleVoted
                        if (votes != null) {
                            resultRatingVotedCount.text =
                                getString(R.string.votes_format).format(votes)
                        }
                    }
                    resultViews.text =
                        res.views?.let { views -> humanReadableByteCountSI(views) }
                            ?: getString(R.string.no_data)

                    resultBack.setColorFilter(Color.WHITE)
                    resultTabs.removeAllTabs()
                    resultTabs.isVisible = api.hasReviews
                    if (api.hasReviews) {
                        resultTabs.addTab(resultTabs.newTab().setText(R.string.novel))
                        resultTabs.addTab(resultTabs.newTab().setText(R.string.reviews))
                    }

                    viewsAndRating.isVisible = res.views != null || res.peopleVoted != null

                    resultStatus.text = when (res.status) {
                        1 -> getString(R.string.ongoing)
                        2 -> getString(R.string.completed)
                        3 -> getString(R.string.paused)
                        4 -> getString(R.string.dropped)
                        else -> ""
                    }

                    resultTag.removeAllViews()
                    if (res.tags == null && ((res.status ?: 0) <= 0)) {
                        resultTagHolder.isVisible = false
                    } else {
                        resultTagHolder.isGone = res.tags.isNullOrEmpty()
                        resultTag.apply {

                            val map =
                                api.tags.mapIndexed { i, (value, _) -> value to i }.associate { it }

                            res.tags?.forEach { tag ->
                                val chip = Chip(context)
                                val chipDrawable = ChipDrawable.createFromAttributes(
                                    context,
                                    null,
                                    0,
                                    R.style.ChipFilled
                                )
                                chip.setChipDrawable(chipDrawable)
                                chip.text = tag
                                chip.isChecked = false
                                chip.isCheckable = false
                                chip.isFocusable = false
                                chip.isClickable = false

                                map[tag]?.let { index ->
                                    chip.isClickable = true
                                    chip.setOnClickListener {
                                        activity?.supportFragmentManager?.beginTransaction()
                                            ?.setCustomAnimations(
                                                R.anim.enter_anim,
                                                R.anim.exit_anim,
                                                R.anim.pop_enter,
                                                R.anim.pop_exit
                                            )
                                            ?.add(
                                                R.id.homeRoot,
                                                MainPageFragment().newInstance(
                                                    api.name,
                                                    tag = index
                                                )
                                            )
                                            ?.commit()
                                    }
                                }

                                chip.setTextColor(context.colorFromAttribute(R.attr.textColor))
                                addView(chip)
                            }
                        }
                    }
                    res.synopsis?.let { synopsis ->
                        val syno = if (synopsis.length > MAX_SYNO_LENGH) {
                            synopsis.substring(0, MAX_SYNO_LENGH) + "..."
                        } else {
                            synopsis
                        }

                        resultSynopsisText.setOnClickListener {
                            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                            builder.setMessage(res.synopsis.html()).setTitle(R.string.synopsis)
                                .show()
                        }
                        resultSynopsisText.text = syno.html()
                    } ?: run {
                        resultSynopsisText.text = "..."
                    }

                    if (res is StreamResponse) {
                        resultQuickstream.isVisible = true
                        resultTotalChapters.isVisible = true
                        if (res.data.isNotEmpty()) {
                            resultTotalChapters.text =
                                getString(R.string.latest_format).format(res.data.last().name)
                        } else {
                            resultTotalChapters.text = getString(R.string.no_chapters)
                        }
                    } else {
                        resultTotalChapters.isVisible = false
                        resultQuickstream.isVisible = false
                    }

                    resultLoading.isVisible = false
                    resultLoadingError.isVisible = false
                    resultHolder.isVisible = true
                    resultPosterBlur.isVisible = true
                    resultHolder.post {
                        updateScrollHeight()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString("url") ?: throw NotImplementedError()
        val apiName = arguments?.getString("apiName") ?: throw NotImplementedError()

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()

        if (viewModel.loadResponse.value == null)
            viewModel.initState(apiName, url)

        binding.apply {
            activity?.fixPaddingStatusbar(resultInfoHeader)

            resultOpeninbrowerText.text = apiName //""// resultUrl

            resultReloadConnectionerror.setOnClickListener {
                viewModel.initState(apiName, url)
            }

            resultOpeninbrower.setOnClickListener {
                viewModel.openInBrowser()
            }

            reviewsFab.setOnClickListener {
                resultReviews.smoothScrollToPosition(0) // NEEDS THIS TO RESET VELOCITY
                resultMainscroll.smoothScrollTo(0, 0)
            }

            resultOpeninbrower.setOnClickListener {
                viewModel.openInBrowser()
            }


            val backParameter = resultBack.layoutParams as CoordinatorLayout.LayoutParams
            backParameter.setMargins(
                backParameter.leftMargin,
                backParameter.topMargin + (activity?.getStatusBarHeight() ?: 0),
                backParameter.rightMargin,
                backParameter.bottomMargin
            )
            resultBack.layoutParams = backParameter

            resultBack.setOnClickListener {
                (activity as? AppCompatActivity)?.backPressed()
            }

            val parameter = resultEmptyView.layoutParams as LinearLayout.LayoutParams
            parameter.setMargins(
                parameter.leftMargin,
                parameter.topMargin + (activity?.getStatusBarHeight() ?: 0),
                parameter.rightMargin,
                parameter.bottomMargin
            )
            resultEmptyView.layoutParams = parameter

            resultShare.setOnClickListener {
                viewModel.share()
            }

            val reviewAdapter = ReviewAdapter2()

            resultReviews.adapter = reviewAdapter
            resultReviews.layoutManager = GridLayoutManager(context, 1)

            observe(viewModel.reviews) { reviews ->
                when (reviews) {
                    is Resource.Success -> {
                        resultviewReviewsLoading.isVisible = false
                        resultviewReviewsLoadingShimmer.startShimmer()
                        resultReviews.isVisible = true
                        // fuck jvm, we have to do a copy because otherwise it wont fucking register
                        reviewAdapter.submitList(reviews.value.map { it.copy() })
                    }

                    is Resource.Loading -> {
                        resultviewReviewsLoadingShimmer.stopShimmer()
                        resultviewReviewsLoading.isVisible = true
                        resultReviews.isVisible = false
                    }

                    is Resource.Failure -> {
                        debugException { "This should never happened" }
                    }
                }
            }

            resultTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    viewModel.switchTab(tab?.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            resultBookmark.setOnClickListener { view ->
                view.popupMenu(
                    ReadType.values().map { Pair(it.prefValue, it.stringRes) },
                    selectedItemId = viewModel.readState.value?.prefValue
                ) {
                    viewModel.bookmark(itemId)
                }
            }

            resultDownloadGenerateEpub.setOnClickListener {
                viewModel.readEpub()
            }

            resultDownloadBtt.setOnClickListener {
                viewModel.downloadOrPause()
            }

            resultQuickstream.setOnClickListener {
                viewModel.streamRead()
            }

            downloadDeleteTrashFromResult.setOnClickListener {
                viewModel.deleteAlert()
            }
        }
        observe(viewModel.currentTabIndex) { pos ->
            binding.apply {
                resultNovelHolder.isVisible = 0 == pos
                resultReviewsholder.isVisible = 1 == pos
                reviewsFab.isVisible = 1 == pos
            }
        }
        observe(viewModel.readState) {
            binding.resultBookmark.setImageResource(if (it == ReadType.NONE) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24)
        }
        observe(viewModel.loadResponse, ::newState)

        observe(viewModel.downloadState) { progressState ->
            if (progressState != null) {
                val hasDownload = progressState.progress > 0

                binding.downloadDeleteTrashFromResult.apply {
                    isVisible = hasDownload
                    isClickable = hasDownload
                }
                binding.resultDownloadProgressText.text =
                    "${progressState.progress}/${progressState.total}"

                binding.resultDownloadProgressBar.apply {
                    max = progressState.total * 100

                    val animation: ObjectAnimator = ObjectAnimator.ofInt(
                        this,
                        "progress",
                        this.progress,
                        progressState.progress * 100
                    )
                    animation.duration = 500
                    animation.setAutoCancel(true)
                    animation.interpolator = DecelerateInterpolator()
                    animation.start()
                }

                val ePubGeneration = progressState.progress > 0
                binding.resultDownloadGenerateEpub.apply {
                    isClickable = ePubGeneration
                    alpha = if (ePubGeneration) 1f else 0.5f
                }

                val download = progressState.progress < progressState.total

                binding.resultDownloadBtt.apply {
                    isClickable = download
                    alpha = if (download) 1f else 0.5f

                    //iconSize = 30.toPx
                    text = when (progressState.state) {
                        DownloadState.IsDone -> getString(R.string.downloaded)
                        DownloadState.IsDownloading -> getString(R.string.pause)
                        DownloadState.IsPaused -> getString(R.string.resume)
                        DownloadState.IsFailed -> getString(R.string.re_downloaded)
                        DownloadState.IsStopped -> getString(R.string.downloaded)
                        DownloadState.Nothing -> getString(R.string.download)
                        DownloadState.IsPending -> getString(R.string.loading)
                    }
                    setIconResource(
                        when (progressState.state) {
                            DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                            DownloadState.IsPaused -> R.drawable.netflix_play
                            DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsDone -> R.drawable.ic_baseline_check_24
                            else -> R.drawable.netflix_download
                        }
                    )
                }
            } else {
                binding.downloadDeleteTrashFromResult.isVisible = false

            }
        }


        //result_container.setBackgroundColor(requireContext().colorFromAttribute(R.attr.bitDarkerGrayBackground))

        binding.resultMainscroll.setOnScrollChangeListener { v: NestedScrollView, _, scrollY, _, oldScrollY ->
            if (viewModel.isInReviews()) {
                binding.reviewsFab.alpha = scrollY / 50.toPx.toFloat()
            }

            val scrollFade = maxOf(0f, 1 - scrollY / 170.toPx.toFloat())
            binding.resultInfoHeader.apply {
                alpha = scrollFade
                scaleX = 0.95f + scrollFade * 0.05f
                scaleY = 0.95f + scrollFade * 0.05f
            }

            val crossFade = maxOf(0f, 1 - scrollY / 140.toPx.toFloat())
            binding.resultBack.apply {
                alpha = crossFade
                isEnabled = crossFade > 0
            }

            //REVIEWS
            val dy = scrollY - oldScrollY
            if (dy > 0) { //check for scroll down
                val max = (v.getChildAt(0).measuredHeight - v.measuredHeight)
                if (scrollY >= max) {
                    viewModel.loadMoreReviews()
                }
            }
        }
    }
}
