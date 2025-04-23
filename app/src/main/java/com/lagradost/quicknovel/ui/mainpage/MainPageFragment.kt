package com.lagradost.quicknovel.ui.mainpage

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.FilterBottomSheetBinding
import com.lagradost.quicknovel.databinding.FragmentMainpageBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar


class MainPageFragment : Fragment() {
    lateinit var binding: FragmentMainpageBinding
    private val viewModel: MainPageViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentMainpageBinding.inflate(inflater)
        return binding.root
    }

    companion object {
        fun newInstance(
            apiName: String,
            mainCategory: Int? = null,
            orderBy: Int? = null,
            tag: Int? = null
        ): Bundle =
            Bundle().apply {
                putString("apiName", apiName)

                if (mainCategory != null)
                    putInt("mainCategory", mainCategory)
                if (orderBy != null)
                    putInt("orderBy", orderBy)
                if (tag != null)
                    putInt("tag", tag)
            }
    }

    var isLoading = false
    var pastVisiblesItems = 0
    var visibleItemCount = 0
    var totalItemCount = 0

    var defMainCategory: Int? = null
    var defOrderBy: Int? = null
    var defTag: Int? = null

    var isInSearch = false

    private fun setupGridView() {
        val compactView = false //activity?.getGridIsCompact() ?: return
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.mainpageList.spanCount = spanCountLandscape
        } else {
            binding.mainpageList.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val apiName = requireArguments().getString("apiName")!!

        defMainCategory = arguments?.getInt("mainCategory", 0)
        defOrderBy = arguments?.getInt("orderBy", 0)
        defTag = arguments?.getInt("tag", 0)

        /*
        if (defTag == -1) defTag = null
        if (defOrderBy == -1) defOrderBy = null
        if (defMainCategory == -1) defMainCategory = null*/

        activity?.fixPaddingStatusbar(binding.mainpageToolbar)

        viewModel.init(
            apiName, defMainCategory,
            defOrderBy,
            defTag
        )

        binding.mainpageToolbar.apply {
            title = apiName
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationOnClickListener {
                //val navController = requireActivity().findNavController(R.id.nav_host_fragment)
                //navController.navigate(R.id.navigation_homepage, Bundle(), MainActivity.navOptions)
                // activity?.popCurrentPage()
                activity?.onBackPressed()
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_open_in_browser -> {
                        viewModel.openInBrowser()
                    }

                    else -> {
                    }
                }
                return@setOnMenuItemClickListener true
            }
            val myActionMenuItem =
                menu.findItem(R.id.action_search)
            val searchView = myActionMenuItem.actionView as SearchView
            myActionMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                    viewModel.switchToMain()
                    return true
                }
            })

            searchView.queryHint = getString(R.string.search_hint)

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    viewModel.search(query)//MainActivity.activeAPI.search(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    return true
                }
            })
        }

        setupGridView()

        binding.mainpageList.apply {
            val mainPageAdapter = MainAdapter2(this, this@MainPageFragment, 1)
            adapter = mainPageAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) { //check for scroll down
                        binding.mainpageFab.shrink()
                        val layoutManager =
                            recyclerView.layoutManager as? GridLayoutManager ?: return
                        visibleItemCount = layoutManager.childCount
                        totalItemCount = layoutManager.itemCount
                        pastVisiblesItems = layoutManager.findFirstVisibleItemPosition()
                        if (!isLoading && !isInSearch) {
                            if (visibleItemCount + pastVisiblesItems >= totalItemCount) {
                                isLoading = true
                                viewModel.load(
                                    null,
                                    viewModel.currentMainCategory.value,
                                    viewModel.currentOrderBy.value,
                                    viewModel.currentTag.value
                                )
                            }
                        }
                    } else if (dy < -5) {
                        binding.mainpageFab.extend()
                    }
                }
            })

            observe(viewModel.loadingMoreItems) {
                mainPageAdapter.setLoading(it)
            }

            observe(viewModel.currentCards) { data ->
                when (data) {
                    is Resource.Success -> {
                        val value = data.value
                        binding.mainpageLoading.isVisible = false
                        binding.mainpageLoadingError.isVisible = false

                        mainPageAdapter.submitList(value)
                        binding.mainpageList.isInvisible = false
                        //binding.mainpageList.isVisible = true
                        // mainPageAdapter.setLoading(false)
                    }

                    is Resource.Loading -> {
                        mainPageAdapter.submitList(listOf())
                        binding.mainpageList.isInvisible = true
                        binding.mainpageLoading.isVisible = true
                        binding.mainpageLoadingError.isVisible = false
                        // mainPageAdapter.setLoading(true)
                    }

                    is Resource.Failure -> {
                        mainPageAdapter.submitList(listOf())
                        binding.mainpageList.isInvisible = false
                        binding.mainpageErrorText.text = data.errorString
                        binding.mainpageLoading.isVisible = false
                        binding.mainpageLoadingError.isVisible = true
                        // mainPageAdapter.setLoading(false)
                    }
                }

                isLoading = false
            }
        }


        binding.mainpageFab.setOnClickListener {
            val api = viewModel.api
            if (!api.hasMainPage) {
                return@setOnClickListener
            }

            val bottomSheetDialog = BottomSheetDialog(requireContext())
            val binding = FilterBottomSheetBinding.inflate(layoutInflater, null, false)
            bottomSheetDialog.setContentView(binding.root)

            fun setUp(
                data: List<Pair<String, String>>,
                txt: TextView,
                spinner: Spinner,
                startId: Int?
            ) {
                if (data.isEmpty()) {
                    txt.isVisible = false
                    spinner.isVisible = false
                } else {
                    val arrayAdapter =
                        ArrayAdapter<String>(requireContext(), R.layout.spinner_select_dialog)

                    arrayAdapter.addAll(data.map { t -> t.first })
                    spinner.adapter = arrayAdapter
                    spinner.setSelection(startId ?: 0)
                }
            }
            binding.apply {
                setUp(
                    api.orderBys,
                    filterOrderText,
                    filterOrderSpinner,
                    viewModel.currentOrderBy.value
                )
                setUp(
                    api.mainCategories,
                    filterGeneralText,
                    filterGeneralSpinner,
                    viewModel.currentMainCategory.value
                )
                setUp(api.tags, filterTagText, filterTagSpinner, viewModel.currentTag.value)

                filterButton.setOnClickListener {
                    fun getId(spinner: Spinner): Int? {
                        return if (spinner.isVisible) spinner.selectedItemPosition else null
                    }

                    val generalId = getId(filterGeneralSpinner)
                    val orderId = getId(filterOrderSpinner)
                    val tagId = getId(filterTagSpinner)
                    isLoading = true

                    viewModel.load(0, generalId, orderId, tagId)

                    bottomSheetDialog.dismiss()
                }
            }
            bottomSheetDialog.setOnDismissListener {
                //  MainActivity.semihideNavbar()
            }
            bottomSheetDialog.show()
            //  MainActivity.showNavbar()
        }


        observe(viewModel.isInSearch) {
            isInSearch = it
            binding.mainpageFab.isGone = it // CANT USE FILTER ON A SEARCHERS
        }
    }
}