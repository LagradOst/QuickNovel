package com.lagradost.quicknovel.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.ObserveEffect
import com.lagradost.quicknovel.compose.loadPrimaryColor
import com.lagradost.quicknovel.compose.loadThemeMode
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.tachiyomi.collectAsState
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.ui.settings.searchLangList
import com.lagradost.quicknovel.ui.settings.searchProvidersList
import kotlinx.collections.immutable.toPersistentSet


class SearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(inflater.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            CloudStreamTheme(
                mode = LocalContext.current.loadThemeMode(),
                primaryColor = LocalContext.current.loadPrimaryColor(),
            ) {
                val viewModel = viewModel<HomeViewModel2>()
                //val viewModel: HomeViewModel2 =
               //     viewModel(factory = HomeViewModel2.provideFactory(store.searchProvidersList()))
                val state by viewModel.state.collectAsStateWithLifecycle()

                val store = AndroidPreferenceStore(context)
                val selectionState by store.searchProvidersList().collectAsState()
                LaunchedEffect(selectionState) {
                    viewModel.onAction(HomeAction.ConfigureApisNames(selectionState.toPersistentSet()))
                }

                val selectionLangState by store.searchLangList().collectAsState()
                LaunchedEffect(selectionLangState) {
                    viewModel.onAction(HomeAction.ConfigureApisLanguages(selectionLangState.toPersistentSet()))
                }

                ObserveEffect(viewModel.effect) { effect ->
                    when (effect) {
                        is HomeEffect.NavigateToMainPage -> {
                            CommonActivity.activity?.navigate(
                                R.id.global_to_navigation_mainpage,
                                MainPageFragment.newInstance(effect.api, effect.filter)
                            )
                        }

                    }
                }

                SearchScreen(state, viewModel::onAction)
            }
        }
    }
}


/*
class SearchFragment : BaseFragment<FragmentSearchBinding>(
    BindingCreator.Inflate(FragmentSearchBinding::inflate)
) {
    private val viewModel: SearchViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

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
                setRecycledViewPool(SearchAdapter.sharedPool)
                val searchAdapter = SearchAdapter(viewModel, binding.homeExpandedRecycler)
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

    override fun fixLayout(view: View) {
        val compactView = false//activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }

        binding?.homeBrowselist?.spanCount = currentSpan
        binding?.searchAllRecycler?.spanCount = currentSpan
        configEvent.invoke(currentSpan)
    }

    lateinit var searchExitIcon: ImageView
    lateinit var searchMagIcon: ImageView

    override fun onBindingCreated(binding: FragmentSearchBinding, savedInstanceState: Bundle?) {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) == true
        binding.searchMasterRecycler.isVisible = false
        binding.searchAllRecycler.isGone = false

        val masterAdapter = ParentItemAdapter(viewModel)
        val allAdapter = SearchAdapter(viewModel, binding.searchAllRecycler)
        binding.searchAllRecycler.adapter = allAdapter
        binding.searchAllRecycler.setRecycledViewPool(SearchAdapter.sharedPool)
        binding.searchMasterRecycler.apply {
            setRecycledViewPool(ParentItemAdapter.sharedPool)
            adapter = masterAdapter
            layoutManager = GridLayoutManager(context, 1)
        }

        observeNullable(viewModel.searchResponse) { response ->
            binding.homeBrowselist.isVisible = response == null
            if (response == null) {
                binding.searchAllRecycler.isVisible = false
                allAdapter.submitIncomparableList(emptyList())
                searchExitIcon.alpha = 1f
                binding.searchLoadingBar.alpha = 0f
                return@observeNullable
            }
            binding.searchAllRecycler.isGone = isAdvancedSearch
            when (response) {
                is Resource.Success -> {
                    response.value.let { data ->
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

        observeNullable(viewModel.currentSearch) { list ->
            if (list == null) {
                binding.searchMasterRecycler.isVisible = false
                masterAdapter.submitIncomparableList(emptyList())
            } else {
                binding.searchMasterRecycler.isVisible = isAdvancedSearch
                masterAdapter.submitList(list.map {
                    HomePageList(
                        it.apiName, if (it.data is Resource.Success) it.data.value else emptyList()
                    )
                })
            }
        }

        activity?.fixPaddingStatusbar(binding.searchToolbar)

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
                if (newText.isEmpty()) {
                    viewModel.clearSearch()
                }
                return true
            }
        })

        /*binding.mainSearch.setOnQueryTextFocusChangeListener { searchView, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                searchView.doOnLayout {
                    val imm: InputMethodManager? =
                        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager?
                    imm?.showSoftInput(searchView.findFocus(), 0)
                }
            }
        }*/
        //binding.mainSearch.onActionViewExpanded()


        val browseAdapter = BrowseAdapter()
        binding.homeBrowselist.apply {
            adapter = browseAdapter
            // layoutManager = GridLayoutManager(context, 1)
            setHasFixedSize(true)
        }

        observe(homeViewModel.homeApis) { list ->
            browseAdapter.submitList(list)
        }

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
}*/