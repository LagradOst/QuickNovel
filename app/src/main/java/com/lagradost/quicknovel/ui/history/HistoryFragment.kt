package com.lagradost.quicknovel.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.loadPrimaryColor
import com.lagradost.quicknovel.compose.loadThemeMode

/*
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.loadPrimaryColor
import com.lagradost.quicknovel.compose.loadThemeMode
import com.lagradost.quicknovel.databinding.FragmentHistoryBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.BaseFragment
import com.lagradost.quicknovel.ui.setRecycledViewPool
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.popupMenu


class HistoryFragment : BaseFragment<FragmentHistoryBinding>(
    BindingCreator.Inflate(FragmentHistoryBinding::inflate)
) {
    private val viewModel: HistoryViewModel by viewModels()

    override fun fixLayout(view: View) {
        val compactView = true //requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding?.historyCardSpace?.spanCount = spanCountLandscape
        } else {
            binding?.historyCardSpace?.spanCount = spanCountPortrait
        }
    }

    override fun onBindingCreated(binding: FragmentHistoryBinding) {
        activity?.fixPaddingStatusbar(binding.historyToolbar)

        val historyAdapter = HistoryAdapter(viewModel)

        binding.historyCardSpace.apply {
            setRecycledViewPool(HistoryAdapter.sharedPool)
            adapter = historyAdapter
        }

        observe(viewModel.cards) { cards ->
            historyAdapter.submitList(cards)
            binding.historyNothingHolder.isVisible = cards.isEmpty()
        }

        binding.historyToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_history_more -> {
                    binding.historyToolbar.findViewById<View>(R.id.action_history_more)
                        .popupMenu(listOf(1 to R.string.clear_history)) {
                            if (itemId == 1) {
                                viewModel.deleteAllAlert()
                            }
                        }
                }

                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }
    }
}*/


class HistoryFragment : Fragment() {
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
                HistoryScreen()
            }
        }
    }
}

