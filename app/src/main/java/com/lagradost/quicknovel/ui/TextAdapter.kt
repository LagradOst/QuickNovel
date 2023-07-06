package com.lagradost.quicknovel.ui

import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.getSpans
import androidx.core.text.toSpanned
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.databinding.SingleImageBinding
import com.lagradost.quicknovel.databinding.SingleTextBinding
import com.lagradost.quicknovel.util.UIHelper.setImage
import io.noties.markwon.SpannableBuilder
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.image.AsyncDrawableSpan

// var spanned: Spanned
class TextAdapter : ListAdapter<Spanned, TextAdapter.TextAdapterHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextAdapterHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ViewBinding = when (viewType) {
            0 -> SingleTextBinding.inflate(inflater, parent, false)
            1 -> SingleImageBinding.inflate(inflater, parent, false)
            else -> throw NotImplementedError()
        }

        return TextAdapterHolder(binding)
    }

    override fun onBindViewHolder(holder: TextAdapterHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)

        if (item.getSpans<AsyncDrawableSpan>(0, item.length).isNotEmpty()) {
            return 1
        }

        return 0
    }

    class TextAdapterHolder(private val binding: ViewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(obj: Spanned) {
            binding.apply {
                when (binding) {
                    is SingleImageBinding -> {
                        val img = obj.getSpans<AsyncDrawableSpan>(0, obj.length)[0]
                        img.drawable.result?.let { drawable ->
                            binding.root.setImageDrawable(drawable)
                        } ?: kotlin.run {
                            binding.root.setImage(img.drawable.destination)
                        }
                    }

                    is SingleTextBinding -> {
                        /*val start = spanned.getSpanStart(obj)
                                val end = spanned.getSpanEnd(obj)

                                println("SPAN: $start->$end ${spanned.substring(start,end)}")
                                val builder = SpannableBuilder(spanned.substring(start,end))
                                builder.setSpan(obj,0, builder.length)*/
                        binding.root.text = obj
                    }

                    else -> {
                        throw NotImplementedError()
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Spanned>() {
        override fun areItemsTheSame(oldItem: Spanned, newItem: Spanned): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Spanned, newItem: Spanned): Boolean = oldItem == newItem
    }
}