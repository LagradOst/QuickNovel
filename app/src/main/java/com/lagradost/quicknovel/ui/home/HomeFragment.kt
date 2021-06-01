package com.lagradost.quicknovel.ui.home

import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.*
import kotlinx.android.synthetic.main.fragment_home.*


class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val apis = ArrayList<MainAPI>()
        for (api in MainActivity.apis) {
            if (api.hasMainPage) {
                apis.add(api)
            }
        }
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            BrowseAdapter(
                it,
                apis,
                home_browselist,
            )
        }
        home_browselist.adapter = adapter
        home_browselist.layoutManager = GridLayoutManager(context, 1)
        MainActivity.fixPaddingStatusbar(homeRoot)
/*
        home_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_goto_search -> {
                    val navController = MainActivity.activity.findNavController(R.id.nav_host_fragment)
                    navController.navigate(R.id.navigation_search, null, MainActivity.navOptions)
                }
                else -> {
                }
            }
            return@setOnMenuItemClickListener true
        }*/
    }
}