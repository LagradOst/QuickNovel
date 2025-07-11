package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.databinding.SimpleChapterBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState

class ChapterAdapter(val viewModel: ResultViewModel) : NoStateAdapter<ChapterData>(DiffCallback()) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Nothing> {
        return ChapterAdapterHolder(
            SimpleChapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Nothing>, item: ChapterData, position: Int) {
        when (holder) {
            is ChapterAdapterHolder -> {
                val currentItem = getItem(position)
                holder.bind(currentItem, viewModel)
            }
        }
    }

    class ChapterAdapterHolder(private val binding: SimpleChapterBinding) :
        ViewHolderState<Nothing>(binding) {
        private fun refresh(card: ChapterData, viewModel: ResultViewModel) {
            binding.apply {
                root.alpha = if (viewModel.hasReadChapter(chapter = card)) 0.5F else 1.0F
            }
        }

        fun bind(card: ChapterData, viewModel: ResultViewModel) {
            binding.apply {
                name.text = card.name
                releaseDate.text = card.dateOfRelease
                releaseDate.isGone = card.dateOfRelease.isNullOrBlank()
                root.setOnClickListener {
                    viewModel.streamRead(card)
                    refresh(card, viewModel)
                }
                root.setOnLongClickListener {
                    viewModel.setReadChapter(chapter = card, !viewModel.hasReadChapter(card))
                    refresh(card, viewModel)
                    return@setOnLongClickListener true
                }
                refresh(card, viewModel)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChapterData>() {
        override fun areItemsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean =
            oldItem == newItem
    }
}