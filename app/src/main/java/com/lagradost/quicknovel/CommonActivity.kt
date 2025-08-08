package com.lagradost.quicknovel

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.ui.UiText
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.toPx
import java.lang.ref.WeakReference
import java.util.Locale

object CommonActivity {
    private var _activity: WeakReference<Activity>? = null
    var activity
        get() = _activity?.get()
        set(value) {
            _activity = WeakReference(value)
        }

    const val TAG = "COMPACT"
    var currentToast: Toast? = null

    fun showToast(@StringRes message: Int, duration: Int? = null) {
        activity?.runOnUiThread { showToast(activity, message, duration) }
    }

    fun showToast(message: String?, duration: Int? = null) {
        activity?.runOnUiThread { showToast(activity, message, duration) }
    }

    fun showToast(message: UiText?, duration: Int? = null) {
        val act = activity ?: return
        if (message == null) return
        act.runOnUiThread {
            showToast(act, message.asString(act), duration)
        }
    }


    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
    fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
        if (act == null) return
        showToast(act, act.getString(message), duration)
    }

    @MainThread
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (act == null || message == null) {
            Log.w(TAG, "invalid showToast act = $act message = $message")
            return
        }
        Log.i(TAG, "showToast = $message")

        try {
            currentToast?.cancel()
        } catch (e: Exception) {
            logError(e)
        }
        try {
            val inflater =
                act.getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val layout: View = inflater.inflate(
                R.layout.toast,
                act.findViewById<View>(R.id.toast_layout_root) as ViewGroup?
            )

            val text = layout.findViewById(R.id.text) as TextView
            text.text = message.trim()

            val toast = Toast(act)
            toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 5.toPx)
            toast.duration = duration ?: Toast.LENGTH_SHORT
            toast.view = layout
            //https://github.com/PureWriter/ToastCompat
            toast.show()
            currentToast = toast
        } catch (e: Exception) {
            logError(e)
        }
    }
    fun init(act: Activity) {
        this.activity = act

        // Just in case????
        if(BaseApplication.context == null)
            BaseApplication.context = act

        act.updateLocale()
    }

    fun setLocale(context: Context?, languageCode: String?) {
        if (context == null || languageCode == null) return
        val locale = Locale(languageCode)
        val resources: Resources = context.resources
        val config = resources.configuration
        Locale.setDefault(locale)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun Context?.updateLocale() {
        if (this == null) return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val localeCode = settingsManager.getString(this.getString(R.string.locale_key), null)
        setLocale(this, localeCode)
    }

    fun loadThemes(act: Activity?) {
        if (act == null) return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(act)

        val currentTheme =
            when (settingsManager.getString(act.getString(R.string.theme_key), "AmoledLight")) {
                "Black" -> R.style.AppTheme
                "Light" -> R.style.LightMode
                "Amoled" -> R.style.AmoledMode
                "AmoledLight" -> R.style.AmoledModeLight
                "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.MonetMode else R.style.AppTheme
                else -> R.style.AppTheme
            }

        val currentOverlayTheme =
            when (settingsManager.getString(act.getString(R.string.primary_color_key), "Normal")) {
                "Normal" -> R.style.OverlayPrimaryColorNormal
                "CarnationPink" -> R.style.OverlayPrimaryColorCarnationPink
                "DarkGreen" -> R.style.OverlayPrimaryColorDarkGreen
                "Maroon" -> R.style.OverlayPrimaryColorMaroon
                "NavyBlue" -> R.style.OverlayPrimaryColorNavyBlue
                "Grey" -> R.style.OverlayPrimaryColorGrey
                "White" -> R.style.OverlayPrimaryColorWhite
                "Brown" -> R.style.OverlayPrimaryColorBrown
                "Purple" -> R.style.OverlayPrimaryColorPurple
                "Green" -> R.style.OverlayPrimaryColorGreen
                "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
                "Red" -> R.style.OverlayPrimaryColorRed
                "Banana" -> R.style.OverlayPrimaryColorBanana
                "Party" -> R.style.OverlayPrimaryColorParty
                "Pink" -> R.style.OverlayPrimaryColorPink
                "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.OverlayPrimaryColorMonet else R.style.OverlayPrimaryColorNormal
                "Monet2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.OverlayPrimaryColorMonetTwo else R.style.OverlayPrimaryColorNormal
                else -> R.style.OverlayPrimaryColorNormal
            }
        act.theme.applyStyle(currentTheme, true)
        act.theme.applyStyle(currentOverlayTheme, true)


        act.theme.applyStyle(
            R.style.LoadedStyle,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW


        act.window?.navigationBarColor =
            act.colorFromAttribute(R.attr.primaryGrayBackground)
    }
}