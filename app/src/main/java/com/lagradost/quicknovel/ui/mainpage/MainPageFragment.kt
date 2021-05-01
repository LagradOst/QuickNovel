package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
        mainpage_loading.visibility = if (data.size > 0) View.GONE else View.VISIBLE
        (mainpage_list.adapter as MainAdapter).cardList = data
        (mainpage_list.adapter as MainAdapter).notifyDataSetChanged()
        isLoading = false
    }

    var isLoading = false
    var pastVisiblesItems = 0
    var visibleItemCount = 0
    var totalItemCount = 0
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val parameter = mainpage_top_padding.layoutParams as LinearLayout.LayoutParams
        parameter.setMargins(parameter.leftMargin,
            parameter.topMargin + MainActivity.statusBarHeight,
            parameter.rightMargin,
            parameter.bottomMargin)
        mainpage_top_padding.layoutParams = parameter

        viewModel = ViewModelProviders.of(MainActivity.activity).get(MainPageViewModel::class.java)

        if (viewModel.cards.value?.size ?: 0 <= 0 && !isLoading) {
            isLoading = true
            thread {
                val api = viewModel.api.value
                if (api != null)
                    viewModel.load(0,
                        if (api.mainCategories.size > 0) 0 else null,
                        if (api.orderBys.size > 0) 0 else null,
                        if (api.tags.size > 0) 0 else null)
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
                                viewModel.load(null,
                                    viewModel.currentMainCategory.value,
                                    viewModel.currentOrderBy.value,
                                    viewModel.currentTag.value)
                            }
                        }
                    }
                }
            }
        })
        mainpage_fab.setOnClickListener {
            val api = viewModel.api.value
            if (api == null || !api.hasMainPage) {
                return@setOnClickListener
            }

            val bottomSheetDialog = BottomSheetDialog(this.context!!)
            bottomSheetDialog.setContentView(R.layout.filter_bottom_sheet)

            val filter_general_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_general_text)!!
            val filter_general_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_general_spinner)!!
            val filter_order_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_order_text)!!
            val filter_order_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_order_spinner)!!
            val filter_tag_text = bottomSheetDialog.findViewById<TextView>(R.id.filter_tag_text)!!
            val filter_tag_spinner = bottomSheetDialog.findViewById<Spinner>(R.id.filter_tag_spinner)!!
            val filter_button = bottomSheetDialog.findViewById<MaterialButton>(R.id.filter_button)!!

            fun setUp(data: ArrayList<Pair<String, String>>, txt: TextView, spinner: Spinner, startId: Int?) {
                if (data.size == 0) {
                    txt.visibility = View.GONE
                    spinner.visibility = View.GONE
                } else {
                    val arrayAdapter = ArrayAdapter<String>(this.context!!, R.layout.spinner_select_dialog)

                    arrayAdapter.addAll(data.map { t -> t.first })
                    spinner.adapter = arrayAdapter
                    spinner.setSelection(startId ?: 0)
                }
            }

            setUp(api.orderBys, filter_order_text, filter_order_spinner, viewModel.currentOrderBy.value)
            setUp(api.mainCategories, filter_general_text, filter_general_spinner, viewModel.currentMainCategory.value)
            setUp(api.tags, filter_tag_text, filter_tag_spinner, viewModel.currentTag.value)

            filter_button.setOnClickListener {
                fun getId(spinner: Spinner): Int? {
                    return if (spinner.visibility == View.VISIBLE) spinner.selectedItemPosition else null
                }

                val generalId = getId(filter_general_spinner)
                val orderId = getId(filter_order_spinner)
                val tagId = getId(filter_tag_spinner)
                isLoading = true

                thread {
                    viewModel.load(0, generalId, orderId, tagId)
                }
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.show()
        }

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