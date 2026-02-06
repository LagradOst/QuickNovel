package com.lagradost.quicknovel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.work.Configuration
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.utils.ImageLoader
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.DataStore.removeKeys
import com.lagradost.quicknovel.DataStore.setKey
import java.lang.ref.WeakReference

class BaseApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider  {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = base
    }

    override fun newImageLoader(context: PlatformContext): coil3.ImageLoader {
        // Coil Module will be initialized & setSafe globally when first loadImage() is invoked
        return ImageLoader.buildImageLoader(applicationContext)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()


    companion object {
        /** Use to get activity from Context */
        tailrec fun Context.getActivity(): Activity? = this as? Activity
            ?: (this as? ContextWrapper)?.baseContext?.getActivity()

        private var _context: WeakReference<Context>? = null
        var context
            get() = _context?.get()
            set(value) {
                _context = WeakReference(value)
            }

        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKeyClass(path: String) {
            context?.removeKey(path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }
    }
}