package com.lagradost.quicknovel.ui.search

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.util.Apis.Companion.allApi
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.SettingsHelper.getGridIsCompact
import kotlinx.android.synthetic.main.fragment_search.*

class SearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private fun setupGridView() {
        val compactView = requireContext().getGridIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if(cardSpace == null) return
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cardSpace.spanCount = spanCountLandscape
        } else {
            cardSpace.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    private lateinit var viewModel: SearchViewModel
    private val factory = provideSearchViewModelFactory()

    lateinit var searchExitIcon : ImageView
    lateinit var searchMagIcon : ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProviders.of(this, factory)
            .get(SearchViewModel::class.java)

        observe(viewModel.searchResults) { data ->
            when(data) {
                is Resource.Success -> {
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                    (cardSpace.adapter as ResAdapter).cardList = data.value
                    (cardSpace.adapter as ResAdapter).notifyDataSetChanged()
                }
                is Resource.Failure -> {
                    //TODO ADD NO DATA UI
                }
            }
        }

        activity?.fixPaddingStatusbar(searchRoot)

        setupGridView()
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList(),
                cardSpace,
            )
        }

        cardSpace.adapter = adapter
        //cardSpace.layoutManager = GridLayoutManager(context, 1)
        search_loading_bar.alpha = 0f
        searchExitIcon = main_search.findViewById(androidx.appcompat.R.id.search_close_btn)
        searchMagIcon = main_search.findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f

        search_filter.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            //val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
            val apiNamesSetting = requireActivity().getApiSettings()

            val apiNames = apis.map { it.name }

            builder.setMultiChoiceItems(apiNames.toTypedArray(),
                apiNames.map { a -> apiNamesSetting.contains(a) }.toBooleanArray()
            ) { _, position: Int, checked: Boolean ->
                val apiNamesSettingLocal = requireActivity().getApiSettings()
                val settingsManagerLocal = PreferenceManager.getDefaultSharedPreferences(activity)
                if (checked) {
                    apiNamesSettingLocal.add(apiNames[position])
                } else {
                    apiNamesSettingLocal.remove(apiNames[position])
                }

                val edit = settingsManagerLocal.edit()
                edit.putStringSet(getString(R.string.search_providers_list_key),
                    apiNames.filter { a -> apiNamesSettingLocal.contains(a) }.toSet())
                edit.apply()
                allApi.providersActive = apiNamesSettingLocal
            }
            builder.setTitle("Search Providers")
            builder.setNegativeButton("Cancel") { _, _ -> }
            builder.show()
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchExitIcon.alpha = 0f
                search_loading_bar.alpha = 1f
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        main_search.onActionViewExpanded()

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