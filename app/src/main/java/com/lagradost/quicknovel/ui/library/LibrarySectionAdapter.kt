package com.lagradost.quicknovel.ui.library

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.DEFAULT_LIBRARIES
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.ItemLibrarySectionBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import java.util.Collections

class LibrarySectionAdapter(
    private var selectedIndex: Int,
    private val onDragFinished: (List<DefaultLibrary>) -> Unit,
    private val onItemClick: (Int) -> Unit,
    private val onRename: (DefaultLibrary, LibrarySectionAdapter) -> Unit,
    private val onDelete: (DefaultLibrary, LibrarySectionAdapter) -> Any,
    private val onMerge: (DefaultLibrary, LibrarySectionAdapter) -> Any,
) : NoStateAdapter<DefaultLibrary>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.id == b.id },
        contentSame = { a, b -> a == b }
    )
) {
    //This will show or hide the buttons that allow editing attributes
    private var isEditing = false

    //Prevent the delete buttons from being displayed for the default libraries.
    private val builtInKeys = DEFAULT_LIBRARIES.map { it.key }.toSet()

    //This handles dragging the titles and updating their positions
    val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            //In several places, I apply similar checks to prevent the user from interacting
            //with the first library, which is the None library
            if (viewHolder.bindingAdapterPosition == 0) return makeMovementFlags(0,0)
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPos = viewHolder.bindingAdapterPosition
            val targetPos = target.bindingAdapterPosition

            //Prevent the None library from being moved
            if (fromPos == 0 || targetPos == 0) return false

            moveItemVisual(fromPos, targetPos)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        override fun isLongPressDragEnabled(): Boolean = false

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            onDragFinished(immutableCurrentList.filter { it.id != -1 })
        }
    })

    fun moveItemVisual(fromPosition: Int, toPosition: Int) {
        val currentItems = immutableCurrentList.toMutableList()
        //Do not replace the position of None
        if (fromPosition <= 0 || toPosition <= 0) return
        //This could all be done in a single if, but using three makes it clearer
        if (fromPosition !in currentItems.indices) return
        if (toPosition !in currentItems.indices) return
        if(fromPosition == toPosition) return
        Collections.swap(currentItems, fromPosition, toPosition)
        submitList(currentItems)
    }

    fun changeEditingStatus(newStatus: Boolean) {
        isEditing = newStatus
        //It's the only way I could think of to update everyone's state so the special buttons are shown
        notifyDataSetChanged()
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ItemLibrarySectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindContent(holder: ViewHolderState<Any>, item: DefaultLibrary, position: Int) {
        val binding = holder.view as? ItemLibrarySectionBinding ?: return

        //is a default library?
        val isBuiltIn = item.key in builtInKeys
        //is currently selected?
        val isSelected = position == selectedIndex
        //is library None?
        val isSpecial = item.id == -1

        binding.root.isActivated = isSelected
        binding.libraryTitle.text = item.title


        //Decide left icon
        //If edit mode is enabled and it's not the None library
        if (isEditing && !isSpecial) {
            binding.dragHandle.setImageResource(R.drawable.ic_baseline_drag_handle_24)
        } else if (isSelected) {//It is not being edited and the library is selected
            binding.dragHandle.setImageResource(R.drawable.ic_baseline_check_24)
        }
        else{
            binding.dragHandle.setImageResource(0)
        }


        val canEdit = !isBuiltIn && item.editable && isEditing
        binding.actionOptions.isVisible = canEdit


        binding.libraryTitle.setOnClickListener {
            //Decide what happens when I click on the library name
            if (isEditing && !isSpecial) {
                onRename(item, this@LibrarySectionAdapter)
            } else {
                onItemClick(position)
            }
        }
        binding.actionOptions.setOnClickListener {
            it.popupMenu(
                items = listOf(
                    1 to R.string.library_merge,
                    2 to R.string.library_delete
                )
            ){
                when(itemId){
                    1 -> onMerge(item, this@LibrarySectionAdapter)
                    2 -> onDelete(item, this@LibrarySectionAdapter)
                }
            }
        }

        binding.dragHandle.setOnTouchListener { _, event ->
            if (isEditing &&
                !isSpecial &&
                (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP)) {
                itemTouchHelper.startDrag(holder)
            }
            false
        }
    }
}