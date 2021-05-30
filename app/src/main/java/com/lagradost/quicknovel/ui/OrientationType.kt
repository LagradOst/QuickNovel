package com.lagradost.quicknovel.ui

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lagradost.quicknovel.R

enum class OrientationType(val prefValue: Int, val flag: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int, val flagValue: Int) {
    // TODO Default icon
    DEFAULT(0, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.string.default_rotation_type, R.drawable.ic_baseline_screen_rotation_24, 0x00000000),
    FREE(1, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.string.rotation_free, R.drawable.ic_baseline_screen_rotation_24, 0x00000008),
    PORTRAIT(2, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.string.rotation_portrait, R.drawable.ic_baseline_stay_current_portrait_24, 0x00000010),
    LANDSCAPE(3, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.string.rotation_landscape, R.drawable.ic_baseline_stay_current_landscape_24, 0x00000018),
    LOCKED_PORTRAIT(4, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.string.rotation_force_portrait, R.drawable.ic_baseline_screen_lock_portrait_24, 0x00000020),
    LOCKED_LANDSCAPE(5, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.string.rotation_force_landscape, R.drawable.ic_baseline_screen_lock_landscape_24, 0x00000028);

    companion object {
        const val MASK = 0x00000038

        fun fromPreference(preference: Int?): OrientationType = values().find { it.flagValue == preference } ?: FREE

        fun fromSpinner(position: Int?) = values().find { value -> value.prefValue == position } ?: DEFAULT
    }
}