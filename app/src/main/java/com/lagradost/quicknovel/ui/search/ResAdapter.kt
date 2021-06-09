package com.lagradost.quicknovel.ui.search

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import com.lagradost.quicknovel.util.SettingsHelper.getGridFormatId
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.toPx
import kotlinx.android.synthetic.main.search_result_super_compact.view.*
import kotlin.math.roundToInt


class ResAdapter(
    val activity: Activity,
    var cardList: ArrayList<SearchResponse>,
    private val resView: AutofitRecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layout = activity.getGridFormatId()
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            activity,
            resView
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
    constructor(itemView: View, val activity: Activity, resView: AutofitRecyclerView) : RecyclerView.ViewHolder(itemView) {

        val cardView: ImageView = itemView.imageView
        private val cardText: TextView = itemView.imageText
        private val cardTextExtra: TextView? = itemView.imageTextExtra

        //val imageTextProvider: TextView? = itemView.imageTextProvider
        val bg = itemView.backgroundCard
        private val compactView = activity.getGridIsCompact()
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        @SuppressLint("SetTextI18n")
        fun bind(card: SearchResponse) {
            cardView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
            }

            cardText.text = card.name
            if (card.latestChapter == null) {
                if (cardTextExtra != null)
                    cardTextExtra.text = card.apiName
            } else {
                val r = Regex("""[0-9]+""")
                val matches = r.findAll(card.latestChapter, 0).toList()
                if (matches.isNotEmpty()) {
                    var max = 0
                    for (m in matches) {
                        val subMax = m.value.toInt()
                        if (subMax > max) {
                            max = subMax
                        }
                    }
                    if (cardTextExtra != null)
                        cardTextExtra.text =
                            "${card.apiName} • $max Chapter${if (max != 1) "s" else ""}" /*+ if (card.rating == null) "" else " • " + MainActivity.getRating(
                            card.rating)*/
                } else {
                    if (cardTextExtra != null)
                        cardTextExtra.text = card.apiName
                }
            }

            /*
            bg.setCardBackgroundColor(context.colorFromAttribute(R.attr.itemBackground))
            for (d in SearchFragment.searchDowloads) {
                if (card.url == d.source) {
                    bg.setCardBackgroundColor(context.colorFromAttribute(R.attr.colorItemSeen))
                    break
                }
            }*/
            //imageTextProvider.text = card.apiName

            val glideUrl =
                GlideUrl(card.posterUrl)

            cardView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX
            activity.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            bg.setOnClickListener {
                (activity as AppCompatActivity).loadResult(card.url, card.apiName)
            }
        }
    }
}
