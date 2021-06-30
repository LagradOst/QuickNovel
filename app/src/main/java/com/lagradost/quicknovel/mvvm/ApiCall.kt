package com.lagradost.quicknovel.mvvm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Loading(val loadingData : Any? = null) : Resource<Nothing>()
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

fun<T> normalSafeApiCall(apiCall : () -> T) : T? {
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
                /*is HttpException -> {
                    Resource.Failure(false, throwable.code(), throwable.response()?.errorBody(), throwable.localizedMessage)
                }
                is SocketTimeoutException -> {
                    Resource.Failure(true,null,null,"Please try again later.")
                }
                is UnknownHostException ->{
                    Resource.Failure(true,null,null,"Cannot connect to server, try again later.")
                }*/
                else -> {
                    Resource.Failure(true, null, null, throwable.localizedMessage)
                }
            }
        }
    }
}