package com.lagradost.quicknovel

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.browse_list_compact.view.*

class BrowseAdapter(
    val context: Context,
    var cardList: ArrayList<MainAPI>,
    val resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
   // var cardList = animeList
  //  var context: Context? = context
   // var resView: RecyclerView? = resView

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
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView: CardView = itemView.browse_background
        val browse_icon_background: CardView = itemView.browse_icon_background
        val browse_icon: ImageView = itemView.browse_icon
        val browse_text: TextView = itemView.browse_text

        fun bind(api: MainAPI) {
            browse_text.text = api.name
            val icon = api.iconId
            if (icon != null) {
                browse_icon.setImageResource(icon)
            }
            browse_icon_background.setCardBackgroundColor(context.getColor(api.iconBackgroundId))

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
