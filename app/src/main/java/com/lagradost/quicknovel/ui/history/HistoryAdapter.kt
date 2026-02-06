package com.lagradost.quicknovel.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.HistoryResultCompactBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.setImage

class HistoryAdapter(private val viewModel: HistoryViewModel) :
    NoStateAdapter<ResultCached>(DiffCallback()) {

    companion object {
        val sharedPool =
            RecyclerView.RecycledViewPool().apply {
                this.setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(HistoryResultCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: ResultCached,
        position: Int
    ) {
        val binding = holder.view as? HistoryResultCompactBinding ?: return

        binding.apply {
            imageText.text = item.name
            historyExtraText.text = "${item.totalChapters} " + root.context.getString(R.string.read_action_chapters)
            imageView.setImage(item.poster)

            historyPlay.setOnClickListener {
                viewModel.stream(item)
            }
            imageView.setOnClickListener {
                viewModel.open(item)
            }
            historyDelete.setOnClickListener {
                viewModel.deleteAlert(item)
            }
            imageView.setOnLongClickListener {
                viewModel.showMetadata(item)
                return@setOnLongClickListener true
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