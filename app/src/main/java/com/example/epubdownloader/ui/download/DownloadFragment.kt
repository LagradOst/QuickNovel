package com.example.epubdownloader.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epubdownloader.*
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlinx.android.synthetic.main.fragment_search.*

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
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    fun loadData() {
        val arry = ArrayList<DownloadDataLoaded>()
        val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
        for (k in keys) {
            val res = DataStore.getKey<DownloadData>(k) as DownloadData?
            if (res != null) {
                val localId = BookDownloader.generateId(res.apiName, res.author, res.name)
                val info = BookDownloader.downloadInfo(res.author, res.name, 100000, res.apiName)
                if (info != null && info.progress > 0) {
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
                        DataStore.getKey(DOWNLOAD_TOTAL, localId.toString(), info.progress)!!,
                        false
                    ))
                }
            }
        }

        (download_cardSpace.adapter as DloadAdapter).cardList = arry
        (download_cardSpace.adapter as DloadAdapter).notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            DloadAdapter(
                it,
                ArrayList<DownloadDataLoaded>(),
                download_cardSpace,
            )
        }
        download_cardSpace.adapter = adapter
        download_cardSpace.layoutManager = GridLayoutManager(context, 1)

        val parameter = download_top_padding.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        download_top_padding.layoutParams = parameter

        loadData()
    }
}