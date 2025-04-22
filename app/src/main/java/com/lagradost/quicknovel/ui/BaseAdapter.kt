package com.lagradost.quicknovel.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import java.util.concurrent.CopyOnWriteArrayList

open class ViewHolderState<T>(val view: ViewBinding) : ViewHolder(view.root) {
    open fun save(): T? = null
    open fun restore(state: T) = Unit
    open fun onViewAttachedToWindow() = Unit
    open fun onViewDetachedFromWindow() = Unit
    open fun onViewRecycled() = Unit
}


// Based of the concept https://github.com/brahmkshatriya/echo/blob/main/app%2Fsrc%2Fmain%2Fjava%2Fdev%2Fbrahmkshatriya%2Fecho%2Fui%2Fadapters%2FMediaItemsContainerAdapter.kt#L108-L154
class StateViewModel : ViewModel() {
    val layoutManagerStates = hashMapOf<Int, HashMap<Int, Any?>>()
}

abstract class NoStateAdapter<T : Any>(fragment: Fragment) : BaseAdapter<T, Any>(fragment, 0)

/**
 * BaseAdapter is a persistent state stored adapter that supports headers and footers.
 * This should be used for restoring eg scroll or focus related to a view when it is recreated.
 *
 * Id is a per fragment based unique id used to store the underlying data done in an internal ViewModel.
 *
 * diffCallback is how the view should be handled when updating, override onUpdateContent for updates
 *
 * NOTE:
 *
 * By default it should save automatically, but you can also call save(recycle)
 *
 * By default no state is stored, but doing an id != 0 will store
 *
 * By default no headers or footers exist, override footers and headers count
 */
abstract class BaseAdapter<
        T : Any,
        S : Any>(
    fragment: Fragment,
    val id: Int = 0,
    diffCallback: DiffUtil.ItemCallback<T> = BaseDiffCallback()
) : RecyclerView.Adapter<ViewHolderState<S>>() {
    open val footers: Int = 0
    open val headers: Int = 0

    protected open fun getItemViewTypeCustom(item: Any): Int {
        return CONTENT
    }

    fun getItem(position: Int): T {
        return mDiffer.currentList[position]
    }

    fun getItemOrNull(position: Int): T? {
        return mDiffer.currentList.getOrNull(position)
    }

    private val mDiffer: AsyncListDiffer<T> = AsyncListDiffer(
        object : NonFinalAdapterListUpdateCallback(this) {
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                super.onMoved(fromPosition + headers, toPosition + headers)
            }

            override fun onRemoved(position: Int, count: Int) {
                super.onRemoved(position + headers, count)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                super.onChanged(position + headers, count, payload)
            }

            override fun onInserted(position: Int, count: Int) {
                super.onInserted(position + headers, count)
            }
        },
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    open fun submitList(list: List<T>?) {
        // deep copy at least the top list, because otherwise adapter can go crazy
        mDiffer.submitList(list?.let { CopyOnWriteArrayList(it) })
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size + footers + headers
    }

    open fun onUpdateContent(holder: ViewHolderState<S>, item: T, position: Int) =
        onBindContent(holder, item, position)

    open fun onBindContent(holder: ViewHolderState<S>, item: T, position: Int) = Unit
    open fun onBindFooter(holder: ViewHolderState<S>) = Unit
    open fun onBindHeader(holder: ViewHolderState<S>) = Unit
    open fun onCreateContent(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateFooter(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateHeader(parent: ViewGroup): ViewHolderState<S> = throw NotImplementedError()
    open fun onCreateCustom(parent: ViewGroup, viewType: Int): ViewHolderState<S> =
        throw NotImplementedError()

    override fun onViewAttachedToWindow(holder: ViewHolderState<S>) {
        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<S>) {
        holder.onViewDetachedFromWindow()
    }

    @Suppress("UNCHECKED_CAST")
    fun save(recyclerView: RecyclerView) {
        for (child in recyclerView.children) {
            val holder =
                recyclerView.findContainingViewHolder(child) as? ViewHolderState<S> ?: continue
            setState(holder)
        }
    }

    fun clear() {
        stateViewModel.layoutManagerStates[id]?.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getState(holder: ViewHolderState<S>): S? =
        stateViewModel.layoutManagerStates[id]?.get(holder.absoluteAdapterPosition) as? S

    private fun setState(holder: ViewHolderState<S>) {
        if (id == 0) return

        if (!stateViewModel.layoutManagerStates.contains(id)) {
            stateViewModel.layoutManagerStates[id] = HashMap()
        }
        stateViewModel.layoutManagerStates[id]?.let { map ->
            map[holder.absoluteAdapterPosition] = holder.save()
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            if (v !is RecyclerView) return
            save(v)
        }
    }

    final override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnAttachStateChangeListener(attachListener)
        super.onAttachedToRecyclerView(recyclerView)
    }

    final override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnAttachStateChangeListener(attachListener)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    final override fun getItemViewType(position: Int): Int {
        if (position < headers) {
            return HEADER
        }
        if (position - headers >= mDiffer.currentList.size) {
            return FOOTER
        }

        return getItemViewTypeCustom(mDiffer.currentList[position - headers])
    }

    private val stateViewModel: StateViewModel by fragment.viewModels()

    final override fun onViewRecycled(holder: ViewHolderState<S>) {
        setState(holder)
        holder.onViewRecycled()
        super.onViewRecycled(holder)
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderState<S> {
        return when (viewType) {
            CONTENT -> onCreateContent(parent)
            HEADER -> onCreateHeader(parent)
            FOOTER -> onCreateFooter(parent)
            else -> onCreateCustom(parent, viewType)
        }
    }

    // https://medium.com/@domen.lanisnik/efficiently-updating-recyclerview-items-using-payloads-1305f65f3068
    override fun onBindViewHolder(
        holder: ViewHolderState<S>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        when (getItemViewType(position)) {
            FOOTER -> {
                onBindFooter(holder)
            }

            HEADER -> {
                onBindHeader(holder)
            }

            else -> {
                val realPosition = position - headers
                val item = getItem(realPosition)
                onUpdateContent(holder, item, realPosition)
            }
        }
    }

    final override fun onBindViewHolder(holder: ViewHolderState<S>, position: Int) {
        when (getItemViewType(position)) {
            FOOTER -> {
                onBindFooter(holder)
            }

            HEADER -> {
                onBindHeader(holder)
            }

            else -> {
                val realPosition = position - headers
                val item = getItem(realPosition)
                onBindContent(holder, item, realPosition)
            }
        }

        getState(holder)?.let { state ->
            holder.restore(state)
        }
    }

    companion object {
        const val HEADER: Int = Int.MAX_VALUE - 1
        const val FOOTER: Int = Int.MAX_VALUE - 2
        const val CONTENT: Int = Int.MAX_VALUE - 0
    }
}

class BaseDiffCallback<T : Any>(
    val itemSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() },
    val contentSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() }
) : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemSame(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentSame(oldItem, newItem)
    override fun getChangePayload(oldItem: T, newItem: T): Any = Any()
}