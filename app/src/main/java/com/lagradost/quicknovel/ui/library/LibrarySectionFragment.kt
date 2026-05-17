package com.lagradost.quicknovel.ui.library

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.CommonActivity.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.addLibrary
import com.lagradost.quicknovel.databinding.FragmentLibrarySectionBinding
import com.lagradost.quicknovel.deleteLibrary
import com.lagradost.quicknovel.getLibraries
import com.lagradost.quicknovel.getLibraryBookmarkCount
import com.lagradost.quicknovel.mergeLibraries
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.saveLibraries
import com.lagradost.quicknovel.ui.BaseFragment
import com.lagradost.quicknovel.updateLibrary
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog

class LibrarySectionFragment : BaseFragment<FragmentLibrarySectionBinding>(
    BindingCreator.Inflate(FragmentLibrarySectionBinding::inflate)
) {
    private lateinit var adapter: LibrarySectionAdapter

    override fun onBindingCreated(binding: FragmentLibrarySectionBinding) {
        adapter = LibrarySectionAdapter(
            onRenameClick = ::showRenameDialog,
            onMergeClick = ::showMergeDialog,
            onDeleteClick = ::showDeleteDialog,
            onDragFinished = { saveNewOrder() }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        binding.fabAddFolder.setOnClickListener { showCreateDialog() }
        binding.libraryToolbar.setNavigationOnClickListener { dispatchBackPressed() }
        refresh()
    }

    override fun fixLayout(view: View) {
    }

    private fun saveNewOrder() {
        val ctx = context ?: return
        val currentItems = adapter.immutableCurrentList
        val reorderedList = currentItems.mapIndexed { index, library ->
            library.copy(position = index + 1)
        }
        postLibraryAction {
            ctx.saveLibraries(reorderedList)
        }
    }

    private fun refresh() {
        val ctx = context ?: return
        val libs = ctx.getLibraries()
        val counts = libs.associate { lib -> lib.id to ctx.getLibraryBookmarkCount(lib.id) }
        adapter.submitList(libs, counts)
    }

    private inline fun postLibraryAction(crossinline action: () -> Unit) {
        binding?.root?.post {
            runLibraryAction(action)
        }
    }

    private inline fun runLibraryAction(crossinline action: () -> Unit) {
        try {
            action()
            showToast(R.string.done)
        } catch (t: Throwable) {
            logError(t)
            showToast(t.message ?: getString(R.string.error_loading))
        }
    }

    private fun showCreateDialog() {
        val context = requireContext()
        val inputView = layoutInflater.inflate(R.layout.dialog_add_folder, null)
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.library_create)
            .setView(inputView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val editText = inputView.findViewById<EditText>(R.id.editFolderName)
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    dialog.dismiss()
                    postLibraryAction {
                        val current = context.getLibraries()
                        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
                        val nextPosition = (current.maxOfOrNull { it.position } ?: 0) + 1
                        context.addLibrary(
                            DefaultLibrary(
                                id = nextId,
                                key = "CUSTOM_$nextId",
                                title = title,
                                editable = true,
                                position = nextPosition
                            )
                        )
                        refresh()
                    }
                    return@setPositiveButton
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(item: DefaultLibrary) {
        val context = requireContext()
        val inputView = layoutInflater.inflate(R.layout.dialog_add_folder, null)

        val resId = item.title.toIntOrNull()
        val currentTitle = if (resId != null) context.getString(resId) else item.title

        inputView.findViewById<EditText>(R.id.editFolderName).setText(currentTitle)

        MaterialAlertDialogBuilder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.library_rename)
            .setView(inputView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val editText = inputView.findViewById<EditText>(R.id.editFolderName)
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    dialog.dismiss()
                    postLibraryAction {
                        context.updateLibrary(item.copy(title = title))
                        refresh()
                    }
                    return@setPositiveButton
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMergeDialog(item: DefaultLibrary) {
        val context = requireContext()
        val libraries = context.getLibraries()
        val targetCandidates = libraries.filter { it.id != item.id }
        if (targetCandidates.isEmpty()) return

        val names = targetCandidates.map { lib ->
            val resId = lib.title.toIntOrNull()
            if (resId != null) context.getString(resId) else lib.title
        }

        context.showBottomDialog(
            items = names,
            selectedIndex = -1,
            name = getString(R.string.library_merge),
            showApply = false,
            dismissCallback = {},
            callback = { which ->
                val target = targetCandidates.getOrNull(which) ?: return@showBottomDialog
                postLibraryAction {
                    context.mergeLibraries(item.id, target.id)
                    refresh()
                }
            }
        )
    }

    private fun showDeleteDialog(item: DefaultLibrary) {
        val context = requireContext()
        val inUse = context.getLibraryBookmarkCount(item.id)
        if (inUse > 0) {
            showToast(R.string.library_delete_empty_only_message)
            return
        }

        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.library_delete)
            .setMessage(R.string.library_delete_message)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                dialog.dismiss()
                postLibraryAction {
                    context.deleteLibrary(item.id)
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}