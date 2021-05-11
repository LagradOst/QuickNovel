package com.lagradost.quicknovel.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.material.button.MaterialButton
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MyFlowLayout
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.result_review.view.*

class ReviewAdapter(
    context: Context,
    reviewList: ArrayList<UserReview>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = reviewList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ReviewCardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.result_review, parent, false),
            context!!,
            resView!!
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
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val review_author: TextView = itemView.review_author
        val review_body: TextView = itemView.review_body
        val review_time: TextView = itemView.review_time
        val review_image: ImageView = itemView.review_image
        val review_tags: MyFlowLayout = itemView.review_tags
        val review_title: TextView = itemView.review_title

        @SuppressLint("SetTextI18n")
        fun bind(card: UserReview) {
            var reviewText = card.review
            if (reviewText.length > MAX_SYNO_LENGH) {
                reviewText = reviewText.substring(0, MAX_SYNO_LENGH) + "..."
            }
            review_body.setOnClickListener {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
                builder.setMessage(card.review)
                val title = card.reviewTitle ?: card.username
                ?: if (card.rating != null) "Overall ${MainActivity.getRatingReview(card.rating)}" else null
                if (title != null)
                    builder.setTitle(title)
                builder.show()
            }

            review_body.text = reviewText
            review_title.text = card.reviewTitle ?: ""
            review_title.visibility = if (review_title.text == "") View.GONE else View.VISIBLE

            review_time.text = card.reviewDate
            review_author.text = card.username
            println(card.avatarUrl)
            val glideUrl =
                GlideUrl(card.avatarUrl)
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(review_image)
            }

            review_tags.removeAllViews()
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            var index = 0
            if (card.rating != null) {
                val viewBtt = inflater.inflate(R.layout.result_review_tag, null)
                val btt = viewBtt as MaterialButton
                btt.strokeColor = ContextCompat.getColorStateList(context, R.color.colorOngoing)
                btt.setTextColor(ContextCompat.getColor(context, R.color.colorOngoing))
                btt.rippleColor = ContextCompat.getColorStateList(context, R.color.colorOngoing)
                btt.text = "Overall ${MainActivity.getRatingReview(card.rating)}"
                review_tags.addView(viewBtt, index)
                index++
            }

            if (card.ratings != null) {
                for (r in card.ratings) {
                    val viewBtt = inflater.inflate(R.layout.result_review_tag, null)
                    val btt = viewBtt as MaterialButton
                    btt.text = "${r.second} ${MainActivity.getRatingReview(r.first)}"

                    review_tags.addView(viewBtt, index)
                    index++
                }
            }
        }
    }
}
