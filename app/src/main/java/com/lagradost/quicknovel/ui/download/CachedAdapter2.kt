package com.lagradost.quicknovel.ui.download

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.widget.AutofitRecyclerView

class CachedAdapter2(private val viewModel: DownloadViewModel, private val resView: AutofitRecyclerView) : ListAdapter<ResultCached, CachedAdapter2.DownloadAdapter2Holder>(
    DiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadAdapter2Holder {
        val binding = HistoryResultCompactBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return DownloadAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: DownloadAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, viewModel, resView)
    }

    class DownloadAdapter2Holder(private val binding : HistoryResultCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card : ResultCached, viewModel: DownloadViewModel, resView: AutofitRecyclerView) {
            binding.apply {

            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ResultCached>() {
        override fun areItemsTheSame(oldItem: ResultCached, newItem: ResultCached): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ResultCached, newItem: ResultCached): Boolean = oldItem == newItem
    }
}