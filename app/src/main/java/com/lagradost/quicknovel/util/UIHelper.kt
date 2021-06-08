package com.lagradost.quicknovel.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.MenuRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import com.lagradost.quicknovel.R
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.math.roundToInt

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

object UIHelper {
    fun humanReadableByteCountSI(bytes: Int): String {
        if (-1000 < bytes && bytes < 1000) {
            return "$bytes"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        var currentBytes = bytes
        while (currentBytes <= -999950 || currentBytes >= 999950) {
            currentBytes /= 1000
            ci.next()
        }
        return String.format("%.1f%c", currentBytes / 1000.0, ci.current()).replace(',', '.')
    }

    fun FragmentActivity.popCurrentPage() {
        val currentFragment = supportFragmentManager.fragments.lastOrNull {
            it.isVisible
        } ?: return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit
            )
            .remove(currentFragment)
            .commitAllowingStateLoss()
    }

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

    @ColorInt
    fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(resource))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        if (alphaFactor < 1f) {
            val alpha = (color.alpha * alphaFactor).roundToInt()
            return Color.argb(alpha, color.red, color.green, color.blue)
        }

        return color
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
        noinline onMenuItemClick: MenuItem.() -> Unit,
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
        noinline onMenuItemClick: MenuItem.() -> Unit,
    ): PopupMenu {
        val popup =
            PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, R.style.AppTheme_Toolbar)
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

    @SuppressLint("RestrictedApi")
    inline fun View.popupMenu(
        items: List<Triple<Int, Int, Int>>,
        noinline onMenuItemClick: MenuItem.() -> Unit,
    ): PopupMenu {
        val popup =
            PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, R.style.AppTheme_Toolbar)
        items.forEach { (id, icon, stringRes) ->
            popup.menu.add(0, id, 0, stringRes).setIcon(icon)
        }

        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }
}