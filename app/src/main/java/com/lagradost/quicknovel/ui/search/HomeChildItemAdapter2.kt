package com.lagradost.quicknovel.ui.search


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.HomeResultGridBinding
import com.lagradost.quicknovel.util.UIHelper.setImage

class HomeChildItemAdapter2(
    private val viewModel: SearchViewModel,
) :
    ListAdapter<SearchResponse, HomeChildItemAdapter2.HomeChildItemAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeChildItemAdapter2Holder {
        val binding =
            HomeResultGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HomeChildItemAdapter2Holder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: HomeChildItemAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class HomeChildItemAdapter2Holder(
        private val binding: HomeResultGridBinding,
        private val viewModel: SearchViewModel
    ) :
        RecyclerView.ViewHolder(binding.root) {


        fun bind(card: SearchResponse) {
            binding.apply {
                imageView.apply {
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
