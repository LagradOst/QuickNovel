package com.lagradost.quicknovel

import android.app.Activity
import android.content.Context
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
import com.lagradost.quicknovel.BaseApplication.Companion.getActivity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.toPx
import java.lang.ref.WeakReference

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
}