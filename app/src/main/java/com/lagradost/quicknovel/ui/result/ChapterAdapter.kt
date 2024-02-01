package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.LoadingBottomBinding
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.databinding.SimpleChapterBinding
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt
class ChapterAdapter : ListAdapter<ChapterData, RecyclerView.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ChapterAdapterHolder(SimpleChapterBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is ChapterAdapterHolder -> {
                val currentItem = getItem(position)
                holder.bind(currentItem)
            }
        }
    }

    class ChapterAdapterHolder(private val binding : SimpleChapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card : ChapterData) {
            binding.apply {
                name.text = card.name
                releaseDate.text = card.dateOfRelease
                releaseDate.isGone = card.dateOfRelease.isNullOrBlank()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChapterData>() {
        override fun areItemsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: ChapterData, newItem: ChapterData): Boolean = oldItem == newItem
    }
}