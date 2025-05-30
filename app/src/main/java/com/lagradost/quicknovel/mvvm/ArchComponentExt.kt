package com.lagradost.quicknovel.mvvm

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.bumptech.glide.load.HttpException
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.ErrorLoadingException
import kotlinx.coroutines.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

inline fun debugException(message: () -> String) {
    if (BuildConfig.DEBUG) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(message: () -> String) {
    if (BuildConfig.DEBUG) {
        logError(DebugException(message.invoke()))
    }
}

inline fun debugAssert(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
        logError(DebugException(message.invoke()))
    }
}

fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { it?.let { t -> action(t) } }
}
fun <T> LifecycleOwner.observeNullable(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { action(it) }
}
inline fun <reified T : Any> some(value: T?): Some<T> {
    return if (value == null) {
        Some.None
    } else {
        Some.Success(value)
    }
}

sealed class Some<out T> {
    data class Success<out T>(val value: T) : Some<T>()
    object None : Some<Nothing>()

    override fun toString(): String {
        return when (this) {
            is None -> "None"
            is Success -> "Some(${value.toString()})"
        }
    }
}

sealed class ResourceSome<out T> {
    data class Success<out T>(val value: T) : ResourceSome<T>()
    object None : ResourceSome<Nothing>()
    data class Loading(val data: Any? = null) : ResourceSome<Nothing>()
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: Any?, //ResponseBody
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    /*try {
        showToast(throwable.stackTraceToString(), Toast.LENGTH_LONG)
    } catch (_ : Throwable) {

    }*/
    Log.d("ApiError", "-------------------------------------------------------------------")
}

fun <T> safe(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

suspend fun <T> safeAsync(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

suspend fun <T> suspendSafeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

fun <T> safeFail(throwable: Throwable): Resource<T> {
    val stackTraceMsg =
        (throwable.localizedMessage ?: "") + "\n\n" + throwable.stackTrace.joinToString(
            separator = "\n"
        ) {
            "${it.fileName} ${it.lineNumber}"
        }
    return Resource.Failure(false, null, null, stackTraceMsg)
}

fun CoroutineScope.launchSafe(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val obj: suspend CoroutineScope.() -> Unit = {
        try {
            block()
        } catch (e: Exception) {
            logError(e)
        }
    }

    return this.launch(context, start, obj)
}

suspend fun <T, V> Resource<T>.map(transform : suspend (T) -> V) : Resource<V> {
    return when(this) {
        is Resource.Failure -> Resource.Failure(this.isNetworkError,this.errorCode,this.errorResponse,this.errorString)
        is Resource.Loading -> Resource.Loading(this.url)
        is Resource.Success -> {
            Resource.Success(transform(this.value))
        }
    }
}

fun <T, V> Resource<T>?.letInner(transform : (T) -> V) : V? {
    return when(this) {
        is Resource.Success -> {
            transform(this.value)
        }
        else -> null
    }
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            logError(throwable)
            when (throwable) {
                is NullPointerException -> {
                    for (line in throwable.stackTrace) {
                        if (line?.fileName?.endsWith("provider.kt", ignoreCase = true) == true) {
                            return@withContext Resource.Failure(
                                false,
                                null,
                                null,
                                "NullPointerException at ${line.fileName} ${line.lineNumber}\nSite might have updated or added Cloudflare/DDOS protection"
                            )
                        }
                    }
                    safeFail(throwable)
                }
                is SocketTimeoutException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        "Connection Timeout\nPlease try again later."
                    )
                }
                is HttpException -> {
                    Resource.Failure(
                        false,
                        throwable.statusCode,
                        null,
                        throwable.message ?: "HttpException"
                    )
                }
                is UnknownHostException -> {
                    Resource.Failure(true, null, null, "Cannot connect to server, try again later.")
                }
                is ErrorLoadingException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        throwable.message ?: "Error loading, try again later."
                    )
                }
                is NotImplementedError -> {
                    Resource.Failure(false, null, null, "This operation is not implemented.")
                }
                is SSLHandshakeException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        (throwable.message ?: "SSLHandshakeException") + "\nTry again later."
                    )
                }
                else -> safeFail(throwable)
            }
        }
    }
}