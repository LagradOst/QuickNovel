package com.lagradost.quicknovel.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.lagradost.quicknovel.updateLibrary

class LibrarySectionFragment : Fragment() {
    private var _binding: FragmentLibrarySectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LibrarySectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibrarySectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition

                adapter.moveItemVisual(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            //user drop the item
            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                saveNewOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.libraryToolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        adapter = LibrarySectionAdapter(
            onRenameClick = ::showRenameDialog,
            onMergeClick = ::showMergeDialog,
            onDeleteClick = ::showDeleteDialog,
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)

        binding.fabAddFolder.setOnClickListener { showCreateDialog() }
        refresh()
    }

    private fun saveNewOrder() {
        val _context = context ?: return
        val currentItems = adapter._items.toList()

        postLibraryAction {
            val reorderedList = currentItems.mapIndexed { index, library ->
                library.copy(position = index + 1)
            }
            _context.saveLibraries(reorderedList)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        val ctx = requireContext()
        adapter.submitList(ctx.getLibraries())
    }

    private inline fun postLibraryAction(crossinline action: () -> Unit) {
        val root = _binding?.root ?: return
        root.post {
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
        val inputView = layoutInflater.inflate(R.layout.dialog_add_folder, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.library_create)
            .setView(inputView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val editText = inputView.findViewById<EditText>(R.id.editFolderName)
                val title = editText.text.toString().trim()
                if (title.isNotEmpty()) {
                    val context = requireContext()
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
        inputView.findViewById<EditText>(R.id.editFolderName).setText(item.title)
        MaterialAlertDialogBuilder(context)
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
        val targetCandidates = context.getLibraries().filter { it.id != item.id }
        if (targetCandidates.isEmpty()) return

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.library_merge)
            .setItems(targetCandidates.map { it.title }.toTypedArray()) { dialog, which ->
                val target = targetCandidates.getOrNull(which) ?: return@setItems
                dialog.dismiss()
                postLibraryAction {
                    context.mergeLibraries(item.id, target.id)
                    refresh()
                }
            }
            .show()
    }

    private fun showDeleteDialog(item: DefaultLibrary) {
        val context = requireContext()
        val inUse = requireContext().getLibraryBookmarkCount(item.id)
        if (inUse > 0) {
            showToast(R.string.library_delete_empty_only_message)
            return
        }

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.library_delete)
        builder.setMessage(R.string.library_delete_message)
        builder.setPositiveButton(R.string.delete) { dialog, _ ->
            dialog.dismiss()
            postLibraryAction {
                context.deleteLibrary(item.id)
                refresh()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

}