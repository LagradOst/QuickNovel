package com.lagradost.quicknovel

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.lagradost.quicknovel.util.UIHelper.parseFontFileName
import java.io.File

class FontAdapter(val context: Context, private val checked: Int?, private val fonts: ArrayList<File?>) : BaseAdapter() {
    override fun getCount(): Int {
        return fonts.size
    }

    override fun getItem(position: Int): Any {
        return fonts[position] ?: 0
    }

    override fun getItemId(position: Int): Long {
        return fonts[position]?.name?.hashCode()?.toLong() ?: 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view =
            (convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.sort_bottom_single_choice, parent, false)) as TextView

        val font = fonts[position]
        view.text = parseFontFileName(font?.name)
        view.isSelected = position == checked
        if (font != null) {
            view.typeface = Typeface.createFromFile(font)
        }
        return view
    }
}