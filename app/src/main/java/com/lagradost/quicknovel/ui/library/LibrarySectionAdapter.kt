package com.lagradost.quicknovel.ui.library
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.DEFAULT_LIBRARIES
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.databinding.ItemLibrarySectionBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import java.util.Collections

class LibrarySectionAdapter(
    private val onRenameClick: (DefaultLibrary) -> Unit,
    private val onMergeClick: (DefaultLibrary) -> Unit,
    private val onDeleteClick: (DefaultLibrary) -> Unit,
    private val onDragFinished: () -> Unit,
) : NoStateAdapter<DefaultLibrary>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.id == b.id },
        contentSame = { a, b -> a == b }
    )
) {
    private var counts = mutableMapOf<Int, Int>()
    private val builtInKeys = DEFAULT_LIBRARIES.map { it.key }.toSet()
    val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            moveItemVisual(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        override fun isLongPressDragEnabled(): Boolean = false

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            onDragFinished()
        }
    })

    fun submitList(newItems: List<DefaultLibrary>, novelCounts: Map<Int, Int> = emptyMap()) {
        counts.clear()
        counts.putAll(novelCounts)
        super.submitList(newItems, null)
    }

    fun moveItemVisual(fromPosition: Int, toPosition: Int) {
        val currentItems = immutableCurrentList.toMutableList()
        if (fromPosition !in currentItems.indices || toPosition !in currentItems.indices) return

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentItems, i, i - 1)
            }
        }
        submitList(currentItems, counts)
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ItemLibrarySectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: DefaultLibrary, position: Int) {
        val binding = holder.view as? ItemLibrarySectionBinding ?: return
        val context = binding.root.context
        val isBuiltIn = item.key in builtInKeys

        val resId = item.title.toIntOrNull()
        binding.sectionName.text = if (resId != null) context.getString(resId) else item.title

        binding.novelCount.text = "${counts[item.id] ?: 0}"


        binding.actionRename.isVisible = isBuiltIn || item.editable
        binding.actionMerge.isVisible = !isBuiltIn && item.editable
        binding.actionDelete.isVisible = !isBuiltIn && item.editable

        binding.actionRename.setOnClickListener { onRenameClick(item) }
        binding.actionMerge.setOnClickListener { onMergeClick(item) }
        binding.actionDelete.setOnClickListener { onDeleteClick(item) }

        binding.root.setOnClickListener { if (isBuiltIn) onRenameClick(item) }

        binding.dragHandle.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performClick()
                itemTouchHelper.startDrag(holder)
            }
            false
        }
    }
}