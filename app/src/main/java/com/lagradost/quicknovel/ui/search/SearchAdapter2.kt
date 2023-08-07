package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt


class SearchAdapter2(
    private val viewModel: SearchViewModel,
    private val resView: AutofitRecyclerView,
) :
    ListAdapter<SearchResponse, SearchAdapter2.SearchAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchAdapter2Holder {
        val binding =
            SearchResultGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchAdapter2Holder(binding, resView, viewModel)
    }

    override fun onBindViewHolder(holder: SearchAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class SearchAdapter2Holder(
        private val binding: SearchResultGridBinding,
        private val resView: AutofitRecyclerView,
        private val viewModel: SearchViewModel
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: SearchResponse) {
            binding.apply {
                val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                imageView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    setImage(card.image)

                    setOnClickListener {
                        viewModel.load(card)
                    }

                    setOnLongClickListener {
                        viewModel.showMetadata(card)
                        return@setOnLongClickListener true
                    }
                }
                imageText.text = card.name
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResponse>() {
        override fun areItemsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean =
            oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean =
            oldItem == newItem
    }
}