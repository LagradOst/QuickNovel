package com.lagradost.quicknovel.ui

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lagradost.quicknovel.R

enum class ReadingType(val prefValue: Int, @StringRes val stringRes: Int) {
    DEFAULT(0, R.string.default_text),
    INF_SCROLL(1, R.string.inf_scroll),
    BTT_SCROLL(2, R.string.button_scroll),
    OVERSCROLL_SCROLL(3, R.string.overscroll_scroll);

    companion object {
        fun fromSpinner(position: Int?) = values().find { value -> value.prefValue == position } ?: DEFAULT
    }
}