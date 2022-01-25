package com.lagradost.quicknovel.ui.mainpage

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import kotlinx.android.synthetic.main.fragment_mainpage.*


class MainPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        /*activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )*/

        return inflater.inflate(R.layout.fragment_mainpage, container, false)
    }

    fun newInstance(
        apiName: String,
        mainCategory: Int? = null,
        orderBy: Int? = null,
        tag: Int? = null
    ) =
        MainPageFragment().apply {
            arguments = Bundle().apply {
                putString("apiName", apiName)

                if (mainCategory != null)
                    putInt("mainCategory", mainCategory)
                if (orderBy != null)
                    putInt("orderBy", orderBy)
                if (tag != null)
                    putInt("tag", tag)
            }
        }

    private lateinit var viewModel: MainPageViewModel

    private fun updateList(data: Resource<List<SearchResponse>>) {
        when (data) {
            is Resource.Success -> {
                val value = data.value
                mainpage_loading_error?.isVisible = false
                mainpage_loading?.isVisible = false

                (mainpage_list?.adapter as MainAdapter?)?.cardList = value
                mainpage_list?.adapter?.notifyDataSetChanged()
            }
            is Resource.Loading -> {
                mainpage_loading_error?.isVisible = false
                mainpage_loading?.isVisible = true
            }
            is Resource.Failure -> {
                mainpage_error_text?.text = data.errorString
                mainpage_loading_error?.isVisible = true
                mainpage_loading?.isVisible = false
            }
        }

        isLoading = false
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
        val compactView = requireContext().getGridIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mainpage_list.spanCount = spanCountLandscape
        } else {
            mainpage_list.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val apiName = requireArguments().getString("apiName")!!
        viewModel = ViewModelProviders.of(this, provideMainPageViewModelFactory(apiName))
            .get(MainPageViewModel::class.java)

        defMainCategory = arguments?.getInt("mainCategory", 0)
        defOrderBy = arguments?.getInt("orderBy", 0)
        defTag = arguments?.getInt("tag", 0)

        /*
        if (defTag == -1) defTag = null
        if (defOrderBy == -1) defOrderBy = null
        if (defMainCategory == -1) defMainCategory = null*/

        activity?.fixPaddingStatusbar(mainpageRoot)

        viewModel.load(
            0,
            defMainCategory,
            defOrderBy,
            defTag
        )

        mainpage_toolbar.title = apiName

        mainpage_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        mainpage_toolbar.setNavigationOnClickListener {
            //val navController = requireActivity().findNavController(R.id.nav_host_fragment)
            //navController.navigate(R.id.navigation_homepage, Bundle(), MainActivity.navOptions)
            // activity?.popCurrentPage()
            activity?.onBackPressed()
        }

        mainpage_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_open_in_browser -> {
                    val url = viewModel.currentUrl.value
                    if (url != null) {
                        val i = Intent(Intent.ACTION_VIEW)
                        i.data = Uri.parse(url)
                        startActivity(i)
                    }
                }
                /*
                R.id.action_search -> {
                }*/
                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }

        val myActionMenuItem =
            mainpage_toolbar.menu.findItem(R.id.action_search)
        val searchView = myActionMenuItem.actionView as SearchView
        myActionMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                // viewModel.isInSearch.postValue(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                viewModel.switchToMain()
                // if (viewModel.isSearchResults.value == true)
                //    defLoad() // IN CASE THE USER HAS SEARCHED SOMETHING, RELOAD ON BACK
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

        setupGridView()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            MainAdapter(
                it,
                ArrayList(),
                mainpage_list,
            )
        }

        mainpage_list.adapter = adapter
        val mLayoutManager = mainpage_list.layoutManager!! as GridLayoutManager

        mainpage_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { //check for scroll down
                    mainpage_fab.shrink()

                    visibleItemCount = mLayoutManager.childCount
                    totalItemCount = mLayoutManager.itemCount
                    pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition()
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
                    mainpage_fab.extend()
                }
            }
        })

        mainpage_fab.setOnClickListener {
            val api = viewModel.api
            if (!api.hasMainPage) {
                return@setOnClickListener
            }

            val bottomSheetDialog = BottomSheetDialog(requireContext())
            bottomSheetDialog.setContentView(R.layout.filter_bottom_sheet)

            val filterGeneralText =
                bottomSheetDialog.findViewById<TextView>(R.id.filter_general_text)!!
            val filterGeneralSpinner =
                bottomSheetDialog.findViewById<Spinner>(R.id.filter_general_spinner)!!
            val filterOrderText = bottomSheetDialog.findViewById<TextView>(R.id.filter_order_text)!!
            val filterOrderSpinner =
                bottomSheetDialog.findViewById<Spinner>(R.id.filter_order_spinner)!!
            val filterTagText = bottomSheetDialog.findViewById<TextView>(R.id.filter_tag_text)!!
            val filterTagSpinner =
                bottomSheetDialog.findViewById<Spinner>(R.id.filter_tag_spinner)!!
            val filterButton = bottomSheetDialog.findViewById<MaterialButton>(R.id.filter_button)!!

            fun setUp(
                data: List<Pair<String, String>>,
                txt: TextView,
                spinner: Spinner,
                startId: Int?
            ) {
                if (data.isEmpty()) {
                    txt.visibility = View.GONE
                    spinner.visibility = View.GONE
                } else {
                    val arrayAdapter =
                        ArrayAdapter<String>(requireContext(), R.layout.spinner_select_dialog)

                    arrayAdapter.addAll(data.map { t -> t.first })
                    spinner.adapter = arrayAdapter
                    spinner.setSelection(startId ?: 0)
                }
            }

            setUp(api.orderBys, filterOrderText, filterOrderSpinner, viewModel.currentOrderBy.value)
            setUp(
                api.mainCategories,
                filterGeneralText,
                filterGeneralSpinner,
                viewModel.currentMainCategory.value
            )
            setUp(api.tags, filterTagText, filterTagSpinner, viewModel.currentTag.value)

            filterButton.setOnClickListener {
                fun getId(spinner: Spinner): Int? {
                    return if (spinner.visibility == View.VISIBLE) spinner.selectedItemPosition else null
                }

                val generalId = getId(filterGeneralSpinner)
                val orderId = getId(filterOrderSpinner)
                val tagId = getId(filterTagSpinner)
                isLoading = true

                viewModel.load(0, generalId, orderId, tagId)

                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setOnDismissListener {
                //  MainActivity.semihideNavbar()
            }
            bottomSheetDialog.show()
            //  MainActivity.showNavbar()
        }

        observe(viewModel.currentCards, ::updateList)
        observe(viewModel.isInSearch) {
            isInSearch = it
            mainpage_fab.isGone = it // CANT USE FILTER ON A SEARCHERS
        }
    }
}