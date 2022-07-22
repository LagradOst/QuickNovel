package com.lagradost.quicknovel.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.createQuickStream
import com.lagradost.quicknovel.BookDownloader.openQuickStream
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.history_result_compact.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class CachedAdapter(
    val activity: Activity,
    var cardList: ArrayList<ResultCached>,
    private val resView: AutofitRecyclerView,
    private val updateCallback: (id: Int) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.history_result_compact
        //if (activity.getDownloadIsCompact()) R.layout.download_result_compact else R.layout.download_result_grid
        return DownloadCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            activity,
            resView,
            updateCallback
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

    class DownloadCardViewHolder
    constructor(itemView: View, activity: Activity, resView: AutofitRecyclerView, updateCallback: (id: Int) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val localActivity = activity
        val cardView: ImageView = itemView.imageView
        private val cardText = itemView.imageText
        private val downloadOpenBtt: FrameLayout = itemView.history_open_btt
        private val backgroundCard: CardView = itemView.backgroundCard
        private val deleteTrash: ImageView = itemView.history_delete
        private val playStreamRead: ImageView = itemView.history_play
        private val imageTextMore: TextView = itemView.history_extra_text
        private val compactView = true //localActivity.getDownloadIsCompact()
        val callback = updateCallback

        private val coverHeight: Int = if (compactView) 100.toPx else (resView.itemWidth / 0.68).roundToInt()

        @SuppressLint("SetTextI18n")
        fun bind(card: ResultCached) {
            cardView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
            }

            cardText.text = card.name
            imageTextMore.text = "${card.totalChapters} Chapters"

            cardView.setImage(card.poster)

            fun handleSource() {
                (localActivity as AppCompatActivity).loadResult(card.source, card.apiName)
            }

            fun handleRead() {
                main {
                    val data = withContext(Dispatchers.IO) {
                        val api = Apis.getApiFromName(card.apiName)
                        api.load(card.source)
                    }
                    if (data is Resource.Success) {
                        val res = data.value

                        if (res.data.isEmpty()) {
                            Toast.makeText(localActivity, R.string.no_chapters_found, Toast.LENGTH_SHORT).show()
                            return@main
                        }

                        val uri = withContext(Dispatchers.IO) {
                            localActivity.createQuickStream(
                                BookDownloader.QuickStreamData(
                                    BookDownloader.QuickStreamMetaData(
                                        res.author,
                                        res.name,
                                        card.apiName,
                                    ),
                                    res.posterUrl,
                                    res.data.toMutableList()
                                )
                            )
                        }
                        localActivity.openQuickStream(uri)
                    } else {
                        localActivity.let { ctx ->
                            Toast.makeText(ctx, "Error Loading Novel", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            fun handleDelete() {
                val dialogClickListener =
                    DialogInterface.OnClickListener { _, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                localActivity.removeKey(RESULT_BOOKMARK, card.id.toString())
                                localActivity.removeKey(RESULT_BOOKMARK_STATE, card.id.toString())
                                callback.invoke(card.id)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                            }
                        }
                    }
                val builder: AlertDialog.Builder = AlertDialog.Builder(localActivity)
                builder.setMessage("This remove ${card.name} from your bookmarks.\nAre you sure?").setTitle("Remove")
                    .setPositiveButton("Remove", dialogClickListener)
                    .setNegativeButton("Cancel", dialogClickListener)
                    .show()
            }

            if (compactView) {
                playStreamRead.setOnClickListener {
                    handleRead()
                }
                cardView.setOnClickListener {
                    handleSource()
                }
            } else {
                backgroundCard.setOnClickListener {
                    handleRead()
                }
                backgroundCard.setOnLongClickListener {
                    val items = listOf(
                        Triple(0, R.drawable.ic_baseline_menu_book_24, R.string.download_read_action),
                        Triple(1, R.drawable.ic_baseline_open_in_new_24, R.string.download_open_action),
                        Triple(3, R.drawable.ic_baseline_delete_outline_24, R.string.download_delete_action)
                    )
                    it.popupMenu(
                        items = items,
                        //   ?: preferences.defaultOrientationType(),
                    ) {
                        when (itemId) {
                            0 -> handleRead()
                            1 -> handleSource()
                            3 -> handleDelete()
                        }
                    }
                    return@setOnLongClickListener true
                }
            }

            deleteTrash.setOnClickListener {
                handleDelete()
            }
        }
    }
}
