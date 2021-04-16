package com.lagradost.quicknovel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.ui.search.SearchFragment
import kotlinx.android.synthetic.main.search_result_super_compact.view.*


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

        val layout = R.layout.search_result_super_compact
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
        val imageTextProvider: TextView = itemView.imageTextProvider
        val bg = itemView.backgroundCard
        fun bind(card: SearchResponse) {
            cardText.text = card.name
            if (card.latestChapter == null) {
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
                    cardTextExtra.text =
                        "${card.apiName} • $max Chapter${if (max != 1) "s" else ""}" /*+ if (card.rating == null) "" else " • " + MainActivity.getRating(
                            card.rating)*/
                } else {
                    cardTextExtra.text = card.apiName
                }
            }

            bg.setCardBackgroundColor(MainActivity.activity.getColor(R.color.itemBackground))
            for (d in SearchFragment.searchDowloads) {
                if(card.url == d.source) {
                    bg.setCardBackgroundColor(MainActivity.activity.getColor(R.color.colorItemSeen))
                    break
                }
            }
            //imageTextProvider.text = card.apiName

            val glideUrl =
                GlideUrl(card.posterUrl)
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
