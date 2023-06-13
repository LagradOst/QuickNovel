package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.BookDownloader
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadResultCompactBinding
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class DownloadAdapter2(private val viewModel: DownloadViewModel, private val resView: AutofitRecyclerView) : ListAdapter<DownloadFragment.DownloadDataLoaded, DownloadAdapter2.DownloadAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadAdapter2Holder {
        val binding = DownloadResultCompactBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return DownloadAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: DownloadAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, viewModel, resView)
    }

    class DownloadAdapter2Holder(private val binding : DownloadResultCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card : DownloadFragment.DownloadDataLoaded, viewModel: DownloadViewModel, resView: AutofitRecyclerView) {
            binding.apply {
                itemView.apply {
                    val compactView = true; //context.getDownloadIsCompact()
                    val coverHeight: Int = if (compactView) 100.toPx else (resView.itemWidth / 0.68).roundToInt()
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }

                backgroundCard.setOnClickListener {
                    // load epub
                }
                imageView.setOnClickListener {
                    loadResult(card.source, card.apiName)
                }
                imageView.setImage(card.posterUrl)

                val isGenerating = false
                downloadProgressbar.isVisible = !isGenerating
                downloadProgressbarIndeterment.isVisible = isGenerating

                downloadProgressText.text =
                    "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

                downloadProgressbar.apply {
                    max = card.downloadedTotal * 100

                    // shitty check for non changed
                    if (imageText.text == card.name) {
                        val animation: ObjectAnimator = ObjectAnimator.ofInt(this,
                            "progress",
                            progress,
                            card.downloadedCount * 100)

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
                var realState = card.state
                if (card.downloadedCount >= card.downloadedTotal && card.updated) {
                    downloadUpdate.alpha = 0.5f
                    downloadUpdate.isEnabled = false
                    realState = BookDownloader.DownloadType.IsDone
                } else {
                    downloadUpdate.alpha = 1f
                    downloadUpdate.isEnabled = true
                }
                downloadUpdate.contentDescription = when (realState) {
                    BookDownloader.DownloadType.IsDone -> "Done"
                    BookDownloader.DownloadType.IsDownloading -> "Pause"
                    BookDownloader.DownloadType.IsPaused -> "Resume"
                    BookDownloader.DownloadType.IsFailed -> "Re-Download"
                    BookDownloader.DownloadType.IsStopped -> "Update"
                    BookDownloader.DownloadType.IsPending -> "Pending"
                }

                downloadUpdate.setImageResource(when (realState) {
                    BookDownloader.DownloadType.IsDownloading -> R.drawable.ic_baseline_pause_24
                    BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
                    BookDownloader.DownloadType.IsStopped -> R.drawable.ic_baseline_autorenew_24
                    BookDownloader.DownloadType.IsFailed -> R.drawable.ic_baseline_autorenew_24
                    BookDownloader.DownloadType.IsDone -> R.drawable.ic_baseline_check_24
                    BookDownloader.DownloadType.IsPending -> R.drawable.nothing
                })
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DownloadFragment.DownloadDataLoaded>() {
        override fun areItemsTheSame(oldItem: DownloadFragment.DownloadDataLoaded, newItem: DownloadFragment.DownloadDataLoaded): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DownloadFragment.DownloadDataLoaded, newItem: DownloadFragment.DownloadDataLoaded): Boolean = oldItem == newItem
    }
}