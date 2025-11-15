package com.lagradost.quicknovel.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.BrowseListCompactBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment

class BrowseAdapter2 : NoStateAdapter<MainAPI>(DiffCallback()) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(BrowseListCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: MainAPI, position: Int) {
        val binding = holder.view as? BrowseListCompactBinding ?: return

        binding.apply {
            browseText.text = item.name
            item.iconId?.let { browseIcon.setImageResource(it) }
            browseIconBackground.setCardBackgroundColor(
                ContextCompat.getColor(
                    browseIconBackground.context,
                    item.iconBackgroundId
                )
            )
            browseBackground.setOnClickListener {
                activity?.navigate(
                    R.id.global_to_navigation_mainpage,
                    MainPageFragment.newInstance(item.name)
                )
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MainAPI>() {
        override fun areItemsTheSame(oldItem: MainAPI, newItem: MainAPI): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: MainAPI, newItem: MainAPI): Boolean =
            oldItem.name == newItem.name
    }
}