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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.DownloadHelper.updateDownloadFromCard
import com.lagradost.quicknovel.ui.history.HistoryAdapter
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlinx.android.synthetic.main.fragment_result.*
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
        val tags: List<String>?,
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
        val tags: List<String>?,
        val apiName: String,
        val downloadedCount: Int,
        val downloadedTotal: Int,
        val updated: Boolean,
        val ETA: String,
        val state: BookDownloader.DownloadType,
        val id: Int,
    )

    data class SortingMethod(val name: String, val id: Int)

    private val sortingMethods = arrayOf(
        SortingMethod("Default", DEFAULT_SORT),
        SortingMethod("Recently opened", LAST_ACCES_SORT),
        SortingMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SortingMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
        SortingMethod("Download count (high to low)", DOWNLOADSIZE_SORT),
        SortingMethod("Download count (low to high)", REVERSE_DOWNLOADSIZE_SORT),
        SortingMethod("Download percentage (high to low)", DOWNLOADPRECENTAGE_SORT),
        SortingMethod("Download percentage (low to high)", REVERSE_DOWNLOADPRECENTAGE_SORT),
    )

    private val normalSortingMethods = arrayOf(
        SortingMethod("Default", DEFAULT_SORT),
        SortingMethod("Recently opened", LAST_ACCES_SORT),
        SortingMethod("Alphabetical (A-Z)", ALPHA_SORT),
        SortingMethod("Alphabetical (Z-A)", REVERSE_ALPHA_SORT),
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
        download_cardSpace?.let {
            (it.adapter as DownloadAdapter).updateList(data)
        }
    }

    private fun updateNormalData(data: ArrayList<ResultCached>) {
        bookmark_cardSpace?.let {
            (it.adapter as CachedAdapter).updateList(data)
        }
    }

    private fun setupGridView() {
        val compactView = requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        if (download_cardSpace != null) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                download_cardSpace.spanCount = spanCountLandscape
            } else {
                download_cardSpace.spanCount = spanCountPortrait
            }
        }
        if (bookmark_cardSpace != null) {
            val compactBookmarkView = true
            val bookmarkSpanCountLandscape = if (compactBookmarkView) 2 else 6
            val bookmarkSpanCountPortrait = if (compactBookmarkView) 1 else 3
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                bookmark_cardSpace.spanCount = bookmarkSpanCountLandscape
            } else {
                bookmark_cardSpace.spanCount = bookmarkSpanCountPortrait
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    var isOnDownloads = true
    var currentReadType: ReadType? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(downloadRoot)

        viewModel = ViewModelProviders.of(MainActivity.activity).get(DownloadViewModel::class.java)

        observe(viewModel.cards, ::updateData)
        observe(viewModel.normalCards, ::updateNormalData)

        viewModel.loadData(requireContext())

        observe(viewModel.isOnDownloads) {
            isOnDownloads = it
            bookmark_cardSpace?.visibility = if (!it) View.VISIBLE else View.GONE
            swipe_container?.visibility = if (it) View.VISIBLE else View.GONE
        }


        val readList = arrayListOf(
            ReadType.READING,
            ReadType.ON_HOLD,
            ReadType.PLAN_TO_READ,
            ReadType.COMPLETED,
            ReadType.DROPPED,
        )
        bookmark_tabs.addTab(bookmark_tabs.newTab().setText(getString(R.string.tab_downloads)))
        for (read in readList) {
            bookmark_tabs.addTab(bookmark_tabs.newTab().setText(getString(read.stringRes)))
        }

        bookmark_tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position
                if (pos != null) {
                    context?.let { ctx ->
                        if (pos == 0) {
                            viewModel.loadData(ctx)
                        } else {
                            viewModel.loadNormalData(ctx, readList[pos - 1])
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        download_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_sort -> {
                    val bottomSheetDialog = BottomSheetDialog(requireContext())
                    bottomSheetDialog.setContentView(R.layout.sort_bottom_sheet)
                    val res = bottomSheetDialog.findViewById<ListView>(R.id.sort_click)!!
                    val arrayAdapter = ArrayAdapter<String>(
                        requireContext(),
                        R.layout.sort_bottom_single_choice
                    ) // checkmark_select_dialog
                    res.choiceMode = CHOICE_MODE_SINGLE

                    if (isOnDownloads) {
                        arrayAdapter.addAll(ArrayList(sortingMethods.map { t -> t.name }))
                        res.adapter = arrayAdapter

                        res.setItemChecked(
                            sortingMethods.indexOfFirst { t -> t.id == viewModel.currentSortingMethod.value },
                            true
                        )
                        res.setOnItemClickListener { _, _, position, _ ->
                            val sel = sortingMethods[position].id
                            context?.let { ctx ->
                                ctx.setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, sel)
                                viewModel.sortData(ctx, sel)
                            }
                            bottomSheetDialog.dismiss()
                        }
                    } else {
                        arrayAdapter.addAll(ArrayList(normalSortingMethods.map { t -> t.name }))
                        res.adapter = arrayAdapter

                        res.setItemChecked(
                            normalSortingMethods.indexOfFirst { t -> t.id == viewModel.currentNormalSortingMethod.value },
                            true
                        )
                        res.setOnItemClickListener { _, _, position, _ ->
                            val sel = normalSortingMethods[position].id

                            context?.let { ctx ->
                                ctx.setKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, sel)
                                viewModel.sortNormalData(ctx, sel)
                            }
                            bottomSheetDialog.dismiss()
                        }
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
                download_cardSpace,
            )
        }

        observe(viewModel.currentReadType) {
            currentReadType = it
        }

        val normalAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            CachedAdapter(
                it,
                download_cardSpace,
            ) {
                val type = currentReadType
                if (type != null) {
                    context?.let { ctx ->
                        viewModel.loadNormalData(ctx, type)
                    }
                }
            }
        }

        adapter?.setHasStableIds(true)
        normalAdapter?.setHasStableIds(true)
        download_cardSpace.adapter = adapter
        bookmark_cardSpace.adapter = normalAdapter
        val animator: ItemAnimator = download_cardSpace.itemAnimator!!
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        val normalAnimator: ItemAnimator = bookmark_cardSpace.itemAnimator!!
        if (normalAnimator is SimpleItemAnimator) {
            normalAnimator.supportsChangeAnimations = false
        }

        BookDownloader.downloadNotification += ::updateDownloadInfo
        BookDownloader.downloadRemove += ::removeAction
    }
}