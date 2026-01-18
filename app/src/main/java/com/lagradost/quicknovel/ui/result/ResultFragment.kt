package com.lagradost.quicknovel.ui.result

import android.animation.ObjectAnimator
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.databinding.ChapterDialogBinding
import com.lagradost.quicknovel.databinding.ChapterFilterPopupBinding
import com.lagradost.quicknovel.databinding.FragmentResultBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.debugException
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.SortingMethodAdapter
import com.lagradost.quicknovel.ui.mainpage.MainAdapter2
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx

const val MAX_SYNO_LENGH = 300

class ResultFragment : Fragment() {
    lateinit var binding: FragmentResultBinding
    private val viewModel: ResultViewModel by viewModels()

    companion object {
        fun newInstance(url: String, apiName: String, startAction: Int = 0): Bundle =
            Bundle().apply {
                //println(data)
                putString("url", url)
                putString("apiName", apiName)
                putInt("startAction", startAction)
            }

    }


    //private lateinit var viewModel: ResultViewModel

    val repo get() = viewModel.repo

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

    private fun setupGridView() {
        val compactView = false //activity?.getGridIsCompact() ?: return
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.relatedList.spanCount = spanCountLandscape
        } else {
            binding.relatedList.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
        binding.resultHolder.post { // BUG FIX
            updateScrollHeight()
        }
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
        val parameter = binding.chapterList.layoutParams
        parameter.height =
            displayMetrics.heightPixels - binding.viewsAndRating.height - binding.resultTabs.height - binding.resultScrollPadding.paddingTop

        binding.chapterList.layoutParams = parameter
        //ViewGroup.LayoutParams(binding.chapterList.layoutParams.width,displayMetrics.heightPixels)
        /*binding.hiddenView.apply {
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                maxOf(0, total)
            )
        }*/
    }


