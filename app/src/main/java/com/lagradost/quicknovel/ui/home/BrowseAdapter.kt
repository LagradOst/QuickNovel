package com.lagradost.quicknovel.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import kotlinx.android.synthetic.main.browse_list_compact.view.*

class BrowseAdapter(
    val context: Context,
    var cardList: ArrayList<MainAPI>,
    private val resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.browse_list_compact
        return BrowseCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context,
            resView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BrowseCardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return cardList[position].name.hashCode().toLong()
    }

    class BrowseCardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) :
        RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: CardView = itemView.browse_background
        private val browseIconBackground: CardView = itemView.browse_icon_background
        private val browseIcon: ImageView = itemView.browse_icon
        private val browseText: TextView = itemView.browse_text

        fun bind(api: MainAPI) {
            browseText.text = api.name
            val icon = api.iconId
            if (icon != null) {
                browseIcon.setImageResource(icon)
            }

            browseIconBackground.setCardBackgroundColor(ContextCompat.getColor(context, api.iconBackgroundId))

            cardView.setOnClickListener {
                val navController = MainActivity.activity.findNavController(R.id.nav_host_fragment)
                navController.navigate(R.id.navigation_mainpage, Bundle().apply {
                    putString("apiName", api.name)
                }, MainActivity.navOptions)

/*
                MainActivity.activity.supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                    .add(R.id.nav_host_fragment, MainPageFragment().newInstance(api.name))
                    .commit()*/
            }
        }
    }
}
