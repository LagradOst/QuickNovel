package com.lagradost.quicknovel.ui.library

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.addLibrary
import com.lagradost.quicknovel.databinding.BottomSelectionLibrariesBinding
import com.lagradost.quicknovel.deleteLibrary
import com.lagradost.quicknovel.getLibraries
import com.lagradost.quicknovel.mergeLibraries
import com.lagradost.quicknovel.updateLibrary
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog

object LibraryManager {

    //show recyclerview with libraries
    fun showLibraryBottomDialog(
        context: Context,
        list: List<DefaultLibrary>,
        selectedIndex: Int = -1,
        refreshVisual: Event<Boolean>,
        title: String,
        callback: (Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSelectionLibrariesBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        //hide or show edit properties
        var isEditing = false
        binding.bottomSelectionLibrariesTitle.text = title

        val libraryAdapter = LibrarySectionAdapter(
            selectedIndex,
            //Update the position of all libraries
            onDragFinished = { newList ->
                newList.forEachIndexed { index, lib ->
                    context.updateLibrary(lib.copy(position = index + 1))
                }
                refreshVisual.invoke(true)
            },
            //Change a book's library
            onItemClick = { item ->
                callback.invoke(item)
                dialog.dismiss()
            },
            //Rename library
            onRename = { item, adapter -> showInputDialog(context, item.title, R.string.library_rename) { newName ->
                context.updateLibrary(item.copy(title = newName))
                refreshInternal(context, adapter, refreshVisual)
            }},
            //Delete library
            onDelete = { item, adapter ->
                showSimpleDialog(context, R.string.library_delete, context.getString(R.string.permanently_delete_format).format(item.title)){
                    context.deleteLibrary(item.id)
                    refreshInternal(context, adapter, refreshVisual)
                }
            },
            //Merge libraries
            onMerge = { item, adapter ->
                //get all libraries except the one selected
                val targetCandidates = context.getLibraries().filter { it.id != item.id }
                if (targetCandidates.isNotEmpty()) {
                    context.showBottomDialog(
                        items = targetCandidates.map { it.title },
                        selectedIndex = -1,
                        name = context.getString(R.string.library_merge),
                        showApply = false,
                        dismissCallback = {},
                        callback = { which ->
                            val target = targetCandidates.getOrNull(which) ?: return@showBottomDialog
                            context.mergeLibraries(item.id, target.id)
                            refreshInternal(context, adapter, refreshVisual)
                        }
                    )
                }
            }
        )
        //recyclerview
        binding.listview1.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = libraryAdapter
            libraryAdapter.itemTouchHelper.attachToRecyclerView(this)
        }

        //Add a new Library
        binding.actionAdd.setOnClickListener {
            showInputDialog(context, "", R.string.library_create) { name ->
                context.addLibrary(name)
                refreshInternal(context, libraryAdapter, refreshVisual)
            }
        }

        //Edit button to show edit properties
        binding.actionEdit.setOnClickListener {
            isEditing = !isEditing
            binding.actionAdd.isVisible = isEditing
            libraryAdapter.changeEditingStatus(isEditing)
            binding.actionEdit.setImageResource(
                if (isEditing) R.drawable.ic_sharp_clear_24
                else R.drawable.ic_baseline_edit_24
            )
        }

        libraryAdapter.submitList(list)
        dialog.show()
    }

    //this will update ui
    private fun refreshInternal(context: Context, adapter: LibrarySectionAdapter, refreshVisual: Event<Boolean>) {
        val updatedList = context.getLibraries().sortedBy { it.position }
        adapter.submitList(updatedList)
        refreshVisual.invoke(true)
    }


    private fun showSimpleDialog(context: Context, titleRes: Int, text:String, onConfirm: () -> Unit) {
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(titleRes)
            .setMessage(text)
            .setPositiveButton(R.string.ok) { d, _ ->
                try {
                    onConfirm()
                }catch (e: Exception){
                    showToast(e.message)
                }
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }


    private fun showInputDialog(context: Context, initialText: String, titleRes: Int, onConfirm: (String) -> Unit) {
        val inputView = LayoutInflater.from(context).inflate(R.layout.dialog_add_folder, null)
        val editText = inputView.findViewById<EditText>(R.id.editFolderName)
        editText.setText(initialText)

        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(titleRes)
            .setView(inputView)
            .setPositiveButton(R.string.save) { d, _ ->
                try {
                    onConfirm(editText.text.toString().trim())
                }catch (e: Exception){
                    showToast(e.message)
                }
                d.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }
}