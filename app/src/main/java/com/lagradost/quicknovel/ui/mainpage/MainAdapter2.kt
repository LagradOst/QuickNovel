package com.lagradost.quicknovel.ui.mainpage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class MainAdapter2(private val resView: AutofitRecyclerView) : ListAdapter<SearchResponse, MainAdapter2.MainAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainAdapter2Holder {
        val binding = SearchResultGridBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return MainAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: MainAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, resView)
    }

    class MainAdapter2Holder(private val binding : SearchResultGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card : SearchResponse, resView: AutofitRecyclerView) {
            binding.apply {
                val compactView = false//resView.context?.getGridIsCompact() ?: return

                val coverHeight: Int =
                    if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

                imageView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                    setImage(card.posterUrl, headers = card.posterHeaders)
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX
                    setOnClickListener {
                        loadResult(card.url, card.apiName)
                    }
                    setOnLongClickListener {
                        MainActivity.loadPreviewPage(card)
                        return@setOnLongClickListener true
                    }
                }

                imageText.text = card.name
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResponse>() {
        override fun areItemsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean = oldItem == newItem
    }
}