package com.lagradost.quicknovel.ui.mainpage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.LoadingBottomBinding
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.ui.BaseAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import kotlin.math.roundToInt

class MainAdapter2(
    private val resView: AutofitRecyclerView, fragment: Fragment,
    override val footers: Int
) :
    BaseAdapter<SearchResponse, Nothing>(fragment, 0) {

    override fun onBindFooter(holder: ViewHolderState<Nothing>) {
        (holder.view as LoadingBottomBinding).apply {
            val coverHeight: Int =
                (resView.itemWidth / 0.68).roundToInt()

            backgroundCard.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
            }
            if (loadingItems)
                holder.view.resultLoading.startShimmer()
            else
                holder.view.resultLoading.stopShimmer()
        }

        holder.view.root.isVisible = loadingItems
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Nothing> {
        return ViewHolderState(
            SearchResultGridBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ), parent, false
            )
        )
    }

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Nothing> {
        return ViewHolderState(
            LoadingBottomBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ), parent, false
            )
        )
    }

    override fun onBindContent(
        holder: ViewHolderState<Nothing>,
        item: SearchResponse,
        position: Int
    ) {
        (holder.view as SearchResultGridBinding).apply {
            val compactView = false//resView.context?.getGridIsCompact() ?: return

            val coverHeight: Int =
                if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

            imageView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
                setImage(item.image)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null) // HALF IMAGE DISPLAYING FIX
                setOnClickListener {
                    loadResult(item.url, item.apiName)
                }
                setOnLongClickListener {
                    MainActivity.loadPreviewPage(item)
                    return@setOnLongClickListener true
                }
            }

            imageText.text = item.name
        }
    }

    private var loadingItems: Boolean = false

    fun setLoading(to: Boolean) {
        if (loadingItems == to) return
        if (to) {
            loadingItems = true
            notifyItemRangeChanged(itemCount - footers, footers)
        } else {
            loadingItems = false
            notifyItemRangeChanged(itemCount - footers, footers)
        }
    }
}