package me.rhunk.snapenhance.common.util.ktx

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException
import kotlin.coroutines.resumeWithException

suspend inline fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback = object: CompletionHandler, Callback {
            override fun invoke(cause: Throwable?) {
                runCatching { cancel() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response })
            }
        }
        enqueue(callback)
        continuation.invokeOnCancellation(callback)
    }
}
