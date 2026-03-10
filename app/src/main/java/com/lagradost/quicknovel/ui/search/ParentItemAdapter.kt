package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.databinding.HomepageParentBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool

class ParentItemAdapter(
    private val viewModel: SearchViewModel
) :
    NoStateAdapter<HomePageList>(
        diffCallback = BaseDiffCallback(
            itemSame = { a, b -> a.name == b.name },
            contentSame = { a, b -> a == b }
        )) {

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            HomepageParentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: HomePageList, position: Int) {
        val binding = holder.view as? HomepageParentBinding ?: return
        binding.apply {
            val oldAdapter = homeChildRecyclerview.adapter
            binding.homeParentItemTitle.text = item.name
            binding.homeChildMoreInfo.setOnClickListener {
                viewModel.loadHomepageList(item)
            }
            if (oldAdapter is HomeChildItemAdapter) {
                oldAdapter.submitList(item.list)
            } else {
                val newAdapter =
                    HomeChildItemAdapter(viewModel)
                homeChildRecyclerview.adapter = newAdapter
                newAdapter.submitList(item.list)
            }
        }
    }
}