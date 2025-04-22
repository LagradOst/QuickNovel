package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadResultCompactBinding
import com.lagradost.quicknovel.databinding.DownloadResultGridBinding
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.BaseAdapter
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class AnyState(
    view: ViewBinding
) : ViewHolderState<Nothing>(view)

class AnyAdapter(
    private val resView: AutofitRecyclerView,
    private val downloadViewModel: DownloadViewModel,
    fragment: Fragment,
    id: Int
) : BaseAdapter<Any, Nothing>(
    fragment, id,
    diffCallback = BaseDiffCallback(
        itemSame = { a, b ->
            a.hashCode() == b.hashCode()
        },
        contentSame = { a, b ->
            a === b
        }
    )
) {
    companion object {
        const val RESULT_CACHED: Int = 1
        const val DOWNLOAD_DATA_LOADED: Int = 2
    }

    override fun onCreateCustom(parent: ViewGroup, viewType: Int): ViewHolderState<Nothing> {
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

        return AnyState(binding)
    }

    override fun onBindContent(holder: ViewHolderState<Nothing>, item: Any, position: Int) {
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
                                    downloadViewModel.readEpub(item)
                                }
                                setOnLongClickListener {
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }

                            val same = imageText.text == item.name
                            downloadProgressbarIndeterment.isVisible = item.generating
                            val showDownloadLoading = item.state == DownloadState.IsPending
                            downloadUpdateLoading.isVisible = showDownloadLoading

                            imageView.apply {
                                setOnClickListener {
                                    downloadViewModel.readEpub(item)
                                }
                                setOnLongClickListener {
                                    downloadViewModel.showMetadata(item)
                                    return@setOnLongClickListener true
                                }
                            }

                            val epubSize = getKey(DOWNLOAD_EPUB_SIZE, item.id.toString()) ?: 0
                            val diff = item.downloadedCount - epubSize
                            imageTextMore.text = "+$diff "
                            imageTextMore.isVisible = diff > 0 && !showDownloadLoading
                            imageText.text = item.name
                            imageView.setImage(item.image, fadeIn = false, skipCache = false)
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
                                fadeIn = true,
                                skipCache = false
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
                    val same = imageText.text == card.name
                    backgroundCard.apply {
                        setOnClickListener {
                            downloadViewModel.readEpub(card)
                        }
                        setOnLongClickListener {
                            downloadViewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }
                    }
                    imageView.apply {
                        setOnClickListener {
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

                    imageView.setImage(card.image, fadeIn = false, skipCache = false)

                    downloadProgressbar.isVisible = !card.generating
                    downloadProgressbarIndeterment.isVisible = card.generating

                    downloadProgressText.text =
                        "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

                    downloadProgressbar.apply {
                        max = card.downloadedTotal * 100

                        // shitty check for non changed
                        if (same) {
                            val animation: ObjectAnimator = ObjectAnimator.ofInt(
                                this,
                                "progress",
                                progress,
                                card.downloadedCount * 100
                            )

                            animation.duration = 500
                            animation.setAutoCancel(true)
                            animation.interpolator = DecelerateInterpolator()
                            animation.start()
                        } else {
                            progress = card.downloadedCount * 100
                        }
                        //download_progressbar.progress = card.downloadedCount
                        alpha = if (card.downloadedCount >= card.downloadedTotal) 0f else 1f
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
                            else -> downloadViewModel.refreshCard(card)
                        }
                    }

                    downloadUpdateLoading.isVisible = realState == DownloadState.IsPending
                }
            }

            else -> throw NotImplementedError()
        }
    }

    override fun getItemViewTypeCustom(item: Any): Int {
        if (item is ResultCached) {
            return RESULT_CACHED
        } else if (item is DownloadFragment.DownloadDataLoaded) {
            return DOWNLOAD_DATA_LOADED
        }

        throw NotImplementedError()
    }
}