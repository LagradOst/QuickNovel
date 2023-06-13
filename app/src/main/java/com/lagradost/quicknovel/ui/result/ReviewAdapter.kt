package com.lagradost.quicknovel.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.util.SettingsHelper.getRatingReview
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.setImage
import kotlinx.android.synthetic.main.result_review.view.*

class ReviewAdapter(
    val context: Context,
    var cardList: ArrayList<UserReview>,
    private val resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ReviewCardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.result_review, parent, false),
            context,
            resView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ReviewCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        val data = cardList[position]
        return (data.username ?: data.review + data.reviewDate).hashCode().toLong()
    }


    class ReviewCardViewHolder
    constructor(itemView: View, context: Context, resView: RecyclerView) :
        RecyclerView.ViewHolder(itemView) {
        private val localContext = context
        private val reviewAuthor = itemView.review_author
        private val reviewBody = itemView.review_body
        private val reviewTime = itemView.review_time
        private val reviewImage = itemView.review_image
        private val reviewTags = itemView.review_tags
        private val reviewTitle = itemView.review_title

        @SuppressLint("SetTextI18n")
        fun bind(card: UserReview) {
            var reviewText = card.review
            if (reviewText.length > MAX_SYNO_LENGH) {
                reviewText = reviewText.substring(0, MAX_SYNO_LENGH) + "..."
            }
            reviewBody.setOnClickListener {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this.localContext)
                builder.setMessage(card.review)
                val title = card.reviewTitle ?: card.username
                ?: if (card.rating != null) "Overall ${localContext.getRatingReview(card.rating)}" else null
                if (title != null)
                    builder.setTitle(title)
                builder.show()
            }

            reviewBody.text = reviewText
            reviewTitle.text = card.reviewTitle ?: ""
            reviewTitle.visibility = if (reviewTitle.text == "") View.GONE else View.VISIBLE

            reviewTime.text = card.reviewDate
            reviewAuthor.text = card.username
            println(card.avatarUrl)

            reviewImage.setImage(card.avatarUrl)

            reviewTags.apply {
                removeAllViews()

                val context = reviewTags.context

                card.rating?.let {rating ->
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

                    chip.setTextColor(context.colorFromAttribute(R.attr.textColor))
                    addView(chip)
                }
            }
        }
    }
}
