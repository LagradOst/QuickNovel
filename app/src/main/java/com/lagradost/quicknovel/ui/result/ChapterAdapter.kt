package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.databinding.SimpleChapterBinding

class ChapterAdapter(val viewModel : ResultViewModel) : ListAdapter<ChapterData, RecyclerView.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ChapterAdapterHolder(SimpleChapterBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is ChapterAdapterHolder -> {
                val currentItem = getItem(position)
                holder.bind(currentItem, viewModel)
            }
        }
    }

    class ChapterAdapterHolder(private val binding : SimpleChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        private fun refresh(card : ChapterData, viewModel : ResultViewModel) {
            binding.apply {
                root.alpha = if (viewModel.hasReadChapter(chapter = card)) 0.5F else 1.0F
            }
        }
        fun bind(card : ChapterData, viewModel : ResultViewModel) {
            binding.apply {
                name.text = card.name
                releaseDate.text = card.dateOfRelease
                releaseDate.isGone = card.dateOfRelease.isNullOrBlank()
                root.setOnClickListener {
                    viewModel.streamRead(card)
                    refresh(card,viewModel)
                }
                root.setOnLongClickListener {
                    viewModel.showChapterContextMenu(card)
                    return@setOnLongClickListener true
                }
                refresh(card,viewModel)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChapterData>() {
        override fun areItemsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean = oldItem == newItem
    }
}