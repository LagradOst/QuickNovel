package com.lagradost.quicknovel.mvvm

import android.util.Log
import com.bumptech.glide.load.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Loading(val loadingData: Any? = null) : Resource<Nothing>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: Any?, //ResponseBody
        val errorString: String,
    ) : Resource<Nothing>()
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

fun <T> normalSafeApiCall(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
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
                is SocketTimeoutException -> {
                    Resource.Failure(true, null, null, "Please try again later.")
                }
                is HttpException -> {
                    Resource.Failure(false, throwable.statusCode, null, throwable.localizedMessage)
                }
                is UnknownHostException -> {
                    Resource.Failure(true, null, null, "Cannot connect to server, try again later.")
                }
                else -> {
                    val stackTraceMsg = throwable.localizedMessage + "\n\n" + throwable.stackTrace.joinToString(
                        separator = "\n"
                    ) {
                        "${it.fileName} ${it.lineNumber}"
                    }
                    Resource.Failure(false, null, null, stackTraceMsg) //
                }
            }
        }
    }
}