package com.lagradost.quicknovel.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.getSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.FailedSpanned
import com.lagradost.quicknovel.LoadingSpanned
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.SpanDisplay
import com.lagradost.quicknovel.TextSpan
import com.lagradost.quicknovel.databinding.SingleFailedBinding
import com.lagradost.quicknovel.databinding.SingleImageBinding
import com.lagradost.quicknovel.databinding.SingleLoadingBinding
import com.lagradost.quicknovel.databinding.SingleTextBinding
import com.lagradost.quicknovel.util.UIHelper.setImage
import io.noties.markwon.image.AsyncDrawableSpan

const val DRAW_DRAWABLE = 1
const val DRAW_TEXT = 0
const val DRAW_LOADING = 2
const val DRAW_FAILED = 3

data class ScrollVisibility(
    val firstVisible: Int,
    val firstFullyVisible: Int,
    val lastVisible: Int,
    val lastFullyVisible: Int,
)

data class ScrollIndex(
    val index: Int,
    val innerIndex : Int,
)

data class ScrollVisibilityIndex(
    val firstVisible: ScrollIndex,
    val firstFullyVisible: ScrollIndex,
    val lastVisible: ScrollIndex,
    val lastFullyVisible: ScrollIndex,
)

class TextAdapter(private val viewModel: ReadActivityViewModel) :
    ListAdapter<SpanDisplay, TextAdapter.TextAdapterHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextAdapterHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ViewBinding = when (viewType) {
            DRAW_TEXT -> SingleTextBinding.inflate(inflater, parent, false)
            DRAW_DRAWABLE -> SingleImageBinding.inflate(inflater, parent, false)
            DRAW_LOADING -> SingleLoadingBinding.inflate(inflater, parent, false)
            DRAW_FAILED -> SingleFailedBinding.inflate(inflater, parent, false)
            else -> throw NotImplementedError()
        }

        return TextAdapterHolder(binding, viewModel)
    }

    private fun transformIndexToScrollIndex(index: Int): ScrollIndex? {
        if(index < 0||index >= itemCount) return null
        val item = getItem(index)
        return ScrollIndex(index = item.index, innerIndex = item.innerIndex)
    }

    fun getIndex(data: ScrollVisibility): ScrollVisibilityIndex? {
        return ScrollVisibilityIndex(
            firstVisible = transformIndexToScrollIndex(data.firstVisible) ?: return null,
            firstFullyVisible = transformIndexToScrollIndex(data.firstFullyVisible) ?: return null,
            lastFullyVisible = transformIndexToScrollIndex(data.lastFullyVisible) ?: return null,
            lastVisible = transformIndexToScrollIndex(data.lastVisible) ?: return null,
        )
    }

    override fun onBindViewHolder(holder: TextAdapterHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is TextSpan -> {
                if (item.text.getSpans<AsyncDrawableSpan>(0, item.text.length).isNotEmpty()) {
                    DRAW_DRAWABLE
                } else {
                    DRAW_TEXT
                }
            }

            is LoadingSpanned -> {
                DRAW_LOADING
            }

            is FailedSpanned -> {
                DRAW_FAILED
            }

            else -> throw NotImplementedError()
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    class TextAdapterHolder(private val binding: ViewBinding, private val viewModel: ReadActivityViewModel) :
        RecyclerView.ViewHolder(binding.root) {

        private fun bindText(obj: TextSpan) {
            when (binding) {
                is SingleImageBinding -> {
                    val img = obj.text.getSpans<AsyncDrawableSpan>(0, obj.text.length)[0]
                    img.drawable.result?.let { drawable ->
                        binding.root.setImageDrawable(drawable)
                    } ?: kotlin.run {
                        binding.root.setImage(img.drawable.destination)
                    }
                }

                is SingleTextBinding -> {
                    binding.root.text = obj.text
                }

                else -> throw NotImplementedError()
            }
        }

        private fun bindLoading(obj: LoadingSpanned) {
            if (binding !is SingleLoadingBinding) throw NotImplementedError()
            binding.root.text = obj.url?.let { "Loading $it" } ?: "Loading"
        }

        private fun bindFailed(obj: FailedSpanned) {
            if (binding !is SingleFailedBinding) throw NotImplementedError()
            binding.root.text = obj.reason
        }

        fun bind(obj: SpanDisplay) {
            binding.root.setOnClickListener {
                viewModel.switchVisibility()
            }
            when (obj) {
                is TextSpan -> {
                    this.bindText(obj)
                }

                is LoadingSpanned -> {
                    this.bindLoading(obj)
                }

                is FailedSpanned -> {
                    this.bindFailed(obj)
                }

                else -> throw NotImplementedError()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SpanDisplay>() {
        override fun areItemsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean {
            return when (oldItem) {
                is TextSpan -> {
                    if (newItem !is TextSpan) return false
                    // don't check the span content as that does not change
                    return newItem.end == oldItem.end && newItem.start == oldItem.start && newItem.index != oldItem.index
                }

                is LoadingSpanned -> {
                    if (newItem !is LoadingSpanned) return false

                    newItem != oldItem
                }

                is FailedSpanned -> {
                    if (newItem !is FailedSpanned) return false

                    newItem != oldItem
                }

                else -> throw NotImplementedError()
            }
        }
    }
}