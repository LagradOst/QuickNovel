package com.lagradost.quicknovel.ui.search


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.util.UIHelper.setImage
import kotlinx.android.synthetic.main.home_result_grid.view.*

class HomeChildItemAdapter(
    var cardList: List<SearchResponse>,
    private val clickCallback: (SearchClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.home_result_grid
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback
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
    constructor(itemView: View, private val clickCallback: (SearchClickCallback) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        val cardView: ImageView = itemView.imageView
        private val cardText: TextView = itemView.imageText

        //val cardTextExtra: TextView? = itemView.imageTextExtra
        //val imageTextProvider: TextView? = itemView.imageTextProvider
        private val bg: CardView = itemView.backgroundCard

        fun bind(card: SearchResponse) {
            cardText.text = card.name

            //imageTextProvider.text = card.apiName
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
