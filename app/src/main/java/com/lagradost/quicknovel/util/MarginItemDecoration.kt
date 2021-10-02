package com.lagradost.quicknovel.util

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class MarginItemDecoration(
    private val spaceSize: Int,
    private val isHorizontal: Boolean = false,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect, view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        with(outRect) {
            if (isHorizontal) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    left = spaceSize
                }
                right = spaceSize
            } else {
                if (parent.getChildAdapterPosition(view) == 0) {
                    top = spaceSize
                }
                left = spaceSize
                right = spaceSize
                bottom = spaceSize
            }
        }
    }
}