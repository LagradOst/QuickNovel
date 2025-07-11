package com.lagradost.quicknovel.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.setImage

class HistoryAdapter2(private val viewModel: HistoryViewModel) :
    NoStateAdapter<ResultCached>(DiffCallback()) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Nothing> {
        val binding =
            HistoryResultCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryAdapter2Holder(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Nothing>,
        item: ResultCached,
        position: Int
    ) {
        val currentItem = getItem(position)
        when (holder) {
            is HistoryAdapter2Holder -> holder.bind(currentItem, viewModel)
        }
    }

    class HistoryAdapter2Holder(private val binding: HistoryResultCompactBinding) :
        ViewHolderState<Nothing>(binding) {
        fun bind(card: ResultCached, viewModel: HistoryViewModel) {
            binding.apply {
                imageText.text = card.name
                historyExtraText.text = "${card.totalChapters} Chapters"
                imageView.setImage(card.poster)

                historyPlay.setOnClickListener {
                    viewModel.stream(card)
                }
                imageView.setOnClickListener {
                    viewModel.open(card)
                }
                historyDelete.setOnClickListener {
                    viewModel.deleteAlert(card)
                }
                imageView.setOnLongClickListener {
                    viewModel.showMetadata(card)
                    return@setOnLongClickListener true
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