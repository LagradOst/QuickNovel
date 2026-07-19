package com.lagradost.quicknovel.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

//https://stackoverflow.com/questions/34697828/parallel-operations-on-kotlin-collections
fun <T, R> Iterable<T>.pmap(
    numThreads: Int = maxOf(Runtime.getRuntime().availableProcessors() - 2, 1),
    exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
    transform: (T) -> R,
): List<R> {

    // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault
    val defaultSize = if (this is Collection<*>) this.size else 10
    val destination = Collections.synchronizedList(ArrayList<R>(defaultSize))

    for (item in this) {
        exec.submit { destination.add(transform(item)) }
    }

    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.DAYS)

    return ArrayList<R>(destination)
}


@OptIn(DelicateCoroutinesApi::class)
suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    with(CoroutineScope(currentCoroutineContext())) {
        map { async { f(it) } }.map { it.await() }
    }

/** amap with stronger cancellation guarantee, aka will only return when each job is joined */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <A, B> Collection<A>.cmap(f: suspend (A) -> B): List<B> =
    with(CoroutineScope(currentCoroutineContext())) {
        // 1. Spawn all jobs
        map { async { f(it) } }.map { deferred ->
            // 2. Await all jobs without throwing, and join if canceled
            try {
                Result.success(deferred.await())
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    deferred.join()
                }
                Result.failure(e)
            } catch (t : Throwable) {
                Result.failure(t)
            }
            // 3. Throw if something goes wrong
        }.map { it.getOrThrow() }
    }