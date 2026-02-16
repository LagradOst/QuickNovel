package com.lagradost.quicknovel.ui.download

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.databinding.ViewpagerPageBinding
import com.lagradost.quicknovel.ui.BaseAdapter
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import java.lang.ref.WeakReference

data class Page(
    val title: String,
    val unsortedItems: List<Any>,
    val items: List<Any>
)

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getSafeParcelable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getParcelable(key)
    else getParcelable(key, T::class.java)

class ViewpagerAdapterViewHolderState(val binding: ViewpagerPageBinding) :
    ViewHolderState<Bundle>(binding) {
    override fun save(): Bundle =
        Bundle().apply {
            putParcelable(
                "pageRecyclerview",
                binding.pageRecyclerview.layoutManager?.onSaveInstanceState()
            )
        }

    override fun restore(state: Bundle) {
        state.getSafeParcelable<Parcelable>("pageRecyclerview")?.let { recycle ->
            binding.pageRecyclerview.layoutManager?.onRestoreInstanceState(recycle)
        }
    }
}

class ViewpagerAdapter(
    private val downloadViewModel: DownloadViewModel,
    val fragment: Fragment,
    val scrollCallback: (isScrollingDown: Boolean) -> Unit,
) : BaseAdapter<Page, Bundle>(
    id = "ViewpagerAdapter".hashCode(),
    diffCallback = BaseDiffCallback(
        itemSame = { a, b ->
            a.title == b.title
        },
        contentSame = { a, b ->
            a.items == b.items && a.title == b.title
        }
    )) {

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Bundle> {
        return ViewpagerAdapterViewHolderState(
            ViewpagerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: Page,
        position: Int
    ) {
        val binding = holder.view
        if (binding !is ViewpagerPageBinding) return
        (binding.pageRecyclerview.adapter as? AnyAdapter)?.submitList(item.items)
    }

    /*fun setupGridView(context : Context) {
        val compactView = context.getDownloadIsCompact()

        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = context.resources.configuration.orientation

        val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }

        notifyDataSetChanged()
    }*/


    val collectionsOfRecyclerView = mutableMapOf<Int, WeakReference<AutofitRecyclerView>>()
    fun updateProgressOfPage(tab: Int) {
        val rv = collectionsOfRecyclerView[tab]?.get() ?: return
        val ad = rv.adapter as? AnyAdapter ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == -1 || lastVisible == -1) return

        val start =  (firstVisible - 3).coerceAtLeast(0)

        val end =  (lastVisible + 3).coerceAtMost(ad.itemCount - 1)

        val count = (end - start) + 1

        if (count > 0) {
            rv.post {
                ad.notifyItemRangeChanged(start, count, "new")
            }
        }
    }
    override fun onBindContent(holder: ViewHolderState<Bundle>, item: Page, position: Int) {
        val binding = holder.view
        if (binding !is ViewpagerPageBinding) return

        binding.pageRecyclerview.tag = position
        binding.pageRecyclerview.apply {
            val compactView = binding.root.context.getDownloadIsCompact()

            val spanCountLandscape = if (compactView) 2 else 6
            val spanCountPortrait = if (compactView) 1 else 3
            val orientation = resources.configuration.orientation

            spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                spanCountLandscape
            } else {
                spanCountPortrait
            }

            if (adapter == null) { //  || rebind
                // Only add the items after it has been attached since the items rely on ItemWidth
                // Which is only determined after the recyclerview is attached.
                // If this fails then item height becomes 0 when there is only one item
                doOnAttach {
                    setRecycledViewPool(AnyAdapter.sharedPool)
                    adapter = AnyAdapter(
                        this,
                        downloadViewModel
                    ).apply {
                        footers = if(position == 0) 1 else 0
                        collectionsOfRecyclerView[position] = WeakReference(binding.pageRecyclerview)
                        setHasStableIds(true)
                        submitList(item.items)
                    }
                }
            } else {
                if(!collectionsOfRecyclerView.containsKey(position)){
                    collectionsOfRecyclerView[position] = WeakReference(binding.pageRecyclerview)
                }
                (adapter as? AnyAdapter)?.apply {
                    footers = if(position == 0) 1 else 0
                    submitList(item.items)
                }
                // scrollToPosition(0)
            }
            clearOnScrollListeners()
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy != 0) {
                        scrollCallback.invoke(dy > 0)
                    }
                }
            })
        }
    }
}