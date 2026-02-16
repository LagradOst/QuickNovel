package com.lagradost.quicknovel.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.TransactionTooLargeException
import android.text.Spanned
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import coil3.dispose
import coil3.request.transformations
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.ImageLayoutBinding
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.UiText
import com.lagradost.quicknovel.ui.txt
import io.noties.markwon.image.AsyncDrawable
import java.io.File
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.core.graphics.drawable.toDrawable

//import androidx.palette.graphics.Palette


val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

fun Int.divCeil(other: Int): Int {
    return this.floorDiv(other) + this.rem(other).sign.absoluteValue
}

fun Long.divCeil(other: Long): Long {
    return this.floorDiv(other) + this.rem(other).sign.absoluteValue
}

object UIHelper {
    fun String?.html(): Spanned {
        return getHtmlText(this?.trim()?.replace("\n", "<br>") ?: return "".toSpanned())
    }

    private fun getHtmlText(text: String): Spanned {
        return try {
            // I have no idea if this can throw any error, but I don't want to try
            HtmlCompat.fromHtml(
                text, HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            logError(e)
            text.toSpanned()
        }
    }

    fun clipboardHelper(label: UiText, text: CharSequence) {
        val ctx = context ?: return
        try {
            ctx.let {
                val clip = ClipData.newPlainText(label.asString(ctx), text)
                val labelSuffix = txt(R.string.toast_copied).asString(ctx)
                ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    showToast("${label.asString(ctx)} $labelSuffix")
                }
            }
        } catch (t: Throwable) {
            Log.e("ClipboardService", "$t")
            when (t) {
                is SecurityException -> {
                    showToast(R.string.clipboard_permission_error)
                }

                is TransactionTooLargeException -> {
                    showToast(R.string.clipboard_too_large)
                }

                else -> {
                    showToast(R.string.clipboard_unknown_error, LENGTH_LONG)
                }
            }
        }
    }

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

    fun Dialog?.dismissSafe(activity: Activity?) {
        if (this?.isShowing == true && activity?.isFinishing == false) {
            this.dismiss()
        }
    }

    fun bindImage(imageView: ImageView, img: AsyncDrawable) {
        val url = img.destination
        img.result?.let { drawable ->
            imageView.setImageDrawable(drawable)
        } ?: kotlin.run {
            imageView.setImage(url)
        }
    }


    fun showImageDialog(context: Context, apply: (ImageView) -> Unit) {
        val settingsDialog = Dialog(context, R.style.AlertDialogCustomTransparentFullscreen)
        //settingsDialog.window?.apply {
        //    requestFeature(Window.FEATURE_NO_TITLE)
        //}
        val binding = ImageLayoutBinding.inflate(LayoutInflater.from(context))
        apply(binding.image)

        binding.image.setOnClickListener {
            settingsDialog.dismissSafe(CommonActivity.activity)
        }
        settingsDialog.setContentView(
            binding.root
        )

        settingsDialog.show()
    }

    fun showImage(context: Context?, image: UiImage) {
        if (context == null) return
        showImageDialog(context) {
            it.setImage(image)
        }
    }

    fun showImage(context: Context?, drawable: AsyncDrawable) {
        if (context == null) return
        showImageDialog(context) {
            bindImage(it, drawable)
        }
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

    fun ImageView?.setImage(
        url: String?,
        headers: Map<String, String>? = null,
        @DrawableRes
        errorImageDrawable: Int? = null,
        fadeIn: Boolean = true,
        radius: Int = 0,
        sample: Int = 3,
        //colorCallback: ((Palette) -> Unit)? = null
    ): Boolean {
        if (url.isNullOrBlank()) return false
        this.setImage(
            UiImage.Image(url, headers, errorImageDrawable),
            errorImageDrawable,
            fadeIn,
            radius,
            sample,
            //colorCallback
        )
        return true
    }

    fun ImageView?.setImage(
        uiImage: UiImage?,
        @DrawableRes
        errorImageDrawable: Int? = null,
        fadeIn: Boolean = true,
        radius: Int = 0,
        sample: Int = 3,
        skipCache: Boolean = true,

        //colorCallback: ((Palette) -> Unit)? = null,
    ): Boolean {
        if (this == null || uiImage == null) {
            this?.dispose()
            return false
        }
        val transformations = if (radius > 0) listOf(
            BlurTransformation(
                scale = sample.toFloat(),
                radius = radius
            )
        ) else emptyList()

        when (uiImage) {
            is UiImage.Image -> {
                this.loadImage(uiImage.url, uiImage.headers) {
                    transformations(transformations)
                }
            }

            is UiImage.Bitmap -> {
                this.loadImage(uiImage.bitmap) {
                    transformations(transformations)
                }
            }

            is UiImage.Drawable -> {
                this.loadImage(uiImage.resId) {
                    transformations(transformations)
                }
            }
        }
        return true
        /*val glideImage=
            (uiImage as? UiImage.Drawable)?.resId ?: (uiImage as? UiImage.Bitmap)?.bitmap
            ?: (uiImage as? UiImage.Image)?.let { image ->
                val glideHeaders = LazyHeaders.Builder().apply {
                    image.headers?.forEach {
                        addHeader(it.key, it.value)
                    }
                }.build()

                GlideUrl(image.url, glideHeaders)
            } ?: return false

        return try {
            var builder = GlideApp.with(this)
                .load(glideImage)
                .skipMemoryCache(skipCache)
                .diskCacheStrategy(DiskCacheStrategy.ALL).let { req ->
                    if (fadeIn)
                        req.transition(DrawableTransitionOptions.withCrossFade())
                    else req
                }

            if (radius > 0) {
                builder = builder.apply(bitmapTransform(BlurTransformation(radius, sample)))
            }

            /*if (colorCallback != null) {
                builder = builder.listener(object : RequestListener<Drawable> {
                    @SuppressLint("CheckResult")
                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.toBitmapOrNull()
                            ?.let { bitmap ->
                                createPaletteAsync(
                                    identifier,
                                    bitmap,
                                    colorCallback
                                )
                            }
                        return false
                    }

                    @SuppressLint("CheckResult")
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
            }*/

            val res = if (errorImageDrawable != null)
                builder.error(errorImageDrawable).into(this)
            else
                builder.into(this)
            res.clearOnDetach()

            true
        } catch (e: Exception) {
            logError(e)
            false
        }*/
    }

