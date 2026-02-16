package com.lagradost.quicknovel.ui.mainpage

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.FragmentMainpageBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
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

    lateinit var searchExitIcon: ImageView
    lateinit var searchMagIcon: ImageView
    private var lastId: Int = -1 // dirty fix
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

        searchExitIcon = binding.mainSearch.findViewById(androidx.appcompat.R.id.search_close_btn)
        searchMagIcon = binding.mainSearch.findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f
        binding.mainSearch.queryHint = "${getString(R.string.search)} $apiName…"
        binding.searchBrowse.setOnClickListener {
            viewModel.openInBrowser()
        }
        binding.searchBack.setOnClickListener {
            if (viewModel.isInSearch.value == true) {
                viewModel.switchToMain()
            } else {
                activity?.onBackPressed()
            }
        }

        binding.mainSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })


        /*binding.mainpageToolbar.apply {
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
        }*/

        setupGridView()

        binding.mainpageList.apply {
            setRecycledViewPool(MainAdapter.sharedPool)
            val mainPageAdapter = MainAdapter(this, 1)
            adapter = mainPageAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) { //check for scroll down
                        return
                    }
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
                }
            })

            observe(viewModel.loadingMoreItems) {
                mainPageAdapter.setLoading(it)
            }

            observe(viewModel.currentCards) { data ->
                when (data) {
                    is Resource.Success -> {
                        val value = data.value
                        binding.mainpageLoadingError.isVisible = false
                        mainPageAdapter.submitList(value.items)

                        // this is needed to fix the scroll issue when value.size % 3 == 0
                        if (value.pages == 1 && lastId != value.id) {
                            lastId = value.id
                            binding.mainpageList.post {
                                binding.mainpageList.scrollToPosition(0)
                            }
                        }
                        binding.mainpageList.isInvisible = false
                        //binding.mainpageList.isVisible = true
                        // mainPageAdapter.setLoading(false)


                        searchExitIcon.alpha = 1f
                        binding.searchLoadingBar.alpha = 0f
                    }

                    is Resource.Loading -> {
                        mainPageAdapter.submitList(listOf())
                        binding.mainpageList.isInvisible = true
                        binding.mainpageLoadingError.isVisible = false


                        searchExitIcon.alpha = 0f
                        binding.searchLoadingBar.alpha = 1f
                        // mainPageAdapter.setLoading(true)
                    }

                    is Resource.Failure -> {
                        mainPageAdapter.submitList(listOf())
                        binding.mainpageList.isInvisible = false
                        binding.mainpageErrorText.text = data.errorString
                        binding.mainpageLoadingError.isVisible = true

                        searchExitIcon.alpha = 1f
                        binding.searchLoadingBar.alpha = 0f

                        // mainPageAdapter.setLoading(false)
                    }
                }

                isLoading = false
            }
        }


        observeNullable(viewModel.currentOrderBy) { orderBy ->
            val spinner = binding.filterOrderSpinner
            val orderPair = orderBy?.let { viewModel.api.orderBys.getOrNull(it) }
            spinner.isVisible = orderPair != null
            spinner.text = orderPair?.first
        }

        observeNullable(viewModel.currentTag) { tagBy ->
            val spinner = binding.filterTagSpinner
            val tagPair = tagBy?.let { viewModel.api.tags.getOrNull(it) }
            spinner.isVisible = tagPair != null
            spinner.text = tagPair?.first
        }

        observeNullable(viewModel.currentMainCategory) { generalBy ->
            val spinner = binding.filterGeneralSpinner
            val generalPair = generalBy?.let { viewModel.api.mainCategories.getOrNull(it) }
            spinner.isVisible = generalPair != null
            spinner.text = generalPair?.first
        }

        binding.filterGeneralSpinner.setOnClickListener { view ->
            val context = view.context ?: return@setOnClickListener
            context.showDialog(
                viewModel.api.mainCategories.map { it.first },
                viewModel.currentMainCategory.value ?: -1,
                context.getString(R.string.filter_dialog_general),
                true,
                {}) { selection ->
                viewModel.setMainCategory(selection)
            }
        }

        binding.filterTagSpinner.setOnClickListener { view ->
            val context = view.context ?: return@setOnClickListener
            context.showDialog(
                viewModel.api.tags.map { it.first },
                viewModel.currentTag.value ?: -1,
                context.getString(R.string.filter_dialog_genre),
                true,
                {}) { selection ->
                viewModel.setTag(selection)
            }
        }

        binding.filterOrderSpinner.setOnClickListener { view ->
            val context = view.context ?: return@setOnClickListener
            context.showDialog(
                viewModel.api.orderBys.map { it.first },
                viewModel.currentOrderBy.value ?: -1,
                context.getString(R.string.filter_dialog_order_by),
                true,
                {}) { selection ->
                viewModel.setOrderBy(selection)
            }
        }

        observe(viewModel.isInSearch) {
            isInSearch = it
            binding.mainpageSortbyHolder.isGone = it // CANT USE FILTER ON A SEARCHERS
        }
    }
}