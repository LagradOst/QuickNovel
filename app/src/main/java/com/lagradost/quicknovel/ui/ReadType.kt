package com.lagradost.quicknovel.ui

import androidx.annotation.StringRes
import com.lagradost.quicknovel.R

enum class ReadType(val prefValue: Int, @StringRes val stringRes: Int) {
    NONE(0, R.string.type_none),
    PLAN_TO_READ(1, R.string.type_plan_to_read),
    DROPPED(2, R.string.type_dropped),
    COMPLETED(3, R.string.type_completed),
    ON_HOLD(4, R.string.type_on_hold),
    READING(5, R.string.type_reading);

    companion object {
        fun fromSpinner(position: Int?) = values().find { value -> value.prefValue == position } ?: NONE
    }
}