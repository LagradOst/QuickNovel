package com.lagradost.quicknovel.ui.search

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.databinding.FragmentSearchBinding
import com.lagradost.quicknovel.databinding.HomeEpisodesExpandedBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.normalSafeApiCall
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.settings.SettingsFragment
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

class SearchFragment : Fragment() {
    lateinit var binding: FragmentSearchBinding
    private val viewModel: SearchViewModel by viewModels()

    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1
        var currentDialog: Dialog? = null

        fun loadHomepageList(viewModel: SearchViewModel, item: HomePageList) {
            if (currentDialog != null) return
            val act = activity ?: return

            val bottomSheetDialog = BottomSheetDialog(act)
            val binding = HomeEpisodesExpandedBinding.inflate(act.layoutInflater, null, false)
            bottomSheetDialog.setContentView(binding.root)

            binding.homeExpandedText.text = item.name
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialog.dismiss()
            }


            binding.homeExpandedRecycler.apply {
                val searchAdapter = SearchAdapter2(viewModel, binding.homeExpandedRecycler)
                searchAdapter.submitList(item.list)
                adapter = searchAdapter
                spanCount = currentSpan
            }

            val spanListener = { span: Int ->
                binding.homeExpandedRecycler.spanCount = span
            }

            configEvent += spanListener

            bottomSheetDialog.setOnDismissListener {
                configEvent -= spanListener
                currentDialog = null
            }
            currentDialog = bottomSheetDialog
            bottomSheetDialog.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        binding = FragmentSearchBinding.inflate(inflater)
        return binding.root
    }

    private fun fixGrid() {
        val compactView = false//activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }
        binding.searchAllRecycler.spanCount = currentSpan
        configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    lateinit var searchExitIcon: ImageView
    lateinit var searchMagIcon: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val masterAdapter = ParentItemAdapter2(viewModel)
        val allAdapter = SearchAdapter2(viewModel, binding.searchAllRecycler)
        binding.searchAllRecycler.adapter = allAdapter
        binding.searchMasterRecycler.apply {
            adapter = masterAdapter
            layoutManager = GridLayoutManager(context, 1)
        }

        observe(viewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        allAdapter.submitList(data)
                    }
                    searchExitIcon.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    binding.searchLoadingBar.alpha = 1f
                }
            }
        }

        observe(viewModel.currentSearch) { list ->
            normalSafeApiCall {
                masterAdapter.submitList(list.map {
                    HomePageList(
                        it.apiName,
                        if (it.data is Resource.Success) it.data.value else emptyList()
                    )
                })
            }
        }

        activity?.fixPaddingStatusbar(binding.searchRoot)

        fixGrid()
        binding.searchLoadingBar.alpha = 0f
        searchExitIcon = binding.mainSearch.findViewById(androidx.appcompat.R.id.search_close_btn)
        searchMagIcon = binding.mainSearch.findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f

        binding.searchFilter.setOnClickListener {
            SettingsFragment.showSearchProviders(it.context)
        }

        binding.mainSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchExitIcon.alpha = 0f
                binding.searchLoadingBar.alpha = 1f
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        binding.mainSearch.setOnQueryTextFocusChangeListener { searchView, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                searchView.doOnLayout {
                    val imm: InputMethodManager? =
                        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager?
                    imm?.showSoftInput(searchView.findFocus(), 0)
                }
            }
        }
        binding.mainSearch.onActionViewExpanded()


        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) == true
        binding.searchMasterRecycler.isVisible = isAdvancedSearch
        binding.searchAllRecycler.isGone = isAdvancedSearch

        /*
        thread {
            searchDowloads.clear()
            val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
            for (k in keys) {
                val data = DataStore.getKey<DownloadFragment.DownloadData>(k)
                if (data != null) {
                    val info = requireContext().downloadInfo(data.author, data.name, 100000, data.apiName)
                    if(info != null && info.progress > 0) {
                        searchDowloads.add(data)
                    }
                }
            }
        }*/
    }
}