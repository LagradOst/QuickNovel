package com.lagradost.quicknovel.ui.mainpage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import com.lagradost.quicknovel.util.SettingsHelper.getGridFormatId
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.toPx
import kotlinx.android.synthetic.main.search_result_super_compact.view.*
import kotlin.math.roundToInt


class MainAdapter(
    val context: Context,
    var cardList: ArrayList<SearchResponse>,
    private val resView: AutofitRecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layout = context.getGridFormatId()
        return MainCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context,
            resView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MainCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }


    class MainCardViewHolder
    constructor(itemView: View, _context: Context, resView: AutofitRecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardText: TextView = itemView.imageText
        val cardTextExtra: TextView? = itemView.imageTextExtra
        val cardView: ImageView = itemView.imageView

        /*

        val imageTextProvider: TextView = itemView.imageTextProvider*/
        val compactView = context.getGridIsCompact()
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()
        val bg = itemView.backgroundCard
        fun bind(card: SearchResponse) {

            cardView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    coverHeight
                )
            }

            cardText.text = card.name
            if (cardTextExtra != null)
                cardTextExtra.text = card.latestChapter ?: ""

            /*
            bg.setCardBackgroundColor(MainActivity.activity.getColor(R.color.itemBackground))
            for (d in SearchFragment.searchDowloads) {
                if (card.url == d.source) {
                    bg.setCardBackgroundColor(MainActivity.activity.getColor(R.color.colorItemSeen))
                    break
                }
            }*/
            //imageTextProvider.text = card.apiName

            val glideUrl =
                GlideUrl(card.posterUrl)

            cardView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            bg.setOnClickListener {
                MainActivity.loadResult(card.url, card.apiName)
            }
        }
    }
}
