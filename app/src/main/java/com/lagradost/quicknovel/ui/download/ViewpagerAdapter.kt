package com.lagradost.quicknovel.ui.download

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView.OnFlingListener
import com.lagradost.quicknovel.databinding.ViewpagerPageBinding
import com.lagradost.quicknovel.ui.BaseAdapter
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.SettingsHelper.getDownloadIsCompact

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
                        setHasStableIds(true)
                        submitList(item.items)
                    }
                }
            } else {
                (adapter as? AnyAdapter)?.apply {
                    footers = if(position == 0) 1 else 0
                    submitList(item.items)
                }
                // scrollToPosition(0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val diff = scrollY - oldScrollY
                    if (diff == 0) return@setOnScrollChangeListener

                    scrollCallback.invoke(diff > 0)
                }
            } else {
                onFlingListener = object : OnFlingListener() {
                    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                        scrollCallback.invoke(velocityY > 0)
                        return false
                    }
                }
            }
        }
    }
}