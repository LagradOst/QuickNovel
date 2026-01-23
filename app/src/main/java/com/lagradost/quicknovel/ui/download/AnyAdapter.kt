package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BookDownloader2.preloadPartialImportedPdf
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadImportBinding
import com.lagradost.quicknovel.databinding.DownloadImportCardBinding
import com.lagradost.quicknovel.databinding.DownloadResultCompactBinding
import com.lagradost.quicknovel.databinding.DownloadResultGridBinding
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class AnyAdapter(
    private val resView: AutofitRecyclerView,
    private val downloadViewModel: DownloadViewModel,
) : NoStateAdapter<Any>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b ->
            a.hashCode() == b.hashCode()
        },
        contentSame = { a, b ->
            a == b
        }
    )
) {
    companion object {
        const val RESULT_CACHED: Int = 1
        const val DOWNLOAD_DATA_LOADED: Int = 2
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItemOrNull(position)) {
            is ResultCached -> item.id.toLong()
            is DownloadFragment.DownloadDataLoaded -> item.id.toLong()
            else -> 0L
        }
    }

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        val compact = parent.context.getDownloadIsCompact()

        return ViewHolderState(
            if(compact) {
                DownloadImportBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            } else {
                DownloadImportCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            }

        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when(val binding = holder.view) {
            is DownloadResultGridBinding -> {
                clearImage(binding.imageView)
            }
            is HistoryResultCompactBinding -> {
                clearImage(binding.imageView)
            }
            is DownloadResultCompactBinding -> {
                clearImage(binding.imageView)
            }
        }
    }

    override fun onBindFooter(holder: ViewHolderState<Any>) {
        when(val binding = holder.view) {
            is DownloadImportBinding -> {
                binding.backgroundCard.setOnClickListener {
                    downloadViewModel.importEpub()
                }
            }
            is DownloadImportCardBinding -> {
                binding.backgroundCard.apply {
                    setOnClickListener {
                        downloadViewModel.importEpub()
                    }
                    val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }
            }
        }
    }

    override fun onCreateCustomContent(parent: ViewGroup, viewType: Int): ViewHolderState<Any> {
        val compact = parent.context.getDownloadIsCompact()
        val binding = when (viewType) {
            RESULT_CACHED -> {
                if (compact) {
                    HistoryResultCompactBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                } else {
                    DownloadResultGridBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
            }

            DOWNLOAD_DATA_LOADED -> {
                if (compact) {
                    DownloadResultCompactBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                } else {
                    DownloadResultGridBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
            }

            else -> throw NotImplementedError()
        }

        return ViewHolderState(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindContent(holder: ViewHolderState<Any>, item: Any, position: Int) {
        when (val view = holder.view) {
            is HistoryResultCompactBinding -> {
                val card = item as ResultCached
                view.apply {
                    imageText.text = card.name
                    historyExtraText.text = "${card.totalChapters} Chapters"
                    imageView.setImage(card.poster)

                    historyPlay.setOnClickListener {
                        downloadViewModel.stream(card)
                    }
                    imageView.setOnClickListener {
                        downloadViewModel.load(card)
                    }
                    historyDelete.setOnClickListener {
                        downloadViewModel.deleteAlert(card)
                    }
                    imageView.setOnLongClickListener {
                        downloadViewModel.showMetadata(card)
                        return@setOnLongClickListener true
                    }
                }
            }

            is DownloadResultGridBinding -> {
                when (item) {
                    is DownloadFragment.DownloadDataLoaded -> {
                        view.apply {
                            backgroundCard.apply {
                                val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    coverHeight
                                )
                                setOnClickListener {
                                    if(item.apiName == IMPORT_SOURCE_PDF && item.downloadedCount < item.downloadedTotal)
                                    {
                                        preloadPartialImportedPdf(item, context)
                                        if(item.state != DownloadState.IsDownloading && item.state != DownloadState.IsPaused)
                                        {
                                            downloadViewModel.refreshCard(item)
                                        }
                                    }
                                    downloadViewModel.readEpub(item)
                                }
                                setOnLongClickListener {
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }

                            downloadProgressbarIndeterment.isVisible = item.generating
                            val showDownloadLoading = item.state == DownloadState.IsPending

                            val isAPdfDownloading = item.apiName == IMPORT_SOURCE_PDF && (item.downloadedTotal != item.downloadedCount)
                            downloadUpdateLoading.isVisible = showDownloadLoading || isAPdfDownloading

                            val epubSize = getKey(DOWNLOAD_EPUB_SIZE, item.id.toString()) ?: 0
                            val diff = item.downloadedCount - epubSize
                            imageTextMore.text = "+$diff "
                            imageTextMore.isVisible = diff > 0 && !showDownloadLoading && !item.isImported
                            imageText.text = item.name

                            imageView.alpha = if (isAPdfDownloading) 0.6f else 1.0f
                            imageView.setImage(item.image)
                        }
                    }

                    is ResultCached -> {
                        view.apply {
                            backgroundCard.apply {
                                val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    coverHeight
                                )
                                setOnClickListener {
                                    downloadViewModel.load(item)
                                }
                                setOnLongClickListener {
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }
                            imageView.setImage(
                                item.image,
                            ) // skipCache = false
                            imageText.text = item.name
                            imageTextMore.isVisible = false
                        }
                    }

                    else -> throw NotImplementedError()
                }
            }

            is DownloadResultCompactBinding -> {
                val card = item as DownloadFragment.DownloadDataLoaded
                view.apply {
                    downloadHolder.isGone = card.isImported && (card.apiName != IMPORT_SOURCE_PDF || card.downloadedTotal == card.downloadedCount)
                    val same = imageText.text == card.name
                    backgroundCard.apply {
                        setOnClickListener {
                            if(card.apiName == IMPORT_SOURCE_PDF && card.downloadedCount < card.downloadedTotal)
                                preloadPartialImportedPdf(card, context)
                            downloadViewModel.readEpub(card)
                        }
                        setOnLongClickListener {
                            downloadViewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }
                    }
                    imageView.apply {
                        setOnClickListener {
                            if (!item.isImported)
                                downloadViewModel.load(card)
                        }
                        setOnLongClickListener {
                            downloadViewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }
                    }

                    downloadDeleteTrash.setOnClickListener {
                        downloadViewModel.deleteAlert(card)
                    }

                    val epubSize = getKey(DOWNLOAD_EPUB_SIZE, card.id.toString()) ?: 0
                    val diff = card.downloadedCount - epubSize
                    imageTextMore.text = if (diff > 0) "+$diff " else ""

                    imageView.setImage(card.image)

                    downloadProgressText.text =
                        "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

                    downloadProgressbar.apply {
                        max = card.downloadedTotal.toInt() * 100

                        // shitty check for non changed
                        if (same || imageText.text.isEmpty()) {//the first time, imageText.text is empty
                            val animation: ObjectAnimator = ObjectAnimator.ofInt(
                                this,
                                "progress",
                                progress,
                                card.downloadedCount.toInt() * 100
                            )

                            animation.duration = 500
                            animation.setAutoCancel(true)
                            animation.interpolator = DecelerateInterpolator()
                            animation.start()
                        } else {
                            progress = card.downloadedCount.toInt() * 100
                        }
                        //download_progressbar.progress = card.downloadedCount
                        isIndeterminate = card.generating
                        isVisible = card.generating || (card.downloadedCount < card.downloadedTotal)
                    }

                    imageText.text = card.name
                    val realState = card.state
                    /*if (card.downloadedCount >= card.downloadedTotal) {
                        downloadUpdate.alpha = 0.5f
                        downloadUpdate.isEnabled = false
                    } else {*/
                    downloadUpdate.alpha = 1f
                    downloadUpdate.isEnabled = true
                    //}
                    downloadUpdate.contentDescription = when (realState) {
                        DownloadState.IsDone -> "Done"
                        DownloadState.IsDownloading -> "Pause"
                        DownloadState.IsPaused -> "Resume"
                        DownloadState.IsFailed -> "Re-Download"
                        DownloadState.IsStopped -> "Update"
                        DownloadState.IsPending -> "Pending"
                        DownloadState.Nothing -> "Update"
                    }

                    downloadUpdate.setImageResource(
                        when (realState) {
                            DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
                            DownloadState.IsPaused -> R.drawable.netflix_play
                            DownloadState.IsStopped -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsFailed -> R.drawable.ic_baseline_autorenew_24
                            DownloadState.IsDone -> R.drawable.ic_baseline_check_24
                            DownloadState.IsPending -> R.drawable.nothing
                            DownloadState.Nothing -> R.drawable.ic_baseline_autorenew_24
                        }
                    )
                    downloadUpdate.setOnClickListener {
                        when (realState) {
                            DownloadState.IsDownloading -> downloadViewModel.pause(card)
                            DownloadState.IsPaused -> downloadViewModel.resume(card)
                            DownloadState.IsPending -> {}
                            else -> downloadViewModel.refreshCard(card)//this also resume download of imported pdfs
                        }
                    }

                    downloadUpdateLoading.isVisible = realState == DownloadState.IsPending
                }
            }

            else -> throw NotImplementedError()
        }
    }

    override fun customContentViewType(item: Any): Int {
        if (item is ResultCached) {
            return RESULT_CACHED
        } else if (item is DownloadFragment.DownloadDataLoaded) {
            return DOWNLOAD_DATA_LOADED
        }
        throw NotImplementedError()
    }
}