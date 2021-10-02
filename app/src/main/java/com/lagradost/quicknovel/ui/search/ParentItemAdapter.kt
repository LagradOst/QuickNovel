package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.util.MarginItemDecoration
import com.lagradost.quicknovel.util.toDp
import kotlinx.android.synthetic.main.homepage_parent.view.*

class SearchClickCallback(val action: Int, val view: View, val card: SearchResponse)

class ParentItemAdapter(
    var items: List<HomePageList>,
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
            recyclerView.addItemDecoration(MarginItemDecoration(8.toDp, true))
            recyclerView.adapter = HomeChildItemAdapter(info.list, clickCallback)
            (recyclerView.adapter as HomeChildItemAdapter).notifyDataSetChanged()

            moreInfo.setOnClickListener {
                moreInfoClickCallback.invoke(info)
            }
        }
    }
}