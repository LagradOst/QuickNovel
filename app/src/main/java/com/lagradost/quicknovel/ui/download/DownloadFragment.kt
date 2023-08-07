package com.lagradost.quicknovel.ui.download

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.databinding.FragmentDownloadsBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

class DownloadFragment : Fragment() {
    private val viewModel: DownloadViewModel by viewModels()
    lateinit var binding: FragmentDownloadsBinding

    data class DownloadData(
        @JsonProperty("source")
        val source: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("author")
        val author: String?,
        @JsonProperty("posterUrl")
        val posterUrl: String?,
        //RATING IS FROM 0-100
        @JsonProperty("rating")
        val rating: Int?,
        @JsonProperty("peopleVoted")
        val peopleVoted: Int?,
        @JsonProperty("views")
        val views: Int?,
        @JsonProperty("synopsis")
        val synopsis: String?,
        @JsonProperty("tags")
        val tags: List<String>?,
        @JsonProperty("apiName")
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
        var synopsis: String?,
        var tags: List<String>?,
        var apiName: String,
        var downloadedCount: Int,
        var downloadedTotal: Int,
        var ETA: String,
        var state: DownloadState,
        val id: Int,
        var generating : Boolean,
    ) {
        val image get() = img(posterUrl)
    }

    data class SortingMethod(@StringRes val name: Int, val id: Int)

    private val sortingMethods = arrayOf(
        SortingMethod(R.string.default_sort, DEFAULT_SORT),
        SortingMethod(R.string.recently_sort, LAST_ACCES_SORT),
        SortingMethod(R.string.alpha_sort_az, ALPHA_SORT),
        SortingMethod(R.string.alpha_sort_za, REVERSE_ALPHA_SORT),
        SortingMethod(R.string.download_sort_hl, DOWNLOADSIZE_SORT),
        SortingMethod(R.string.download_sort_lh, REVERSE_DOWNLOADSIZE_SORT),
        SortingMethod(R.string.download_perc_hl, DOWNLOADPRECENTAGE_SORT),
        SortingMethod(R.string.download_perc_lh, REVERSE_DOWNLOADPRECENTAGE_SORT),
    )

    private val normalSortingMethods = arrayOf(
        SortingMethod(R.string.default_sort, DEFAULT_SORT),
        SortingMethod(R.string.recently_sort, LAST_ACCES_SORT),
        SortingMethod(R.string.alpha_sort_az, ALPHA_SORT),
        SortingMethod(R.string.alpha_sort_za, REVERSE_ALPHA_SORT),
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
            binding.bookmarkCardSpace.spanCount = spanCountLandscape
        } else {
            binding.downloadCardSpace.spanCount = spanCountPortrait
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
        activity?.fixPaddingStatusbar(binding.downloadToolbar)

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
                        arrayAdapter.addAll(ArrayList(sortingMethods.map { t -> getString(t.name)  }))
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
                        arrayAdapter.addAll(ArrayList(normalSortingMethods.map { t -> getString(t.name) }))
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
            setColorSchemeColors(context.colorFromAttribute(R.attr.colorPrimary))
            setProgressBackgroundColorSchemeColor(context.colorFromAttribute(R.attr.primaryGrayBackground))
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