package com.lagradost.quicknovel.ui.mainpage

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import com.lagradost.quicknovel.util.SettingsHelper.getGridFormatId
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import kotlinx.android.synthetic.main.search_result_super_compact.view.*
import kotlin.math.roundToInt


class MainAdapter(
    val activity: Activity,
    private val resView: AutofitRecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList: MutableList<SearchResponse> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layout = activity.getGridFormatId()
        return MainCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            activity,
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

    fun updateList(newList: List<SearchResponse>) {
        val diffResult = DiffUtil.calculateDiff(
            SearchResponseDiffCallback(this.cardList, newList)
        )

        cardList.clear()
        cardList.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class MainCardViewHolder
    constructor(itemView: View, val activity: Activity, resView: AutofitRecyclerView) :
        RecyclerView.ViewHolder(itemView) {
        private val cardText: TextView = itemView.imageText
        private val cardTextExtra: TextView? = itemView.imageTextExtra
        val cardView: ImageView = itemView.imageView

        /*

        val imageTextProvider: TextView = itemView.imageTextProvider*/
        private val compactView = activity.getGridIsCompact()
        private val coverHeight: Int =
            if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()
        private val bg = itemView.backgroundCard
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

            cardView.setImage(card.posterUrl, headers = card.posterHeaders)
            cardView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX

            bg.setOnClickListener {
                (activity as AppCompatActivity).loadResult(card.url, card.apiName)
            }
        }
    }
}
class SearchResponseDiffCallback(private val oldList: List<SearchResponse>, private val newList: List<SearchResponse>) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].url == newList[newItemPosition].url

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}