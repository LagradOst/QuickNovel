package com.example.epubdownloader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.epubdownloader.MainActivity.Companion.activity
import com.example.epubdownloader.ui.result.ResultFragment
import kotlinx.android.synthetic.main.search_result_compact.view.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt


class ResAdapter(
    context: Context,
    animeList: ArrayList<SearchResponse>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layout = R.layout.search_result_compact
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }

        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText
        val cardTextExtra: TextView = itemView.imageTextExtra
        fun bind(card: SearchResponse) {
            cardText.text = card.name
            if(card.latestChapter != null) {
                cardTextExtra.text = card.latestChapter
            }
            val glideUrl =
                GlideUrl(card.posterUrl)
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            cardView.setOnClickListener {
                MainActivity.loadResult(card.url)
            }
        }

    }
}
