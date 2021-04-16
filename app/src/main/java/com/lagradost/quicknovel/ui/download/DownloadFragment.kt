package com.lagradost.quicknovel.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.*
import kotlinx.android.synthetic.main.fragment_downloads.*
import androidx.recyclerview.widget.SimpleItemAnimator

import androidx.recyclerview.widget.RecyclerView.ItemAnimator

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6

class DownloadFragment : Fragment() {
    data class DownloadData(
        val source: String,
        val name: String,
        val author: String?,
        val posterUrl: String?,
        //RATING IS FROM 0-100
        val rating: Int?,
        val peopleVoted: Int?,
        val views: Int?,
        val Synopsis: String?,
        val tags: ArrayList<String>?,
        val apiName: String,
    )

    data class DownloadDataLoaded(
        val source: String,
        val name: String,
        val author: String?,
        val posterUrl: String?,
        //RATING IS FROM 0-100
        val rating: Int?,
        val peopleVoted: Int?,
        val views: Int?,
        val Synopsis: String?,
        val tags: ArrayList<String>?,
        val apiName: String,
        val downloadedCount: Int,
        val downloadedTotal: Int,
        val updated: Boolean,
        val ETA: String,
        val state: BookDownloader.DownloadType,
        val id: Int,
    )

    data class SotringMethod(val name: String, val id: Int)

    private val sotringMethods = arrayOf(
        SotringMethod("Default", DEFAULT_SORT),
        SotringMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SotringMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
        SotringMethod("Download count, (high to low)", DOWNLOADSIZE_SORT),
        SotringMethod("Download count, (low to high)", REVERSE_DOWNLOADSIZE_SORT),
        SotringMethod("Download percentage, (high to low)", DOWNLOADPRECENTAGE_SORT),
        SotringMethod("Download percentage, (low to high)", REVERSE_DOWNLOADPRECENTAGE_SORT),
    )
    val standardSotringMethod = sotringMethods[0].id
    var currentSortingMethod = standardSotringMethod

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    override fun onDestroy() {
        BookDownloader.downloadNotification -= ::updateDownloadInfo
        BookDownloader.downloadRemove -= ::removeAction
        super.onDestroy()
    }

    fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        val arry = (download_cardSpace.adapter as DloadAdapter).cardList
        var index = 0
        for (res in arry) {
            if (res.id == info.id) {
                (download_cardSpace.adapter as DloadAdapter).cardList[index] =
                    DownloadDataLoaded(
                        res.source,
                        res.name,
                        res.author,
                        res.posterUrl,
                        res.rating,
                        res.peopleVoted,
                        res.views,
                        res.Synopsis,
                        res.tags,
                        res.apiName,
                        info.progress,
                        maxOf(info.progress, info.total), //IDK Bug fix ?
                        true,
                        info.ETA,
                        info.state,
                        res.id,
                    )
                activity?.runOnUiThread {
                    if (download_cardSpace != null) {
                        (download_cardSpace.adapter as DloadAdapter).notifyItemChanged(index)
                    }
                }
                break
            }
            index++
        }
    }

    fun removeAction(id: Int) {
        loadData()
    }

    fun loadData() {
        val arry = ArrayList<DownloadDataLoaded>()
        val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
        for (k in keys) {
            val res =
                DataStore.getKey<DownloadData>(k) // THIS SHIT LAGS THE APPLICATION IF ON MAIN THREAD (IF NOT WARMED UP BEFOREHAND, SEE @WARMUP)
            if (res != null) {
                val localId = BookDownloader.generateId(res.apiName, res.author, res.name)
                val info = BookDownloader.downloadInfo(res.author, res.name, 100000, res.apiName)
                if (info != null && info.progress > 0) {
                    val state =
                        (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped)!!
                    arry.add(DownloadDataLoaded(
                        res.source,
                        res.name,
                        res.author,
                        res.posterUrl,
                        res.rating,
                        res.peopleVoted,
                        res.views,
                        res.Synopsis,
                        res.tags,
                        res.apiName,
                        info.progress,
                        maxOf(info.progress,
                            DataStore.getKey(DOWNLOAD_TOTAL, localId.toString(), info.progress)!!), //IDK Bug fix ?
                        false,
                        "",
                        state,
                        info.id,
                    ))
                }
            }
        }
        val newArry: ArrayList<DownloadDataLoaded> = when (currentSortingMethod) {
            DEFAULT_SORT -> arry
            ALPHA_SORT -> {
                arry.sortBy { t -> t.name }
                arry
            }
            REVERSE_ALPHA_SORT -> {
                arry.sortBy { t -> t.name }
                arry.reverse()
                arry
            }
            DOWNLOADSIZE_SORT -> {
                arry.sortBy { t -> -t.downloadedCount }
                arry
            }
            REVERSE_DOWNLOADSIZE_SORT -> {
                arry.sortBy { t -> t.downloadedCount }
                arry
            }
            DOWNLOADPRECENTAGE_SORT -> {
                arry.sortBy { t -> -t.downloadedCount.toFloat() / t.downloadedTotal }
                arry
            }
            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                arry.sortBy { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                arry
            }
            else -> arry
        }

        activity?.runOnUiThread {
            (download_cardSpace.adapter as DloadAdapter).cardList = newArry
            (download_cardSpace.adapter as DloadAdapter).notifyDataSetChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentSortingMethod = DataStore.getKey("SearchSettings", ::currentSortingMethod.name, standardSotringMethod)
            ?: standardSotringMethod

        download_filter.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this.context!!)
            lateinit var dialog: AlertDialog
            builder.setSingleChoiceItems(sotringMethods.map { t -> t.name }.toTypedArray(),
                sotringMethods.indexOfFirst { t -> t.id == currentSortingMethod }
            ) { _, which ->
                currentSortingMethod = sotringMethods[which].id
                DataStore.setKey("SearchSettings", ::currentSortingMethod.name, currentSortingMethod)

                loadData()
                dialog.dismiss()
            }
            builder.setTitle("Sorting order")
            builder.setNegativeButton("Cancel") { _, _ -> }

            dialog = builder.create()
            dialog.show()
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            DloadAdapter(
                it,
                ArrayList<DownloadDataLoaded>(),
                download_cardSpace,
            )
        }

        adapter?.setHasStableIds(true)
        download_cardSpace.adapter = adapter
        val animator: ItemAnimator = download_cardSpace.getItemAnimator()!!
        if (animator is SimpleItemAnimator) {
            (animator as SimpleItemAnimator).supportsChangeAnimations = false
        }


        download_cardSpace.layoutManager = GridLayoutManager(context, 1)

        val parameter = download_top_padding.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        download_top_padding.layoutParams = parameter

        //thread {
        loadData() // CAN BE DONE ON ANOTHER THREAD
        //}

        BookDownloader.downloadNotification += ::updateDownloadInfo
        BookDownloader.downloadRemove += ::removeAction
    }
}