package com.lagradost.quicknovel.ui

import android.content.Context
import android.util.AttributeSet

class TextImageView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) : super(context)

    internal constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)

    var url : String? = null
}