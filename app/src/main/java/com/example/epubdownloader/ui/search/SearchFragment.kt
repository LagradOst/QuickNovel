package com.example.epubdownloader.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epubdownloader.MainActivity
import com.example.epubdownloader.R
import com.example.epubdownloader.ResAdapter
import com.example.epubdownloader.SearchResponse
import kotlinx.android.synthetic.main.fragment_search.*
import kotlin.concurrent.thread

import android.widget.ImageView




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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parameter = top_padding.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        top_padding.layoutParams = parameter

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList<SearchResponse>(),
                cardSpace,
            )
        }
        cardSpace.adapter = adapter
        cardSpace.layoutManager = GridLayoutManager(context,1)
        search_loading_bar.alpha = 0f
        val search_exit_icon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean
            {
                search_exit_icon.alpha = 0f
                search_loading_bar.alpha = 1f
                thread {
                    val data = MainActivity.activeAPI.search(query)
                    activity?.runOnUiThread {
                        if (data == null) {
                            Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                        } else {
                            (cardSpace.adapter as ResAdapter).cardList =  data
                            (cardSpace.adapter as ResAdapter).notifyDataSetChanged()
                        }
                        search_exit_icon.alpha = 1f
                        search_loading_bar.alpha = 0f
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {


                return true
            }
        })
    }
}