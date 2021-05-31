package com.lagradost.quicknovel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.MenuRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import com.lagradost.quicknovel.MainActivity.Companion.getResourceColor

object UIHelper {
    fun Context.dimensionFromAttribute(attribute: Int): Int {
        val attributes = obtainStyledAttributes(intArrayOf(attribute))
        val dimension = attributes.getDimensionPixelSize(0, 0)
        attributes.recycle()
        return dimension
    }

    fun Context.colorFromAttribute(attribute: Int): Int {
        val attributes = obtainStyledAttributes(intArrayOf(attribute))
        val color = attributes.getColor(0, 0)
        attributes.recycle()
        return color
    }

    fun Activity.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Activity.fixPaddingStatusbar(v: View) {
        v.setPadding(v.paddingLeft, v.paddingTop + getStatusBarHeight(), v.paddingRight, v.paddingBottom)
    }

    fun Activity.requestAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    /**
     * Shows a popup menu on top of this view.
     *
     * @param menuRes menu items to inflate the menu with.
     * @param initMenu function to execute when the menu after is inflated.
     * @param onMenuItemClick function to execute when a menu item is clicked.
     */
    inline fun View.popupMenu(
        @MenuRes menuRes: Int,
        noinline initMenu: (Menu.() -> Unit)? = null,
        noinline onMenuItemClick: MenuItem.() -> Unit
    ): PopupMenu {
        val popup = PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0)
        popup.menuInflater.inflate(menuRes, popup.menu)

        if (initMenu != null) {
            popup.menu.initMenu()
        }
        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }


    /**
     * Shows a popup menu on top of this view.
     *
     * @param items menu item names to inflate the menu with. List of itemId to stringRes pairs.
     * @param selectedItemId optionally show a checkmark beside an item with this itemId.
     * @param onMenuItemClick function to execute when a menu item is clicked.
     */
    @SuppressLint("RestrictedApi")
    inline fun View.popupMenu(
        items: List<Pair<Int, Int>>,
        selectedItemId: Int? = null,
        noinline onMenuItemClick: MenuItem.() -> Unit
    ): PopupMenu {
        val popup = PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, R.style.AppTheme_Toolbar)
        items.forEach { (id, stringRes) ->
            popup.menu.add(0, id, 0, stringRes)
        }

        if (selectedItemId != null) {
            (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

            val emptyIcon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24)
            popup.menu.forEach { item ->
                item.icon = when (item.itemId) {
                    selectedItemId -> ContextCompat.getDrawable(context, R.drawable.ic_check_24)?.mutate()?.apply {
                        setTint(context.getResourceColor(android.R.attr.textColorPrimary))
                    }
                    else -> emptyIcon
                }
            }
        }

        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }
}