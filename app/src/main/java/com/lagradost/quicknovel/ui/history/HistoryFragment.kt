package com.lagradost.quicknovel.ui.history

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.DataStore.removeKeys
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import kotlinx.android.synthetic.main.fragment_downloads.*
import kotlinx.android.synthetic.main.fragment_history.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class HistoryFragment : Fragment() {
    private fun setupGridView() {
        val compactView = true //requireContext().getDownloadIsCompact()
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (history_cardSpace == null) return
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            history_cardSpace.spanCount = spanCountLandscape
        } else {
            history_cardSpace.spanCount = spanCountPortrait
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
    ): View? {

        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    private fun updateHistory() {
        main {
            val list = ArrayList<ResultCached>()

            withContext(Dispatchers.IO) {
                val keys = context?.getKeys(HISTORY_FOLDER) ?: return@withContext
                for (k in keys) {
                    val res =
                        context?.getKey<ResultCached>(k) ?: continue
                    list.add(res)
                }
            }
            val adapter = history_cardSpace?.adapter
            if (adapter is HistoryAdapter) {
                adapter.cardList = list
                adapter.notifyDataSetChanged()
            }
            history_nothing_holder?.visibility = if (list.size > 0) View.GONE else View.VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(historyRoot)

        setupGridView()
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            HistoryAdapter(
                it,
                ArrayList(),
                history_cardSpace,
            ) { _ ->
                updateHistory()
            }
        }

        adapter?.setHasStableIds(true)
        history_cardSpace.adapter = adapter
        val animator: RecyclerView.ItemAnimator = history_cardSpace.itemAnimator!!
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
        updateHistory()

        history_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_history_more -> {
                    history_toolbar.findViewById<View>(R.id.action_history_more)
                        .popupMenu(listOf(Pair(1, R.string.clear_history))) {
                            if (itemId == 1) {
                                val dialogClickListener =
                                    DialogInterface.OnClickListener { _, which ->
                                        when (which) {
                                            DialogInterface.BUTTON_POSITIVE -> {
                                                context?.removeKeys(HISTORY_FOLDER)
                                                updateHistory()
                                            }
                                            DialogInterface.BUTTON_NEGATIVE -> {
                                            }
                                        }
                                    }
                                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                                builder.setMessage("Are you sure?\nAll history will be lost.")
                                    .setTitle("Remove History")
                                    .setPositiveButton("Remove", dialogClickListener)
                                    .setNegativeButton("Cancel", dialogClickListener)
                                    .show()
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