package com.lagradost.quicknovel.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.BrowseListCompactBinding
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment

class BrowseAdapter2 : ListAdapter<MainAPI, BrowseAdapter2.BrowseAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowseAdapter2Holder {
        val binding = BrowseListCompactBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return BrowseAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: BrowseAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class BrowseAdapter2Holder(private val binding : BrowseListCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(api : MainAPI) {
            binding.apply {
                browseText.text = api.name
                api.iconId?.let { browseIcon.setImageResource(it) }
                browseIconBackground.setCardBackgroundColor(ContextCompat.getColor(browseIconBackground.context, api.iconBackgroundId))
                browseBackground.setOnClickListener {
                    activity?.navigate(R.id.global_to_navigation_mainpage,MainPageFragment.newInstance(api.name))
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MainAPI>() {
        override fun areItemsTheSame(oldItem: MainAPI, newItem: MainAPI): Boolean = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: MainAPI, newItem: MainAPI): Boolean = oldItem.name == newItem.name
    }
}