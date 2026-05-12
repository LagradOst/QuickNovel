package com.lagradost.quicknovel.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.DEFAULT_LIBRARIES
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.databinding.ItemLibrarySectionBinding
import java.util.Collections

class LibrarySectionAdapter(
    private val onRenameClick: (DefaultLibrary) -> Unit,
    private val onMergeClick: (DefaultLibrary) -> Unit,
    private val onDeleteClick: (DefaultLibrary) -> Unit,
) : RecyclerView.Adapter<LibrarySectionAdapter.ViewHolder>() {

    private val items = mutableListOf<DefaultLibrary>()
    val _items get() = items
    private val builtInKeys = DEFAULT_LIBRARIES.map { it.key }.toSet()

    fun submitList(newItems: List<DefaultLibrary>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun moveItemVisual(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= items.size ||
            toPosition < 0 || toPosition >= items.size) return

        Collections.swap(items, fromPosition, toPosition)

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLibrarySectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isBuiltIn = item.key in builtInKeys
        val canRename = isBuiltIn || item.editable
        val canMergeDelete = !isBuiltIn && item.editable
        holder.binding.sectionName.text = item.title

        holder.binding.actionRename.visibility = if (canRename) View.VISIBLE else View.GONE
        holder.binding.actionMerge.visibility = if (canMergeDelete) View.VISIBLE else View.GONE
        holder.binding.actionDelete.visibility = if (canMergeDelete) View.VISIBLE else View.GONE

        holder.binding.actionRename.setOnClickListener { onRenameClick(item) }
        holder.binding.actionMerge.setOnClickListener { onMergeClick(item) }
        holder.binding.actionDelete.setOnClickListener { onDeleteClick(item) }

        // Built-in rows are rename-only, so tapping the row acts as a direct rename shortcut.
        holder.itemView.setOnClickListener {
            if (isBuiltIn) onRenameClick(item)
        }

        holder.itemView.setOnLongClickListener { false }
    }

    inner class ViewHolder(val binding: ItemLibrarySectionBinding) : RecyclerView.ViewHolder(binding.root)
}