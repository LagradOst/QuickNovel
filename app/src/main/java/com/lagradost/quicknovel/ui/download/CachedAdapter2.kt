package com.lagradost.quicknovel.ui.download

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.DownloadResultGridBinding
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.setText
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class CachedAdapter2(
    private val viewModel: DownloadViewModel,
    private val resView: AutofitRecyclerView
) : ListAdapter<ResultCached, CachedAdapter2.DownloadAdapter2Holder>(
    DiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadAdapter2Holder {
        val binding: ViewBinding = if (parent.context.getDownloadIsCompact()) {
            HistoryResultCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        fun bind(card: ResultCached, viewModel: DownloadViewModel, resView: AutofitRecyclerView) {
            when (binding) {
                is HistoryResultCompactBinding -> {
                    binding.apply {
                        historyDelete.setOnClickListener {
                            viewModel.deleteAlert(card)
                        }

                        historyPlay.setOnClickListener {
                            viewModel.stream(card)
                        }

                        backgroundCard.setOnClickListener {
                            viewModel.load(card)
                        }

                        imageView.setOnClickListener {
                            viewModel.load(card)
                        }

                        imageView.setOnLongClickListener {
                            viewModel.showMetadata(card)
                            return@setOnLongClickListener true
                        }

                        imageView.setImage(card.image, fadeIn = false, skipCache = false) //
                        imageText.text = card.name
                        historyExtraText.setText(txt(R.string.chapter_format, card.totalChapters)) //"${card.totalChapters} Chapters"
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
                                viewModel.load(card)
                            }
                            setOnLongClickListener {
                                viewModel.showMetadata(card)
                                return@setOnLongClickListener true
                            }
                        }
                        imageView.setImage(card.image, fadeIn = true, skipCache = false) // skipCache = false
                        imageText.text = card.name
                        imageTextMore.isVisible = false
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ResultCached>() {
        override fun areItemsTheSame(oldItem: ResultCached, newItem: ResultCached): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ResultCached, newItem: ResultCached): Boolean =
            oldItem == newItem
    }
}