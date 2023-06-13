package com.lagradost.quicknovel.ui.download

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.databinding.FragmentDownloadsBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

class DownloadFragment : Fragment() {
    private val viewModel: DownloadViewModel by viewModels()
    lateinit var binding: FragmentDownloadsBinding

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
        var source: String,
        var name: String,
        var author: String?,
        var posterUrl: String?,
        //RATING IS FROM 0-100
        var rating: Int?,
        var peopleVoted: Int?,
        var views: Int?,
        var Synopsis: String?,
        var tags: List<String>?,
        var apiName: String,
        var downloadedCount: Int,
        var downloadedTotal: Int,
        var ETA: String,
        var state: BookDownloader2Helper.DownloadState,
        val id: Int,
        var generating : Boolean,
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
    ): View {
        binding = FragmentDownloadsBinding.inflate(inflater)
        return binding.root
        //return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    private fun setupGridView() {
        val compactView = requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.downloadCardSpace.spanCount = spanCountLandscape
        } else {
            binding.downloadCardSpace.spanCount = spanCountPortrait
        }

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.bookmarkCardSpace.spanCount = spanCountLandscape
        } else {
            binding.bookmarkCardSpace.spanCount = spanCountPortrait
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
        activity?.fixPaddingStatusbar(binding.downloadRoot)

        //viewModel = ViewModelProviders.of(activity!!).get(DownloadViewModel::class.java)

        observe(viewModel.isOnDownloads) { onDownloads ->
            isOnDownloads = onDownloads
            binding.bookmarkCardSpace.isVisible = !onDownloads
            binding.swipeContainer.isVisible = onDownloads
        }


        val readList = arrayListOf(
            ReadType.READING,
            ReadType.ON_HOLD,
            ReadType.PLAN_TO_READ,
            ReadType.COMPLETED,
            ReadType.DROPPED,
        )
        binding.bookmarkTabs.apply {
            addTab(newTab().setText(getString(R.string.tab_downloads)))
            for (read in readList) {
                addTab(newTab().setText(getString(read.stringRes)))
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val pos = tab?.position
                    if (pos != null) {
                        if(pos > 0) {
                            viewModel.loadNormalData(readList[pos - 1])
                        } else {
                            viewModel.loadData()
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }

        binding.downloadToolbar.setOnMenuItemClickListener {
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
                            setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, sel)
                            viewModel.sortData(sel)

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

                            setKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, sel)
                            viewModel.sortNormalData(sel)

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

        //swipe_container.setProgressBackgroundColorSchemeColor(requireContext().colorFromAttribute(R.attr.darkBackground))

        binding.swipeContainer.apply {
            setColorSchemeColors(requireContext().colorFromAttribute(R.attr.colorPrimary))
            setOnRefreshListener {
                viewModel.refresh()
                isRefreshing = false
            }
        }

        setupGridView()

        binding.downloadCardSpace.apply {
            itemAnimator?.changeDuration = 0
            val downloadAdapter = DownloadAdapter2(viewModel, this)
            downloadAdapter.setHasStableIds(true)
            adapter = downloadAdapter
            observe(viewModel.downloadCards) { cards ->
                // we need to copy here because otherwise diff wont work
                downloadAdapter.submitList(cards.map { it.copy() })
            }
        }

        binding.bookmarkCardSpace.apply {
            val bookmarkAdapter = CachedAdapter2(viewModel, this)
            adapter = bookmarkAdapter
            observe(viewModel.normalCards) { cards ->
                bookmarkAdapter.submitList(cards.map { it.copy() })
            }
        }

        observe(viewModel.currentReadType) {
            currentReadType = it
        }
    }
}