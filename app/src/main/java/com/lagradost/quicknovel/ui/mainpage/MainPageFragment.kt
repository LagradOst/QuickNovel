package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.mvvm.observe
import kotlinx.android.synthetic.main.fragment_mainpage.*
import kotlin.concurrent.thread

class MainPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        /*activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )*/

        return inflater.inflate(R.layout.fragment_mainpage, container, false)
    }

    private lateinit var viewModel: MainPageViewModel

    private fun updateList(data: ArrayList<MainPageResponse>) {
        (mainpage_list.adapter as MainAdapter).cardList = data
        (mainpage_list.adapter as MainAdapter).notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(MainActivity.activity).get(MainPageViewModel::class.java)

        if (viewModel.cards.value?.size ?: 0 <= 0) {
            thread {
                viewModel.load(0, 0, null, 0)
            }
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            MainAdapter(
                it,
                ArrayList(),
                mainpage_list,
            )
        }
        mainpage_list.adapter = adapter
        mainpage_list.layoutManager = GridLayoutManager(context, 1)

        observe(viewModel.cards, ::updateList)
    }
}