package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.databinding.ResultReviewBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.SettingsHelper.getRatingReview
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.html
import com.lagradost.quicknovel.util.UIHelper.setImage

class ReviewAdapter :
    NoStateAdapter<UserReview>(BaseDiffCallback(itemSame = { a, b ->
        a.review == b.review
    }, contentSame = { a, b ->
        a == b
    })) {

    companion object {
        val sharedPool =
            RecyclerView.RecycledViewPool().apply {
                this.setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ResultReviewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: UserReview, position: Int) {
        val binding = holder.view as? ResultReviewBinding ?: return
        binding.apply {
            val localContext = this.root.context ?: return

            val expandedText = item.review.replace("\n", "")
            var reviewText = expandedText
            if (reviewText.length > MAX_SYNO_LENGH) {
                reviewText = reviewText.substring(0, MAX_SYNO_LENGH) + "..."
            }
            var isExpanded = false

            reviewBody.setOnClickListener {
                isExpanded = !isExpanded
                reviewBody.text = if (isExpanded) expandedText.html() else reviewText.html()
            }

            reviewBody.text = reviewText.html()
            reviewTitle.text = item.reviewTitle.html()
            reviewTitle.isGone = item.reviewTitle.isNullOrBlank()

            reviewTime.text = item.reviewDate
            reviewAuthor.text = item.username

            reviewImage.setImage(item.avatarUrl)

            reviewTags.apply {
                removeAllViews()

                val context = reviewTags.context

                item.rating?.let { rating ->
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

                item.ratings?.forEach { (a, b) ->
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