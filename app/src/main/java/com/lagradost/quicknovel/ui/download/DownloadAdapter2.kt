package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadResultCompactBinding
import com.lagradost.quicknovel.databinding.DownloadResultGridBinding
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class DownloadAdapter2(
    private val viewModel: DownloadViewModel,
    private val resView: AutofitRecyclerView
) : ListAdapter<DownloadFragment.DownloadDataLoaded, DownloadAdapter2.DownloadAdapter2Holder>(
    DiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadAdapter2Holder {
        val binding: ViewBinding = if (parent.context.getDownloadIsCompact()) {
            DownloadResultCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        } else {
            DownloadResultGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        return DownloadAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: DownloadAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, viewModel, resView)
    }

    class DownloadAdapter2Holder(private val binding: ViewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            card: DownloadFragment.DownloadDataLoaded,
            viewModel: DownloadViewModel,
            resView: AutofitRecyclerView
        ) {
            when (binding) {
                is DownloadResultCompactBinding -> {
                    binding.apply {
                        val same = imageText.text == card.name
                        backgroundCard.apply {
                            setOnClickListener {
                                viewModel.readEpub(card)
                            }
                            setOnLongClickListener {
                                viewModel.showMetadata(card)
                                return@setOnLongClickListener true
                            }
                        }
                        imageView.apply {
                            setOnClickListener {
                                viewModel.load(card)
                            }
                            setOnLongClickListener {
                                viewModel.showMetadata(card)
                                return@setOnLongClickListener true
                            }
                        }

                        downloadDeleteTrash.setOnClickListener {
                            viewModel.deleteAlert(card)
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
                                DownloadState.IsDownloading -> viewModel.pause(card)
                                DownloadState.IsPaused -> viewModel.resume(card)
                                DownloadState.IsPending -> {}
                                else -> viewModel.refreshCard(card)
                            }
                        }

                        downloadUpdateLoading.isVisible = realState == DownloadState.IsPending
                    }
                }

                is DownloadResultGridBinding -> {
                    binding.apply {
                        backgroundCard.apply {
                            val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                coverHeight
                            )
                            setOnClickListener {
                                viewModel.readEpub(card)
                            }
                            setOnLongClickListener {
                                viewModel.showMetadata(card)
                                return@setOnLongClickListener true
                            }
                        }

                        val same = imageText.text == card.name
                        downloadProgressbarIndeterment.isVisible = card.generating
                        val showDownloadLoading = card.state == DownloadState.IsPending
                        downloadUpdateLoading.isVisible = showDownloadLoading

                        imageView.apply {
                            setOnClickListener {
                                viewModel.readEpub(card)
                            }
                            setOnLongClickListener {
                                viewModel.showMetadata(card)
                                return@setOnLongClickListener true
                            }
                        }

                        val epubSize = getKey(DOWNLOAD_EPUB_SIZE, card.id.toString()) ?: 0
                        val diff = card.downloadedCount - epubSize
                        imageTextMore.text = "+$diff "
                        imageTextMore.isVisible = diff > 0 && !showDownloadLoading
                        imageText.text = card.name
                        imageView.setImage(card.image, fadeIn = false, skipCache = false)
                    }
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    class DiffCallback : DiffUtil.ItemCallback<DownloadFragment.DownloadDataLoaded>() {
        override fun areItemsTheSame(
            oldItem: DownloadFragment.DownloadDataLoaded,
            newItem: DownloadFragment.DownloadDataLoaded
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: DownloadFragment.DownloadDataLoaded,
            newItem: DownloadFragment.DownloadDataLoaded
        ): Boolean = oldItem == newItem
    }
}