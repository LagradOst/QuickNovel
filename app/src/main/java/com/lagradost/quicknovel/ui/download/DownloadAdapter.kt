package com.lagradost.quicknovel.ui.download

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.hasEpub
import com.lagradost.quicknovel.BookDownloader.openEpub
import com.lagradost.quicknovel.BookDownloader.remove
import com.lagradost.quicknovel.BookDownloader.turnToEpub
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.ui.download.DownloadHelper.updateDownloadFromCard
import com.lagradost.quicknovel.util.Coroutines
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.download_result_compact.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class DownloadAdapter(
    val activity: Activity,
    var cardList: ArrayList<DownloadFragment.DownloadDataLoaded>,
    private val resView: AutofitRecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout =
            if (activity.getDownloadIsCompact()) R.layout.download_result_compact else R.layout.download_result_grid
        return DownloadCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            activity,
            resView
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

    /*
    companion object {
        val cachedLoadResponse = hashMapOf<Int, LoadResponse>()
    }*/

    class DownloadCardViewHolder
    constructor(itemView: View, activity: Activity, resView: AutofitRecyclerView) : RecyclerView.ViewHolder(itemView) {
        private val localActivity = activity
        val cardView: ImageView = itemView.imageView
        private val cardText = itemView.imageText
        private val downloadProgressText: TextView? = itemView.download_progress_text
        private val downloadProgressbar: ProgressBar? = itemView.download_progressbar
        private val downloadUpdate: ImageView? = itemView.download_update
        private val downloadOpenBtt: LinearLayout? = itemView.download_open_btt
        private val backgroundCard: CardView? = itemView.backgroundCard
        private val downloadProgressbarIndeterminate: ProgressBar? = itemView.download_progressbar_indeterment
        private val downloadDeleteTrash: ImageView? = itemView.download_delete_trash
        private val imageTextMore: TextView? = itemView.imageTextMore
        private val compactView = localActivity.getDownloadIsCompact()

        private val coverHeight: Int = if (compactView) 100.toPx else (resView.itemWidth / 0.68).roundToInt()

        // val cardTextExtra: TextView = itemView.imageTextExtra
        // val bg = itemView.backgroundCard
        @SuppressLint("SetTextI18n")
        fun bind(card: DownloadFragment.DownloadDataLoaded) {
            // val api = getApiFromName(card.apiName)

            cardView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
            }

            cardText.text = card.name
            downloadProgressText?.text =
                "${card.downloadedCount}/${card.downloadedTotal}" + if (card.ETA == "") "" else " - ${card.ETA}"

            // ANIMATION PROGRESSBAR
            if (downloadProgressbar != null) {
                downloadProgressbar.max = card.downloadedTotal * 100

                if (downloadProgressbar.progress != 0) {
                    val animation: ObjectAnimator = ObjectAnimator.ofInt(downloadProgressbar,
                        "progress",
                        downloadProgressbar.progress,
                        card.downloadedCount * 100)

                    animation.duration = 500
                    animation.setAutoCancel(true)
                    animation.interpolator = DecelerateInterpolator()
                    animation.start()
                } else {
                    downloadProgressbar.progress = card.downloadedCount * 100
                }
                //download_progressbar.progress = card.downloadedCount
                downloadProgressbar.alpha = if (card.downloadedCount >= card.downloadedTotal) 0f else 1f
            }

            var realState = card.state
            if (downloadUpdate != null) {
                if (card.downloadedCount >= card.downloadedTotal && card.updated) {
                    downloadUpdate.alpha = 0.5f
                    downloadUpdate.isEnabled = false
                    realState = BookDownloader.DownloadType.IsDone
                } else {
                    downloadUpdate.alpha = 1f
                    downloadUpdate.isEnabled = true
                }
            }
            cardView.setImage(card.posterUrl)

            if (downloadUpdate != null) {
                downloadUpdate.contentDescription = when (realState) {
                    BookDownloader.DownloadType.IsDone -> "Done"
                    BookDownloader.DownloadType.IsDownloading -> "Pause"
                    BookDownloader.DownloadType.IsPaused -> "Resume"
                    BookDownloader.DownloadType.IsFailed -> "Re-Download"
                    BookDownloader.DownloadType.IsStopped -> "Update"
                }

                downloadUpdate.setImageResource(when (realState) {
                    BookDownloader.DownloadType.IsDownloading -> R.drawable.ic_baseline_pause_24
                    BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
                    BookDownloader.DownloadType.IsStopped -> R.drawable.ic_baseline_autorenew_24
                    BookDownloader.DownloadType.IsFailed -> R.drawable.ic_baseline_autorenew_24
                    BookDownloader.DownloadType.IsDone -> R.drawable.ic_baseline_check_24
                })
            }

            fun getDiff(): Int {
                val downloaded = localActivity.getKey(DOWNLOAD_EPUB_SIZE, card.id.toString(), 0)!!
                return card.downloadedCount - downloaded
            }

            fun getEpub(): Boolean {
                return getDiff() != 0
            }

            fun updateTxtDiff() {
                val diff = getDiff()
                imageTextMore?.text = if (diff > 0) "+$diff "
                else ""
            }

            updateTxtDiff()

            fun updateBar(isGenerating: Boolean? = null) {
                val isIndeterminate = isGenerating ?: BookDownloader.isTurningIntoEpub.containsKey(card.id)
                downloadProgressbar?.visibility = if (!isIndeterminate) View.VISIBLE else View.INVISIBLE
                downloadProgressbarIndeterminate?.visibility = if (isIndeterminate) View.VISIBLE else View.INVISIBLE
            }

            fun updateEpub() {
                val generateEpub = getEpub()
                if (generateEpub) {
                    //  download_open_btt.setImageResource(R.drawable.ic_baseline_create_24)
                    downloadOpenBtt?.contentDescription = "Generate"
                } else {
                    //  download_open_btt.setImageResource(R.drawable.ic_baseline_menu_book_24)
                    downloadOpenBtt?.contentDescription = "Read"
                }
            }
            updateEpub()
            updateBar(null)

            fun handleRead() {
                if (getEpub()) {
                    updateBar(true)
                    Coroutines.main {
                        val done = withContext(Dispatchers.IO) {
                            localActivity.turnToEpub(card.author, card.name, card.apiName)
                        }

                        if (done) {
                            //Toast.makeText(context, "Created ${card.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(localActivity, "Error creating the Epub", Toast.LENGTH_LONG).show()
                        }
                        updateEpub()
                        updateBar(null)
                        localActivity.setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
                        localActivity.openEpub(card.name)
                        updateTxtDiff()
                    }
                } else {
                    Coroutines.main {
                        withContext(Dispatchers.IO) {
                            if (!localActivity.hasEpub(card.name)) {
                                localActivity.turnToEpub(card.author, card.name, card.apiName)
                            }
                        }

                        localActivity.setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
                        localActivity.openEpub(card.name)
                        updateTxtDiff()
                    }
                }
            }

            fun handleSource() {
                (localActivity as AppCompatActivity).loadResult(card.source, card.apiName)
            }

            fun handleUpdate() {
                updateDownloadFromCard(localActivity, card, true)
            }

            fun handleDelete() {
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                localActivity.remove(card.author, card.name, card.apiName)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }
                val builder: AlertDialog.Builder = AlertDialog.Builder(localActivity)
                builder.setMessage("This will permanently delete ${card.name}.\nAre you sure?").setTitle("Delete")
                    .setPositiveButton("Delete", dialogClickListener)
                    .setNegativeButton("Cancel", dialogClickListener)
                    .show()
            }

            if (compactView) {
                downloadOpenBtt?.setOnClickListener {
                    handleRead()
                }
                cardView.setOnClickListener {
                    handleSource()
                }
            } else {
                backgroundCard?.setOnClickListener {
                    handleRead()
                }
                backgroundCard?.setOnLongClickListener {
                    val items = listOf(
                        Triple(0, R.drawable.ic_baseline_menu_book_24, R.string.download_read_action),
                        Triple(1, R.drawable.ic_baseline_open_in_new_24, R.string.download_open_action),
                        Triple(2, R.drawable.ic_baseline_autorenew_24, R.string.download_update_action),
                        Triple(3, R.drawable.ic_baseline_delete_outline_24, R.string.download_delete_action)
                    )
                    it.popupMenu(
                        items = items,
                        //   ?: preferences.defaultOrientationType(),
                    ) {
                        when(itemId) {
                            0 -> handleRead()
                            1 -> handleSource()
                            2 -> handleUpdate()
                            3 -> handleDelete()
                        }
                    }
                    return@setOnLongClickListener true
                }
            }

            downloadUpdate?.setOnClickListener {
                handleUpdate()
            }

            downloadDeleteTrash?.setOnClickListener {
                handleDelete()
            }
        }
    }
}
