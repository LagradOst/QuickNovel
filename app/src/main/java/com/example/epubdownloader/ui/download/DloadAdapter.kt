package com.example.epubdownloader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.epubdownloader.ui.download.DownloadFragment
import kotlinx.android.synthetic.main.download_result_compact.view.*
import kotlinx.android.synthetic.main.search_result_compact.view.backgroundCard
import kotlinx.android.synthetic.main.search_result_compact.view.imageText
import kotlinx.android.synthetic.main.search_result_compact.view.imageView


class DloadAdapter(
    context: Context,
    animeList: ArrayList<DownloadFragment.DownloadDataLoaded>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.download_result_compact
        return DownloadCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class DownloadCardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText
        val download_progress_text: TextView = itemView.download_progress_text
        val download_progressbar: ProgressBar = itemView.download_progressbar

        //        val cardTextExtra: TextView = itemView.imageTextExtra
        val bg = itemView.backgroundCard
        fun bind(card: DownloadFragment.DownloadDataLoaded) {
            cardText.text = card.name
            download_progress_text.text = "${card.downloadedCount}/${card.downloadedTotal}"
            download_progressbar.progress = card.downloadedCount * 100 / card.downloadedTotal

            val glideUrl =
                GlideUrl(card.posterUrl)
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            bg.setOnClickListener {
                MainActivity.loadResult(card.source)
            }
        }
    }
}
