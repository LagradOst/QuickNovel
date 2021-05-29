package com.lagradost.quicknovel.ui.mainpage

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.mvvm.observe
import kotlinx.android.synthetic.main.fragment_mainpage.*
import kotlin.concurrent.thread


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

    fun newInstance(apiName: String, mainCategory: Int? = null, orderBy: Int? = null, tag: Int? = null) =
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

    private fun updateList(data: ArrayList<MainPageResponse>) {
        mainpage_loading.visibility = if (data.size > 0) View.GONE else View.VISIBLE
        //if (data.size > 0) MainActivity.semihideNavbar()

        (mainpage_list.adapter as MainAdapter).cardList = data
        (mainpage_list.adapter as MainAdapter).notifyDataSetChanged()
        isLoading = false
    }

    var isLoading = false
    var pastVisiblesItems = 0
    var visibleItemCount = 0
    var totalItemCount = 0

    fun defLoad() {
        if (!isLoading) {
            isLoading = true
            thread {
                val api = viewModel.api.value
                if (api != null)
                    viewModel.load(0,
                        if (api.mainCategories.size > 0) 0 else null,
                        if (api.orderBys.size > 0) 0 else null,
                        if (api.tags.size > 0) 0 else null)
            }
        }
    }

    var defMainCategory: Int? = null
    var defOrderBy: Int? = null
    var defTag: Int? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(this).get(MainPageViewModel::class.java)
        arguments?.getString("apiName")?.let {
            viewModel.api.value = MainActivity.getApiFromName(it)
        }

        defMainCategory = arguments?.getInt("mainCategory", -1)
        defOrderBy = arguments?.getInt("orderBy", -1)
        defTag = arguments?.getInt("tag", -1)

        if (defTag == -1) defTag = null
        if (defOrderBy == -1) defOrderBy = null
        if (defMainCategory == -1) defMainCategory = null

        MainActivity.fixPaddingStatusbar(mainpageRoot)

        mainpage_toolbar.title = viewModel.api.value?.name ?: "ERROR"
        mainpage_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        mainpage_toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
            //val navController = MainActivity.activity.findNavController(R.id.nav_host_fragment)
            //navController.navigate(R.id.navigation_homepage, null, MainActivity.navOptions)
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
                viewModel.isInSearch.postValue(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                viewModel.isInSearch.postValue(false)
                if (viewModel.isSearchResults.value == true)
                    defLoad() // IN CASE THE USER HAS SEARCHED SOMETHING, RELOAD ON BACK
                return true
            }
        })

        searchView.queryHint = getString(R.string.search_hint)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                thread {
                    viewModel.search(query)//MainActivity.activeAPI.search(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        /*
        mainpage_list.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val dy = scrollY - oldScrollY
            val statush = MainActivity.statusBarHeight

            mainpage_toolbar.translationY = maxOf(-mainpage_toolbar.height.toFloat() - statush,
                minOf(0f, mainpage_toolbar.translationY - statush - dy)) + statush
           // mainpage_list.setPadding() = mainpage_toolbar.translationY
        }*/
        val compactView = MainActivity.getGridIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mainpage_list.spanCount = spanCountLandscape
        } else {
            mainpage_list.spanCount = spanCountPortrait
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
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
                    if (viewModel.isInSearch.value == true) return

                    visibleItemCount = mLayoutManager.childCount
                    totalItemCount = mLayoutManager.itemCount
                    pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition()
                    if (!isLoading) {
                        if (visibleItemCount + pastVisiblesItems >= totalItemCount) {
                            isLoading = true
                            thread {
                                viewModel.load(null,
                                    viewModel.currentMainCategory.value,
                                    viewModel.currentOrderBy.value,
                                    viewModel.currentTag.value)
                            }
                        }
                    }
                }
                else {
                    mainpage_fab.extend()
                }
            }
        })
        mainpage_fab.setOnClickListener {
            val api = viewModel.api.value
            if (api == null || !api.hasMainPage) {
                return@setOnClickListener
            }

            val bottomSheetDialog = BottomSheetDialog(requireContext())
            bottomSheetDialog.setContentView(R.layout.filter_bottom_sheet)

            val filter_general_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_general_text)!!
            val filter_general_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_general_spinner)!!
            val filter_order_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_order_text)!!
            val filter_order_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_order_spinner)!!
            val filter_tag_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_tag_text)!!
            val filter_tag_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_tag_spinner)!!
            val filter_button = bottomSheetDialog.findViewById<MaterialButton>(R.id.filter_button)!!

            fun setUp(data: ArrayList<Pair<String, String>>, txt: TextView, spinner: Spinner, startId: Int?) {
                if (data.size == 0) {
                    txt.visibility = View.GONE
                    spinner.visibility = View.GONE
                } else {
                    val arrayAdapter = ArrayAdapter<String>(requireContext(), R.layout.spinner_select_dialog)

                    arrayAdapter.addAll(data.map { t -> t.first })
                    spinner.adapter = arrayAdapter
                    spinner.setSelection(startId ?: 0)
                }
            }

            setUp(api.orderBys, filter_order_text, filter_order_spinner, viewModel.currentOrderBy.value)
            setUp(api.mainCategories, filter_general_text, filter_general_spinner, viewModel.currentMainCategory.value)
            setUp(api.tags, filter_tag_text, filter_tag_spinner, viewModel.currentTag.value)

            filter_button.setOnClickListener {
                fun getId(spinner: Spinner): Int? {
                    return if (spinner.visibility == View.VISIBLE) spinner.selectedItemPosition else null
                }

                val generalId = getId(filter_general_spinner)
                val orderId = getId(filter_order_spinner)
                val tagId = getId(filter_tag_spinner)
                isLoading = true

                thread {
                    viewModel.load(0, generalId, orderId, tagId)
                }
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setOnDismissListener {
                //  MainActivity.semihideNavbar()
            }
            bottomSheetDialog.show()
            //  MainActivity.showNavbar()
        }

        /*
        mainpage_list.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val dx = scrollX - oldScrollX
            val dy = scrollY - oldScrollY

            val scaleTo = if (dy > 0) 0f else 1f

            if (mainpage_fab.scaleX == 1 - scaleTo) {
                val scaleDownX = ObjectAnimator.ofFloat(mainpage_fab, "scaleX", scaleTo)
                val scaleDownY = ObjectAnimator.ofFloat(mainpage_fab, "scaleY", scaleTo)
                scaleDownX.duration = 100
                scaleDownY.duration = 100

                val scaleDown = AnimatorSet()
                scaleDown.play(scaleDownX).with(scaleDownY)

                scaleDown.start()
            }
        }*/

        observe(viewModel.cards, ::updateList)

        if (viewModel.cards.value?.size ?: 0 <= 0) {
            isLoading = true
            thread {
                val api = viewModel.api.value
                if (api != null)
                    viewModel.load(0,
                        defMainCategory ?: if (api.mainCategories.size > 0) 0 else null,
                        defOrderBy ?: if (api.orderBys.size > 0) 0 else null,
                        defTag ?: if (api.tags.size > 0) 0 else null)
            }
        }
    }
}