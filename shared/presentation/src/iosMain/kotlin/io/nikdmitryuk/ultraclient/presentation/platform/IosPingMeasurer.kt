package io.nikdmitryuk.ultraclient.presentation.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURL
import platform.Foundation.setHTTPMethod
import platform.Foundation.setTimeoutInterval
import kotlin.coroutines.resume

actual suspend fun measurePingMs(): Long? = suspendCancellableCoroutine { cont ->
    val url = NSURL.URLWithString("https://1.1.1.1/") ?: run { cont.resume(null); return@suspendCancellableCoroutine }
    val request = NSMutableURLRequest.requestWithURL(url).apply {
        setHTTPMethod("HEAD")
        setTimeoutInterval(3.0)
        setCachePolicy(NSURLRequestReloadIgnoringLocalAndRemoteCacheData)
    }
    val start = NSDate.timeIntervalSinceReferenceDate()
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { _, _, _ ->
        val elapsed = ((NSDate.timeIntervalSinceReferenceDate() - start) * 1000).toLong()
        cont.resume(elapsed.takeIf { it > 0 })
    }
    task.resume()
    cont.invokeOnCancellation { task.cancel() }
}
