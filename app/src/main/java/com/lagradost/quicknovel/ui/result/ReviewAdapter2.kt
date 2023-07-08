package com.lagradost.quicknovel.ui.result

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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

                var reviewText = card.review
                if (reviewText.length > MAX_SYNO_LENGH) {
                    reviewText = reviewText.substring(0, MAX_SYNO_LENGH) + "..."
                }

                reviewBody.setOnClickListener {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(localContext)
                    builder.setMessage(card.review)
                    val title = card.reviewTitle ?: card.username
                    ?: if (card.rating != null) localContext.getString(R.string.overall_rating_format)
                        .format(localContext.getRatingReview(card.rating)) else null
                    if (title != null)
                        builder.setTitle(title)
                    builder.show()
                }

                reviewBody.text = reviewText
                reviewTitle.text = card.reviewTitle ?: ""
                reviewTitle.visibility = if (reviewTitle.text == "") View.GONE else View.VISIBLE

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