package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.MainActivity.Companion.activity
import com.lagradost.quicknovel.MainActivity.Companion.getApiFromName
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.download.DownloadFragment.Companion.updateDownloadFromCard
import kotlinx.android.synthetic.main.download_result_compact.view.*
import kotlin.concurrent.thread


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

    override fun getItemId(position: Int): Long {
        return cardList[position].id.toLong()
    }

    companion object {
        val cachedLoadResponse = hashMapOf<Int, LoadResponse>()
    }

    class DownloadCardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText
        val download_progress_text: TextView = itemView.download_progress_text
        val download_progressbar: ProgressBar = itemView.download_progressbar
        val download_update: ImageView = itemView.download_update
        val download_open_btt: LinearLayout = itemView.download_open_btt
        val download_progressbar_indeterment: ProgressBar = itemView.download_progressbar_indeterment
        val download_delete_trash: ImageView = itemView.download_delete_trash
        val imageTextMore: TextView = itemView.imageTextMore

        //        val cardTextExtra: TextView = itemView.imageTextExtra
        val bg = itemView.backgroundCard
        fun bind(card: DownloadFragment.DownloadDataLoaded) {
            val api = getApiFromName(card.apiName)

            cardText.text = card.name
            download_progress_text.text =
                "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

            // ANIMATION PROGRESSBAR
            download_progressbar.max = card.downloadedTotal * 100

            if (download_progressbar.progress != 0) {
                val animation: ObjectAnimator = ObjectAnimator.ofInt(download_progressbar,
                    "progress",
                    download_progressbar.progress,
                    card.downloadedCount * 100)

                animation.duration = 500
                animation.setAutoCancel(true)
                animation.interpolator = DecelerateInterpolator()
                animation.start()
            } else {
                download_progressbar.progress = card.downloadedCount * 100
            }
            //download_progressbar.progress = card.downloadedCount
            download_progressbar.alpha = if (card.downloadedCount >= card.downloadedTotal) 0f else 1f

            var realState = card.state
            if (card.downloadedCount >= card.downloadedTotal && card.updated) {
                download_update.alpha = 0.5f
                download_update.isEnabled = false
                realState = BookDownloader.DownloadType.IsDone
            }
            else {
                download_update.alpha = 1f
                download_update.isEnabled = true
            }

            val glideUrl =
                GlideUrl(card.posterUrl)
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }

            download_update.contentDescription = when (realState) {
                BookDownloader.DownloadType.IsDone -> "Done"
                BookDownloader.DownloadType.IsDownloading -> "Pause"
                BookDownloader.DownloadType.IsPaused -> "Resume"
                BookDownloader.DownloadType.IsFailed -> "Re-Download"
                BookDownloader.DownloadType.IsStopped -> "Update"
            }

            download_update.setImageResource(when (realState) {
                BookDownloader.DownloadType.IsDownloading -> R.drawable.ic_baseline_pause_24
                BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
                BookDownloader.DownloadType.IsStopped -> R.drawable.ic_baseline_autorenew_24
                BookDownloader.DownloadType.IsFailed -> R.drawable.ic_baseline_autorenew_24
                BookDownloader.DownloadType.IsDone -> R.drawable.ic_baseline_check_24
                else -> R.drawable.netflix_download
            })

            fun getDiff(): Int {
                val dloaded = DataStore.getKey(DOWNLOAD_EPUB_SIZE, card.id.toString(), 0)!!
                return card.downloadedCount - dloaded
            }

            fun getEpub(): Boolean {
                return getDiff() != 0
            }

            fun updateTxtDiff() {
                val diff = getDiff()
                imageTextMore.text = if (diff > 0) "+$diff "
                else ""
            }

            updateTxtDiff()

            fun updateBar(isGenerating: Boolean? = null) {
                val isIndeterminate = isGenerating ?: BookDownloader.isTurningIntoEpub.containsKey(card.id)
                download_progressbar.visibility = if (!isIndeterminate) View.VISIBLE else View.INVISIBLE
                download_progressbar_indeterment.visibility = if (isIndeterminate) View.VISIBLE else View.INVISIBLE
            }

            fun updateEpub() {
                val generateEpub = getEpub()
                if (generateEpub) {
                    //  download_open_btt.setImageResource(R.drawable.ic_baseline_create_24)
                    download_open_btt.contentDescription = "Generate"
                } else {
                    //  download_open_btt.setImageResource(R.drawable.ic_baseline_menu_book_24)
                    download_open_btt.contentDescription = "Read"
                }
            }
            updateEpub()
            updateBar(null)

            download_open_btt.setOnClickListener {
                if (getEpub()) {
                    updateBar(true)
                    thread {
                        val done = BookDownloader.turnToEpub(card.author, card.name, card.apiName)
                        activity.runOnUiThread {
                            if (done) {
                                //Toast.makeText(context, "Created ${card.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error creating the Epub", Toast.LENGTH_LONG).show()
                            }
                            updateEpub()
                            updateBar(null)
                            DataStore.setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
                            BookDownloader.openEpub(card.name)
                            updateTxtDiff()
                        }
                    }
                } else {
                    thread {
                        if (!BookDownloader.hasEpub(card.name)) {
                            BookDownloader.turnToEpub(card.author, card.name, card.apiName)
                        }
                        activity.runOnUiThread {
                            DataStore.setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
                            BookDownloader.openEpub(card.name)
                            updateTxtDiff()
                        }
                    }
                }
            }

            download_update.setOnClickListener {
                updateDownloadFromCard(card,true)
            }

            cardView.setOnClickListener {
                MainActivity.loadResult(card.source, card.apiName)
            }

            download_delete_trash.setOnClickListener {
                val dialogClickListener =
                    DialogInterface.OnClickListener { dialog, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                BookDownloader.remove(card.author, card.name, card.apiName)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }
                val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                builder.setMessage("This will permanently delete ${card.name}.\nAre you sure?").setTitle("Delete")
                    .setPositiveButton("Delete", dialogClickListener)
                    .setNegativeButton("Cancel", dialogClickListener)
                    .show()
            }
        }
    }
}
