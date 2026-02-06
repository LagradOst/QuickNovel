package com.lagradost.quicknovel.ui.history

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.FragmentHistoryBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.popupMenu


class HistoryFragment : Fragment() {

    lateinit var binding: FragmentHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()

    private fun setupGridView() {
        val compactView = true //requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.historyCardSpace.spanCount = spanCountLandscape
        } else {
            binding.historyCardSpace.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(binding.historyToolbar)

        setupGridView()

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
}