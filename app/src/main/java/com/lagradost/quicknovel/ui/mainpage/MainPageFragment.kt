package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
        isLoading = false
    }

    var isLoading = false
    var pastVisiblesItems = 0
    var visibleItemCount = 0
    var totalItemCount = 0
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(MainActivity.activity).get(MainPageViewModel::class.java)

        if (viewModel.cards.value?.size ?: 0 <= 0 && !isLoading) {
            isLoading = true
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

        val mLayoutManager: LinearLayoutManager
        mLayoutManager = LinearLayoutManager(this.context)
        mainpage_list.setLayoutManager(mLayoutManager)

        mainpage_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { //check for scroll down
                    visibleItemCount = mLayoutManager.getChildCount()
                    totalItemCount = mLayoutManager.getItemCount()
                    pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition()
                    if (!isLoading) {
                        if (visibleItemCount + pastVisiblesItems >= totalItemCount) {
                            isLoading = true
                            thread {
                                viewModel.load(null, 0, null, 0)
                            }
                        }
                    }
                }
            }
        })
        /*
        mainpage_list.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val dx = scrollX - oldScrollX
            val dy = scrollY - oldScrollY

            val scaleTo = if (dy > 0) 0f else 1f

            if (mainpage_fab.scaleX == 1 - scaleTo) {
                val scaleDownX = ObjectAnimator.ofFloat(mainpage_fab, "scaleX", scaleTo)
                val scaleDownY = ObjectAnimator.ofFloat(mainpage_fab, "scaleY", scaleTo)
                scaleDownX.duration = 100
                scaleDownY.duration = 100

                val scaleDown = AnimatorSet()
                scaleDown.play(scaleDownX).with(scaleDownY)

                scaleDown.start()
            }
        }*/

        observe(viewModel.cards, ::updateList)
    }
}