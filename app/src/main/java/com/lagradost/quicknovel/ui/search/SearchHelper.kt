package com.lagradost.quicknovel.ui.search

import android.app.Activity
import android.widget.Toast
import com.lagradost.quicknovel.MainActivity.Companion.loadSearchResult

object SearchHelper {
    fun handleSearchClickCallback(activity: Activity?, callback: SearchClickCallback) {
        val card = callback.card
        when (callback.action) {
            SEARCH_ACTION_LOAD -> {
                activity.loadSearchResult(card)
            }
            SEARCH_ACTION_SHOW_METADATA -> {
                activity?.let { act ->
                    Toast.makeText(act, callback.card.name, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}