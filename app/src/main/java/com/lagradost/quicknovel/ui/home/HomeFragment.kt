package com.lagradost.quicknovel.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
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
        val homeApis = ArrayList<MainAPI>()
        val langs = context?.getApiProviderLangSettings()
        for (api in apis) {
            if (api.hasMainPage && (langs == null || langs.contains(api.lang))) {
                homeApis.add(api)
            }
        }
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            BrowseAdapter(
                it,
                homeApis,
                home_browselist,
            )
        }
        home_browselist.adapter = adapter
        home_browselist.layoutManager = GridLayoutManager(context, 1)
        activity?.fixPaddingStatusbar(homeRoot)
    }
}