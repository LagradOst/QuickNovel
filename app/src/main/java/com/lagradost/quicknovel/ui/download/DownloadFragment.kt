package com.lagradost.quicknovel.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.mvvm.observe
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlin.concurrent.thread

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8

class DownloadFragment : Fragment() {
    companion object {
        fun updateDownloadFromResult(
            res: LoadResponse,
            localId: Int,
            apiName: String,
            source: String,
            pauseOngoing: Boolean = false,
        ) {
            val api = MainActivity.getApiFromName(apiName)
            DataStore.setKey(DOWNLOAD_TOTAL,
                localId.toString(),
                res.data.size) // FIX BUG WHEN DOWNLOAD IS OVER TOTAL

            DataStore.setKey(DOWNLOAD_FOLDER, BookDownloader.generateId(res, api).toString(),
                DownloadFragment.DownloadData(source,
                    res.name,
                    res.author,
                    res.posterUrl,
                    res.rating,
                    res.peopleVoted,
                    res.views,
                    res.synopsis,
                    res.tags,
                    api.name
                ))
            when (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped) {
                BookDownloader.DownloadType.IsFailed -> BookDownloader.download(res, api)
                BookDownloader.DownloadType.IsStopped -> BookDownloader.download(res, api)
                BookDownloader.DownloadType.IsDownloading -> BookDownloader.updateDownload(localId,
                    if (pauseOngoing) BookDownloader.DownloadType.IsPaused else BookDownloader.DownloadType.IsDownloading)
                BookDownloader.DownloadType.IsPaused -> BookDownloader.updateDownload(localId,
                    BookDownloader.DownloadType.IsDownloading)
                else -> println("ERROR")
            }
        }

        fun updateDownloadFromCard(card: DownloadFragment.DownloadDataLoaded, pauseOngoing: Boolean = false) {
            thread {
                val api = MainActivity.getApiFromName(card.apiName)
                val res =
                    if (DloadAdapter.cachedLoadResponse.containsKey(card.id))
                        DloadAdapter.cachedLoadResponse[card.id] else
                        api.load(card.source)
                if (res == null) {
                    /*MainActivity.activity.runOnUiThread {
                        Toast.makeText(context, "Error loading", Toast.LENGTH_SHORT).show()
                    }*/
                } else {
                    DloadAdapter.cachedLoadResponse[card.id] = res
                    val localId = card.id//BookDownloader.generateId(res, MainActivity.api)
                    updateDownloadFromResult(res, localId, card.apiName, card.source, pauseOngoing)
                }
            }
        }

    }

    private lateinit var viewModel: DownloadViewModel

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
        SotringMethod("Recently opened", LAST_ACCES_SORT),
        SotringMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SotringMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
        SotringMethod("Download count (high to low)", DOWNLOADSIZE_SORT),
        SotringMethod("Download count (low to high)", REVERSE_DOWNLOADSIZE_SORT),
        SotringMethod("Download percentage (high to low)", DOWNLOADPRECENTAGE_SORT),
        SotringMethod("Download percentage (low to high)", REVERSE_DOWNLOADPRECENTAGE_SORT),
    )

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
        viewModel.updateDownloadInfo(info)
    }

    fun removeAction(id: Int) {
        viewModel.removeActon(id)
    }

    fun updateData(data: ArrayList<DownloadFragment.DownloadDataLoaded>) {
        (download_cardSpace.adapter as DloadAdapter).cardList = data
        (download_cardSpace.adapter as DloadAdapter).notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MainActivity.fixPaddingStatusbar(downloadRoot)

        viewModel = ViewModelProviders.of(MainActivity.activity).get(DownloadViewModel::class.java)

        observe(viewModel.cards, ::updateData)
        thread {
            viewModel.loadData()
        }

        download_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_sort -> {
                    val bottomSheetDialog = BottomSheetDialog(this.context!!)
                    bottomSheetDialog.setContentView(R.layout.sort_bottom_sheet)
                    val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                    val arrayAdapter = ArrayAdapter<String>(this.context!!, R.layout.checkmark_select_dialog)
                    arrayAdapter.addAll(ArrayList(sotringMethods.map { t -> t.name }))

                    res.choiceMode = CHOICE_MODE_SINGLE
                    res.adapter = arrayAdapter
                    res.setItemChecked(sotringMethods.indexOfFirst { t -> t.id ==  viewModel.currentSortingMethod.value },true)
                    res.setOnItemClickListener { parent, view, position, id ->
                        val sel = sotringMethods[position].id
                        DataStore.setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, sel)
                        viewModel.sortData(sel)
                        bottomSheetDialog.dismiss()
                    }
                    bottomSheetDialog.show()
                }
                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }
        /*
        download_filter.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this.context!!)
            lateinit var dialog: AlertDialog
            builder.setSingleChoiceItems(sotringMethods.map { t -> t.name }.toTypedArray(),
                sotringMethods.indexOfFirst { t -> t.id ==  viewModel.currentSortingMethod.value }
            ) { _, which ->
                val id = sotringMethods[which].id
                viewModel.currentSortingMethod.postValue(id)
                DataStore.setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, id)

                dialog.dismiss()
            }
            builder.setTitle("Sorting order")
            builder.setNegativeButton("Cancel") { _, _ -> }

            dialog = builder.create()
            dialog.show()
        }*/


        swipe_container.setProgressBackgroundColorSchemeResource(R.color.darkBackground)
        swipe_container.setColorSchemeResources(R.color.colorPrimary)
        swipe_container.setOnRefreshListener {
            for (card in (download_cardSpace.adapter as DloadAdapter).cardList) {
                if ((card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                    updateDownloadFromCard(card)
                }
            }
            swipe_container.isRefreshing = false
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

        BookDownloader.downloadNotification += ::updateDownloadInfo
        BookDownloader.downloadRemove += ::removeAction
    }
}