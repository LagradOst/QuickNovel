package com.lagradost.quicknovel.ui.search

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.normalSafeApiCall
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.fragment_search.*

class SearchFragment : Fragment() {
    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1

        fun Activity.loadHomepageList(item: HomePageList) {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!
            title.text = item.name
            val recycle =
                bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            val titleHolder =
                bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            titleHolder.setOnClickListener {
                bottomSheetDialogBuilder.dismiss()
            }

            // Span settings
            recycle.spanCount = currentSpan

            recycle.adapter = SearchAdapter(item.list, recycle) { callback ->
                handleSearchClickCallback(this, callback)
                if (callback.action == SEARCH_ACTION_LOAD) {
                    bottomSheetDialogBuilder.dismiss()
                }
            }

            val spanListener = { span: Int ->
                recycle.spanCount = span
                (recycle.adapter as SearchAdapter).notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                configEvent -= spanListener
            }

            (recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            ViewModelProvider(this).get(SearchViewModel::class.java)
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private fun fixGrid() {
        val compactView = activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }
        cardSpace.spanCount = currentSpan
        configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    private lateinit var searchViewModel: SearchViewModel

    lateinit var searchExitIcon: ImageView
    lateinit var searchMagIcon: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        if (data.isNotEmpty()) {
                            (cardSpace?.adapter as SearchAdapter?)?.apply {
                                cardList = data.toList()
                                notifyDataSetChanged()
                            }
                        }
                    }
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    search_loading_bar.alpha = 1f
                }
            }
        }

        observe(searchViewModel.currentSearch) { list ->
            normalSafeApiCall {
                (search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                    items = list.map {
                        HomePageList(
                            it.apiName,
                            if (it.data is Resource.Success) it.data.value else listOf()
                        )
                    }
                    notifyDataSetChanged()
                }
            }
        }

        activity?.fixPaddingStatusbar(searchRoot)

        fixGrid()
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            SearchAdapter(
                ArrayList(),
                cardSpace,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }

        val masterAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            ParentItemAdapter(listOf(), { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }, { item ->
                activity?.loadHomepageList(item)
            })

        cardSpace.adapter = adapter
        //cardSpace.layoutManager = GridLayoutManager(context, 1)
        search_loading_bar.alpha = 0f
        searchExitIcon = main_search.findViewById(androidx.appcompat.R.id.search_close_btn)
        searchMagIcon = main_search.findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f

        search_filter.setOnClickListener { view ->
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            //val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
            val apiNamesSetting = view.context.getApiSettings()

            val langs = view.context.getApiProviderLangSettings()
            val apiNames = apis.mapNotNull { if (langs.contains(it.lang)) it.name else null }

            builder.setMultiChoiceItems(
                apiNames.toTypedArray(),
                apiNames.map { a -> apiNamesSetting.contains(a) }.toBooleanArray()
            ) { _, position: Int, checked: Boolean ->
                val apiNamesSettingLocal = requireActivity().getApiSettings()
                val settingsManagerLocal = activity?.let {
                    PreferenceManager.getDefaultSharedPreferences(
                        it
                    )
                }
                if (checked) {
                    apiNamesSettingLocal.add(apiNames[position])
                } else {
                    apiNamesSettingLocal.remove(apiNames[position])
                }

                val edit = settingsManagerLocal?.edit()
                edit?.putStringSet(
                    getString(R.string.search_providers_list_key),
                    apiNames.filter { a -> apiNamesSettingLocal.contains(a) }.toSet()
                )
                edit?.apply()
                providersActive = requireContext().getApiSettings()
            }
            builder.setTitle("Search Providers")
            builder.setNegativeButton("Ok") { _, _ -> }
            builder.show()
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchExitIcon.alpha = 0f
                search_loading_bar.alpha = 1f
                searchViewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        main_search.setOnQueryTextFocusChangeListener { searchView, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                searchView.postDelayed({
                    val imm: InputMethodManager? =
                        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager?
                    imm?.showSoftInput(searchView.findFocus(), 0)
                }, 200)
            }
        }
        main_search.onActionViewExpanded()

        search_master_recycler.adapter = masterAdapter
        search_master_recycler.layoutManager = GridLayoutManager(context, 1)

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) == true

        search_master_recycler.visibility = if (isAdvancedSearch) View.VISIBLE else View.GONE
        cardSpace.visibility = if (!isAdvancedSearch) View.VISIBLE else View.GONE
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