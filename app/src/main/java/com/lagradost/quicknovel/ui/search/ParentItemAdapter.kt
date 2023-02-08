package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import kotlinx.android.synthetic.main.homepage_parent.view.*

class SearchClickCallback(val action: Int, val view: View, val card: SearchResponse)

class ParentItemAdapter(
    private val items: MutableList<HomePageList>,
    private val clickCallback: (SearchClickCallback) -> Unit,
    private val moreInfoClickCallback: (HomePageList) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ParentViewHolder {
        val layout = R.layout.homepage_parent
        return ParentViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            clickCallback,
            moreInfoClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ParentViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    fun updateList(newList: List<HomePageList>) {
        // This does not check the actual items, only the names and list sizes.
        val diffResult = DiffUtil.calculateDiff(
            ResultCachedLoadedDiffCallback(this.items, newList)
        )

        items.clear()
        items.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }


    override fun getItemCount(): Int {
        return items.size
    }

    class ParentViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        private val moreInfoClickCallback: (HomePageList) -> Unit
    ) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.home_parent_item_title
        val recyclerView: RecyclerView = itemView.home_child_recyclerview
        private val moreInfo: FrameLayout = itemView.home_child_more_info
        fun bind(info: HomePageList) {
            title.text = info.name
            val currentAdapter = (recyclerView.adapter as? HomeChildItemAdapter)
            if (currentAdapter == null) {
                recyclerView.adapter =
                    HomeChildItemAdapter(info.list.toMutableList(), clickCallback)
            } else {
                currentAdapter.updateList(info.list)
            }

            moreInfo.setOnClickListener {
                moreInfoClickCallback.invoke(info)
            }
        }
    }

    /**
     * This does not check the actual full lists, only the names and list sizes.
     **/
    private class ResultCachedLoadedDiffCallback(
        private val oldList: List<HomePageList>,
        private val newList: List<HomePageList>
    ) :
        DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].name == newList[newItemPosition].name && oldList[oldItemPosition].list.size == newList[newItemPosition].list.size

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}