package com.lagradost.quicknovel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.browse_list_compact.view.*
import kotlinx.android.synthetic.main.download_result_compact.view.*

class BrowseAdapter(
    context: Context,
    animeList: ArrayList<MainAPI>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.browse_list_compact
        return BrowseCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BrowseCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return cardList[position].name.hashCode().toLong()
    }

    class BrowseCardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: CardView = itemView.browse_background
        val browse_icon: ImageView = itemView.browse_icon
        val browse_text: TextView = itemView.browse_text

        fun bind(api: MainAPI) {
            browse_text.text = api.name
            val icon = api.iconId
            if(icon != null) {
                browse_icon.setImageResource(icon)
            }
            cardView.setOnClickListener {
                println("TEST")
            }
        }
    }
}
