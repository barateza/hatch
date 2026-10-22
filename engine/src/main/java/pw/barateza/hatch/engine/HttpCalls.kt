package pw.barateza.hatch.engine

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `Call.execute()` wrapper that is cancellation aware: cancelling the coroutine cancels the
 * OkHttp call instead of holding on to an I/O thread until a timeout fires.
 */
internal suspend fun OkHttpClient.awaitCall(request: Request): Response =
  suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(
      object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
          if (continuation.isActive) {
            continuation.resume(response)
          } else {
            response.close()
          }
        }
      },
    )
  }