    /*fun ImageView?.setImage(
        url: String?,
        referer: String? = null,
        headers: Map<String, String>? = null,
        @DrawableRes
        errorImageDrawable: Int? = null,
        blur: Boolean = false,
        skipCache: Boolean = true,
        fade: Boolean = true
    ): Boolean {
        if (this == null || url.isNullOrBlank()) return false
        val allHeaders =
            (headers ?: emptyMap()) + (referer?.let { mapOf("referer" to referer) } ?: emptyMap())

        // Using the normal GlideUrl(url) { allHeaders } will refresh the image
        // causing flashing when downloading novels, hence why this is used instead
        val glideHeaders = LazyHeaders.Builder().apply {
            allHeaders.forEach {
                addHeader(it.key, it.value)
            }
        }.build()
        val glideUrl = GlideUrl(url, glideHeaders)

        return try {
            val builder = Glide.with(this)
                .load(glideUrl)
                .let {
                    if (fade)
                        it.transition(
                            DrawableTransitionOptions.withCrossFade()
                        ) else it
                }.let {
                    if (blur)
                        it.apply(bitmapTransform(BlurTransformation(100, 3)))
                    else
                        it
                }
                .skipMemoryCache(skipCache)
                .diskCacheStrategy(DiskCacheStrategy.ALL)

            val res = if (errorImageDrawable != null)
                builder.error(errorImageDrawable).into(this)
            else
                builder.into(this)
            res.clearOnDetach()

            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }*/

    val systemFonts: Array<File> by lazy {
        getAllFonts()
    }

    private fun getAllFonts(): Array<File> {
        return try {
            val path = "/system/fonts"
            val file = File(path)
            file.listFiles() ?: emptyArray()
        } catch (t: Throwable) {
            logError(t)
            emptyArray()
        }
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
        v.setPadding(
            v.paddingLeft,
            v.paddingTop + getStatusBarHeight(),
            v.paddingRight,
            v.paddingBottom
        )
    }

    fun Context.requestAudioFocus(focusRequest: AudioFocusRequest?) {
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

    fun parseFontFileName(name: String?): String {
        return (if (name.isNullOrEmpty()) "Default" else name)
            .replace('-', ' ')
            .replace(".ttf", "")
            .replace(".ttc", "")
            .replace(".otf", "")
            .replace(".otc", "")
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
        val popup = PopupMenu(
            context,
            this,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0
        )
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
        val ctw = ContextThemeWrapper(context, R.style.PopupMenu)
        val popup = PopupMenu(
            ctw,
            this,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0
        )

        items.forEach { (id, stringRes) ->
            popup.menu.add(0, id, 0, stringRes)
        }

        if (selectedItemId != null) {
            (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

            val emptyIcon = ContextCompat.getDrawable(context, R.drawable.ic_blank_24)
            popup.menu.forEach { item ->
                item.icon = when (item.itemId) {
                    selectedItemId -> ContextCompat.getDrawable(context, R.drawable.ic_check_24)
                        ?.mutate()?.apply {
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
        val ctw = ContextThemeWrapper(context, R.style.PopupMenu)
        val popup = PopupMenu(
            ctw,
            this,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0
        )

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

    fun Fragment.hideKeyboard() {
        view.let {
            if (it != null) {
                hideKeyboard(it)
            }
        }
    }

    fun hideKeyboard(view: View) {
        val context = view.context ?: return
        val inputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}