    private fun newState(loadResponse: Resource<LoadResponse>?) {
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
                    downloadWarning.isVisible = (repo?.rateLimitTime ?: 0) > 2000

                    res.image?.let { img ->
                        resultEmptyView.setOnClickListener {
                            UIHelper.showImage(it.context, img)
                        }
                    }

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
                    resultTabs.isVisible = false
                    val hasRelated = !res.related.isNullOrEmpty()
                    val hasChapters =
                        res is StreamResponse && res.data.isNotEmpty() // this was removed because of lag, because of shitty android
                    val api = repo

                    if (api != null && (api.hasReviews || hasRelated || hasChapters)) {
                        resultTabs.isVisible = true
                        resultTabs.addTab(resultTabs.newTab().setText(R.string.novel).setId(0))
                        if (api.hasReviews) {
                            resultTabs.addTab(
                                resultTabs.newTab().setText(R.string.reviews).setId(1)
                            )
                        }
                        if (hasRelated) {
                            resultTabs.addTab(
                                resultTabs.newTab().setText(R.string.related).setId(2)
                            )
                            relatedList.apply {
                                val mainPageAdapter = MainAdapter2(this, 0)
                                adapter = mainPageAdapter
                                mainPageAdapter.submitList(res.related)
                            }
                            setupGridView()
                        }
                        if (hasChapters) {
                            resultTabs.addTab(
                                resultTabs.newTab().setText(R.string.read_action_chapters).setId(3)
                            )
                        }
                    }
                    val target = viewModel.currentTabIndex.value
                    if (target != null) {
                        resultTabs.getTabAt(target)?.let { new ->
                            resultTabs.selectTab(new)
                        }
                    }


                    viewsAndRating.isVisible = res.views != null || res.peopleVoted != null

                    resultStatus.text = res.status?.resource?.let { getString(it) } ?: ""

                    resultTag.removeAllViews()
                    if (res.tags == null && res.status == null) {
                        resultTagHolder.isVisible = false
                    } else {
                        resultTagHolder.isGone = res.tags.isNullOrEmpty()
                        resultTag.apply {

                            val map =
                                api?.tags?.mapIndexed { i, (value, _) -> value to i }
                                    ?.associate { it } ?: emptyMap()

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
                                        val api = repo
                                        if (api != null)
                                            activity?.navigate(
                                                R.id.global_to_navigation_mainpage,
                                                MainPageFragment.newInstance(
                                                    api.name,
                                                    tag = index
                                                )
                                            )
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

                        var isExpanded = false
                        resultSynopsisText.setOnClickListener {
                            isExpanded = !isExpanded
                            resultSynopsisText.text =
                                if (isExpanded) res.synopsis.html() else syno.html()
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

    private fun doAction(action: Int) {
        when (action) {
            R.string.resume -> {
                viewModel.download()
            }

            R.string.download -> {
                viewModel.downloadFrom(null)
            }

            R.string.re_downloaded -> {
                viewModel.download()
            }

            R.string.download_from_chapter -> {
                val chapters =
                    ((viewModel.loadResponse.value as? Resource.Success)?.value as? StreamResponse)?.data
                        ?: return

                val act = CommonActivity.activity ?: return

                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(act, R.style.AlertDialogCustom)

                val binding = ChapterDialogBinding.inflate(layoutInflater, null, false)
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                viewModel.downloadFrom(
                                    binding.chapterEdit.text?.toString()?.toIntOrNull()
                                )
                            }

                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }

                builder.setView(binding.root)
                    .setTitle(R.string.download_from_chapter)
                    .setPositiveButton(R.string.download, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show()

                binding.chapterEdit.doOnTextChanged { text, _, _, _ ->
                    val parsedInt = text?.toString()?.toIntOrNull()
                    if (parsedInt == null || parsedInt < 0 || parsedInt >= chapters.size) {
                        binding.chapterEdit.error = act.getString(R.string.error_outside_chapter)
                    } else {
                        binding.chapterEdit.error = null
                    }
                }
            }

            R.string.delete -> {
                viewModel.deleteAlert()
            }

            R.string.pause -> {
                viewModel.pause()
            }

            R.string.stop -> {
                viewModel.stop()
            }
        }
    }

    private fun getActions(): List<Int>? {
        val items = mutableListOf<Int>()
        val progressState =
            viewModel.downloadState.value ?: return null
        val canDownload =
            progressState.progress < progressState.total
        val canPartialDownload =
            progressState.downloaded < progressState.total && progressState.total > 1

        when (progressState.state) {
            DownloadState.IsPaused -> {
                items.add(R.string.resume)
                items.add(R.string.stop)
            }

            DownloadState.IsDownloading -> {
                items.add(R.string.pause)
                items.add(R.string.stop)
            }

            DownloadState.IsDone -> {
                if (canPartialDownload) {
                    items.add(R.string.download_from_chapter)
                }
            }

            DownloadState.IsPending -> {
            }

            DownloadState.IsFailed, DownloadState.IsStopped -> {
                if (canDownload) {
                    items.add(R.string.re_downloaded)
                }

                if (canPartialDownload) {
                    items.add(R.string.download_from_chapter)
                }
            }

            DownloadState.Nothing -> {
                if (canDownload) {
                    items.add(R.string.download)
                }

                if (canPartialDownload) {
                    items.add(R.string.download_from_chapter)
                }
            }
        }

        if (progressState.progress > 0) {
            items.add(R.string.delete)
        }
        return items
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val url = savedInstanceState?.getString("url") ?: arguments?.getString("url")
        ?: throw NotImplementedError()
        val apiName = savedInstanceState?.getString("apiName") ?: arguments?.getString("apiName")
        ?: throw NotImplementedError()

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

            resultReloadConnectionOpenInBrowser.setOnClickListener {
                viewModel.openInBrowser()
            }

            reviewsFab.setOnClickListener {
                resultReviews.smoothScrollToPosition(0) // NEEDS THIS TO RESET VELOCITY
                resultMainscroll.smoothScrollTo(0, 0)
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
                activity?.onBackPressedDispatcher?.onBackPressed()
                //activity?.onBackPressed()
                //    (activity as? AppCompatActivity)?.backPressed()
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
                    viewModel.switchTab(tab?.id, resultTabs.selectedTabPosition)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            resultBookmark.setOnClickListener { view ->
                view.popupMenu(
                    ReadType.entries.map { it.prefValue to it.stringRes },
                    selectedItemId = viewModel.readState.value?.prefValue
                ) {
                    viewModel.bookmark(itemId)
                }
            }

            resultDownloadGenerateEpub.setOnClickListener {
                viewModel.readEpub()
            }

            resultDownloadBtt.setOnLongClickListener { v ->
                val items = getActions() ?: return@setOnLongClickListener true
                v.popupMenu(items.map { it to it }, null) {
                    doAction(itemId)
                }
                return@setOnLongClickListener true
            }

            resultDownloadBtt.setOnClickListener { v ->
                val actions = getActions()
                if (actions == null) {
                    viewModel.downloadOrPause()
                    return@setOnClickListener
                }
                if (actions.size == 1) {
                    doAction(actions[0])
                } else if (actions.contains(R.string.download) || actions.contains(R.string.pause)) {
                    viewModel.downloadOrPause()
                } else {
                    v.popupMenu(actions.map { it to it }, null) {
                        doAction(itemId)
                    }
                }
            }

            resultQuickstream.setOnClickListener {
                viewModel.streamRead()
            }

            /*downloadDeleteTrashFromResult.setOnClickListener {
                viewModel.deleteAlert()
            }*/
        }

        observe(viewModel.currentTabIndex) { pos ->
            binding.apply {
                resultNovelHolder.isVisible = 0 == pos
                // hiddenView.isGone = 0 == pos
                resultReviewsholder.isVisible = 1 == pos
                reviewsFab.isVisible = 1 == pos
                chaptersFab.isVisible = 3 == pos
                resultRelatedholder.isVisible = 2 == pos
                resultChapterholder.isVisible = 3 == pos
            }
        }

        observe(viewModel.currentTabPosition) { pos ->
            if (binding.resultTabs.selectedTabPosition != pos) {
                binding.resultTabs.selectTab(binding.resultTabs.getTabAt(pos))
            }
        }

        observe(viewModel.readState) {
            binding.resultBookmark.setImageResource(if (it == ReadType.NONE) R.drawable.ic_baseline_bookmark_border_24 else R.drawable.ic_baseline_bookmark_24)
        }
        observe(viewModel.loadResponse, ::newState)


        binding.chapterList.apply {
            val mainPageAdapter = ChapterAdapter(viewModel)
            adapter = mainPageAdapter
            setHasFixedSize(true)
        }

        observeNullable(viewModel.chapters) { chapters ->
            (binding.chapterList.adapter as? ChapterAdapter)?.let { adapter ->
                if (chapters == null || chapters.size > 300) {
                    // if we have too many it takes a long time to diff
                    adapter.submitIncomparableList(chapters)
                } else {
                    adapter.submitList(chapters)
                }
            }
        }

        binding.chaptersFab.setOnClickListener { _ ->
            val act = activity ?: return@setOnClickListener
            val bottomSheetDialog = BottomSheetDialog(act)
            val binding = ChapterFilterPopupBinding.inflate(act.layoutInflater, null, false)
            bottomSheetDialog.setContentView(binding.root)

            val filterTab = binding.filterTabs.newTab().setText(getString(R.string.mainpage_filter))
            val sortTab = binding.filterTabs.newTab()
                .setText(getString(R.string.mainpage_sort_by_button_text))
            binding.filterTabs.addTab(filterTab)
            binding.filterTabs.addTab(sortTab)
            binding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val position = tab?.position
                    binding.filterContent.isVisible = position == 0
                    binding.sortContent.isVisible = position == 1
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                override fun onTabReselected(tab: TabLayout.Tab?) = Unit
            })
            binding.filterTabs.selectTab(filterTab)

            binding.filterBookmarked.isChecked = ResultViewModel.filterChapterByBookmarked
            binding.filterRead.isChecked = ResultViewModel.filterChapterByRead
            binding.filterUnread.isChecked = ResultViewModel.filterChapterByUnread
            binding.filterDownloaded.isChecked = ResultViewModel.filterChapterByDownloads

            binding.filterBookmarked.setOnCheckedChangeListener { _, isChecked ->
                ResultViewModel.filterChapterByBookmarked = isChecked
                viewModel.reorderChapters()
            }

            binding.filterRead.setOnCheckedChangeListener { _, isChecked ->
                ResultViewModel.filterChapterByRead = isChecked
                viewModel.reorderChapters()
            }

            binding.filterUnread.setOnCheckedChangeListener { _, isChecked ->
                ResultViewModel.filterChapterByUnread = isChecked
                viewModel.reorderChapters()
            }

            binding.filterDownloaded.setOnCheckedChangeListener { _, isChecked ->
                ResultViewModel.filterChapterByDownloads = isChecked
                viewModel.reorderChapters()
            }

            val adapter =
                SortingMethodAdapter(ResultViewModel.sortChapterBy) { item, position, newId ->
                    binding.sortContent.adapter?.notifyItemRangeChanged(
                        0,
                        ResultViewModel.chapterSortingMethods.size
                    )
                    ResultViewModel.sortChapterBy = newId
                    viewModel.reorderChapters()
                }
            adapter.submitList(ResultViewModel.chapterSortingMethods.toList())
            binding.sortContent.adapter = adapter
            bottomSheetDialog.show()
        }

        observe(viewModel.downloadState) { progressState ->
            if (progressState == null) {
                //binding.downloadDeleteTrashFromResult.isVisible = false
                return@observe
            }
            //val hasDownload = progressState.progress > 0

            /*binding.downloadDeleteTrashFromResult.apply {
                isVisible = hasDownload
                isClickable = hasDownload
            }*/
            binding.resultDownloadProgressText.text =
                "${progressState.progress}/${progressState.total}"

            binding.resultDownloadProgressBarNotDownloaded.apply {
                println("progressState: ${progressState}")
                max = progressState.total.toInt() * 100
                val animation: ObjectAnimator = ObjectAnimator.ofInt(
                    this,
                    "progress",
                    this.progress,
                    (progressState.progress - progressState.downloaded).toInt() * 100
                )
                animation.duration = 500
                animation.setAutoCancel(true)
                animation.interpolator = DecelerateInterpolator()
                animation.start()
            }

            binding.resultDownloadProgressBar.apply {
                max = progressState.total.toInt() * 100

                val animation: ObjectAnimator = ObjectAnimator.ofInt(
                    this,
                    "progress",
                    this.progress,
                    progressState.progress.toInt() * 100
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

            val canDownload =
                progressState.progress < progressState.total

            val canClick = progressState.total > 0
            binding.resultDownloadBtt.apply {
                isClickable = canClick
                alpha = if (canClick) 1f else 0.5f

                //iconSize = 30.toPx
                setText(
                    when (progressState.state) {
                        DownloadState.IsDone -> R.string.manage
                        DownloadState.IsDownloading -> R.string.pause
                        DownloadState.IsPaused -> R.string.resume
                        DownloadState.IsFailed -> R.string.re_downloaded
                        DownloadState.IsStopped -> R.string.downloaded
                        DownloadState.Nothing -> if (canDownload) {
                            R.string.download
                        } else {
                            R.string.manage
                        }

                        DownloadState.IsPending -> R.string.loading
                    }
                )
                setIconResource(
                    when (progressState.state) {
                        DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                        DownloadState.IsPaused -> R.drawable.netflix_play
                        DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                        DownloadState.IsDone -> R.drawable.ic_outline_settings_24
                        DownloadState.Nothing -> if (canDownload) {
                            R.drawable.netflix_download
                        } else {
                            R.drawable.ic_outline_settings_24
                        }

                        else -> R.drawable.netflix_download
                    }
                )
            }
        }


        //result_container.setBackgroundColor(requireContext().colorFromAttribute(R.attr.bitDarkerGrayBackground))

        binding.resultMainscroll.setOnScrollChangeListener { v: NestedScrollView, _, scrollY, _, oldScrollY ->
            val arr = IntArray(2)
            binding.resultScrollPadding.getLocationOnScreen(arr)
            /*context?.resources?.displayMetrics?.let { displayMetrics->
                binding.scrollHolder.setPadding(0, (displayMetrics.heightPixels - arr[1]),0,0)
                println("scrolled: ${binding.scrollHolder.paddingTop}")

            }*/
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