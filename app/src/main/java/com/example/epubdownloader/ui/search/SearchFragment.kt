package com.example.epubdownloader.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.GridView
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

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList<SearchResponse>(),
                cardSpace,
            )
        }
        cardSpace.adapter = adapter
        cardSpace.layoutManager = GridLayoutManager(context,1)

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean
            {
                thread {
                    val data = MainActivity.api.search(query)
                    activity?.runOnUiThread {
                        if (data == null) {
                            Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                        } else {
                            (cardSpace.adapter as ResAdapter).cardList =  data
                            (cardSpace.adapter as ResAdapter).notifyDataSetChanged()
                        }
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