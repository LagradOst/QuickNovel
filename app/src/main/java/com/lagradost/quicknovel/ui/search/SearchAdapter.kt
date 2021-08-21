package com.lagradost.quicknovel.ui.search

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.util.SettingsHelper.getGridFormatId
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.search_result_super_compact.view.*
import kotlin.math.roundToInt

const val SEARCH_ACTION_LOAD = 0
const val SEARCH_ACTION_SHOW_METADATA = 1

class SearchAdapter(
    var cardList: List<SearchResponse>,
    private val resView: AutofitRecyclerView,
    private val clickCallback: (SearchClickCallback) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = parent.context.getGridFormatId()
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            resView,
            clickCallback,
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
    constructor(itemView: View, resView: AutofitRecyclerView, private val clickCallback: (SearchClickCallback) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        val cardView: ImageView = itemView.imageView
        private val cardText: TextView = itemView.imageText
        private val cardTextExtra: TextView? = itemView.imageTextExtra

        //val imageTextProvider: TextView? = itemView.imageTextProvider
        val bg = itemView.backgroundCard
        private val compactView = itemView.context.getGridIsCompact()
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

            cardView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX
            cardView.setImage(card.posterUrl)

            bg.setOnClickListener {
                clickCallback.invoke(SearchClickCallback(SEARCH_ACTION_LOAD, it, card))
            }

            bg.setOnLongClickListener {
                clickCallback.invoke(SearchClickCallback(SEARCH_ACTION_SHOW_METADATA, it, card))
                return@setOnLongClickListener true
            }
        }
    }
}
