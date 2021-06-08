package com.lagradost.quicknovel.ui.download

import android.content.res.Configuration
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
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.download.DownloadHelper.updateDownloadFromCard
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlinx.android.synthetic.main.fragment_search.*
import kotlin.concurrent.thread

class DownloadFragment : Fragment() {
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

    private fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        viewModel.updateDownloadInfo(info)
    }

    private fun removeAction(id: Int) {
        viewModel.removeActon(id)
    }

    private fun updateData(data: ArrayList<DownloadDataLoaded>) {
        (download_cardSpace.adapter as DownloadAdapter).cardList = data
        (download_cardSpace.adapter as DownloadAdapter).notifyDataSetChanged()
    }

    private fun setupGridView() {
        val compactView = requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if(download_cardSpace == null) return
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            download_cardSpace.spanCount = spanCountLandscape
        } else {
            download_cardSpace.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(downloadRoot)

        viewModel = ViewModelProviders.of(MainActivity.activity).get(DownloadViewModel::class.java)

        observe(viewModel.cards, ::updateData)
        thread {
            viewModel.loadData(requireContext())
        }

        download_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_sort -> {
                    val bottomSheetDialog = BottomSheetDialog(requireContext())
                    bottomSheetDialog.setContentView(R.layout.sort_bottom_sheet)
                    val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                    val arrayAdapter = ArrayAdapter<String>(requireContext(), R.layout.checkmark_select_dialog)
                    arrayAdapter.addAll(ArrayList(sotringMethods.map { t -> t.name }))

                    res.choiceMode = CHOICE_MODE_SINGLE
                    res.adapter = arrayAdapter
                    res.setItemChecked(sotringMethods.indexOfFirst { t -> t.id == viewModel.currentSortingMethod.value },
                        true)
                    res.setOnItemClickListener { _, _, position, _ ->
                        val sel = sotringMethods[position].id
                        requireContext().setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, sel)
                        viewModel.sortData(requireContext(), sel)
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

        swipe_container.setProgressBackgroundColorSchemeColor(requireContext().colorFromAttribute(R.attr.darkBackground))
        swipe_container.setColorSchemeColors(requireContext().colorFromAttribute(R.attr.colorPrimary))
        swipe_container.setOnRefreshListener {
            for (card in (download_cardSpace.adapter as DownloadAdapter).cardList) {
                if ((card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                    updateDownloadFromCard(requireContext(), card)
                }
            }
            swipe_container.isRefreshing = false
        }

        setupGridView()
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            DownloadAdapter(
                it,
                ArrayList(),
                download_cardSpace,
            )
        }

        adapter?.setHasStableIds(true)
        download_cardSpace.adapter = adapter
        val animator: ItemAnimator = download_cardSpace.itemAnimator!!
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        BookDownloader.downloadNotification += ::updateDownloadInfo
        BookDownloader.downloadRemove += ::removeAction
    }
}