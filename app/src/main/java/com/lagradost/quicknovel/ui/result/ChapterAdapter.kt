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
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SimpleChapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    private fun refresh(
        binding: SimpleChapterBinding,
        card: ChapterData,
        viewModel: ResultViewModel
    ) {
        val alpha = if (viewModel.hasReadChapter(chapter = card)) 0.5F else 1.0F

        binding.name.alpha = alpha
        binding.releaseDate.alpha = alpha
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: ChapterData, position: Int) {
        val binding = holder.view as? SimpleChapterBinding ?: return
        binding.apply {
            name.text = item.name
            releaseDate.text = item.dateOfRelease
            releaseDate.isGone = item.dateOfRelease.isNullOrBlank()
            root.setOnClickListener {
                viewModel.streamRead(item)
                refresh(binding, item, viewModel)
            }
            root.setOnLongClickListener {
                viewModel.setReadChapter(chapter = item, !viewModel.hasReadChapter(item))
                refresh(binding, item, viewModel)
                return@setOnLongClickListener true
            }
            refresh(binding, item, viewModel)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChapterData>() {
        override fun areItemsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean =
            oldItem == newItem
    }
}