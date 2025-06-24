package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.databinding.ResultReviewBinding
import com.lagradost.quicknovel.util.SettingsHelper.getRatingReview
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.setImage

class ReviewAdapter2 :
    ListAdapter<UserReview, ReviewAdapter2.ReviewAdapter2Holder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewAdapter2Holder {
        val binding =
            ResultReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewAdapter2Holder(binding)
    }

    override fun onBindViewHolder(holder: ReviewAdapter2Holder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class ReviewAdapter2Holder(private val binding: ResultReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: UserReview) {
            binding.apply {
                val localContext = this.root.context ?: return

                val expandedText = card.review.replace("\n", "")
                var reviewText = expandedText
                if (reviewText.length > MAX_SYNO_LENGH) {
                    reviewText = reviewText.substring(0, MAX_SYNO_LENGH) + "..."
                }
                var isExpanded = false

                reviewBody.setOnClickListener {
                    isExpanded = !isExpanded
                    reviewBody.text = if(isExpanded) expandedText.html() else reviewText.html()
                }

                reviewBody.text = reviewText.html()
                reviewTitle.text = card.reviewTitle.html()
                reviewTitle.isGone = card.reviewTitle.isNullOrBlank()

                reviewTime.text = card.reviewDate
                reviewAuthor.text = card.username

                reviewImage.setImage(card.avatarUrl)

                reviewTags.apply {
                    removeAllViews()

                    val context = reviewTags.context

                    card.rating?.let { rating ->
                        val chip = Chip(context)
                        val chipDrawable = ChipDrawable.createFromAttributes(
                            context,
                            null,
                            0,
                            R.style.ChipReviewAlt
                        )
                        chip.setChipDrawable(chipDrawable)
                        chip.text = "Overall ${localContext.getRatingReview(rating)}"
                        chip.isChecked = false
                        chip.isCheckable = false
                        chip.isFocusable = false
                        chip.isClickable = false

                        // we set the color in code as it cant be set in style
                        chip.setTextColor(context.colorFromAttribute(R.attr.primaryGrayBackground))
                        addView(chip)
                    }

                    card.ratings?.forEach { (a, b) ->
                        val chip = Chip(context)
                        val chipDrawable = ChipDrawable.createFromAttributes(
                            context,
                            null,
                            0,
                            R.style.ChipReview
                        )
                        chip.setChipDrawable(chipDrawable)
                        chip.text = "$b ${localContext.getRatingReview(a)}"
                        chip.isChecked = false
                        chip.isCheckable = false
                        chip.isFocusable = false
                        chip.isClickable = false

                        // we set the color in code as it cant be set in style
                        chip.setTextColor(context.colorFromAttribute(R.attr.textColor))
                        addView(chip)
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<UserReview>() {
        override fun areItemsTheSame(oldItem: UserReview, newItem: UserReview): Boolean =
            oldItem == newItem

        override fun areContentsTheSame(oldItem: UserReview, newItem: UserReview): Boolean =
            oldItem == newItem
    }
}