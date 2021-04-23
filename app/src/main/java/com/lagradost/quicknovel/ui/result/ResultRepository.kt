package com.lagradost.quicknovel.ui.result

import android.os.Handler
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.threadPoolExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.util.concurrent.Flow
import kotlin.coroutines.CoroutineContext

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.concurrent.thread

class ResultRepository {
    fun load(apiName: String, url: String, callback: (LoadResponse?) -> Unit) {
        threadPoolExecutor.execute {
            val data = MainActivity.getApiFromName(apiName).load(url)
            callback(data)
        }
    }

    companion object {
        // Singleton instantiation you already know and love
        @Volatile private var instance: ResultRepository? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: ResultRepository().also { instance = it }
            }
    }
}