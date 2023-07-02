package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.databinding.HomepageParentBinding

class ParentItemAdapter2(
    private val viewModel: SearchViewModel
) :
    ListAdapter<HomePageList, ParentItemAdapter2.ParentItemAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParentItemAdapter2Holder {
        val binding =
            HomepageParentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParentItemAdapter2Holder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: ParentItemAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class ParentItemAdapter2Holder(
        private val binding: HomepageParentBinding,
        private val viewModel: SearchViewModel
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: HomePageList) {
            binding.apply {
                val oldAdapter = homeChildRecyclerview.adapter
                binding.homeParentItemTitle.text = card.name
                binding.homeChildMoreInfo.setOnClickListener {
                    viewModel.loadHomepageList(card)
                }
                if (oldAdapter is HomeChildItemAdapter2) {
                    oldAdapter.submitList(card.list)
                } else {
                    val newAdapter =
                        HomeChildItemAdapter2(viewModel)
                    homeChildRecyclerview.adapter = newAdapter
                    newAdapter.submitList(card.list)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HomePageList>() {
        override fun areItemsTheSame(oldItem: HomePageList, newItem: HomePageList): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: HomePageList, newItem: HomePageList): Boolean =
            oldItem == newItem
    }
}