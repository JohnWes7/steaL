package io.github.johnwes7.steal.shared.models.app

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * 给界面的加载失败：把网络层的各种错误归一成四种情况。
 * A load failure for the UI: normalises the network layer's many errors into four cases.
 *
 * 它从不被抛出，只是 ViewModel 交给界面的值；也只表示失败，「查了没有」不放进来（Service 返回 `null` 或空列表）。
 * 标题、说明和图标不在这里，由各平台按情况取：Android 的文字要放 `strings.xml` 才能本地化。
 * It is never thrown, only a value a view model hands to the UI; it means failure only, and "looked it up,
 * found nothing" stays out (services return `null` or an empty list). Titles, messages and icons live on each
 * platform, chosen per case: Android text has to sit in `strings.xml` to be localised.
 */
sealed interface LoadError {

    /**
     * 没有网络，或连不上主机。
     * No connection, or the host cannot be reached.
     */
    data object Offline : LoadError

    /**
     * 服务器返回了非 2xx 的状态码 [statusCode]。
     * The server answered with the non-2xx status [statusCode].
     */
    data class Server(val statusCode: Int) : LoadError

    /**
     * 响应对不上 DTO。
     * The response does not match the DTOs.
     */
    data object Decoding : LoadError

    /**
     * 其余没归类的失败。
     * Anything left over.
     */
    data object Unknown : LoadError

    companion object {

        /**
         * 把下层抛出的 [error] 归一成一种 [LoadError]。
         * Normalises the [error] the lower layers threw into a [LoadError].
         *
         * 不要把协程的 `CancellationException` 交进来：取消不是失败，调用方要先单独接住它再抛出去，否则会被归成 [Unknown] 显示给用户。
         * 超时排在网络错误前面：三种超时都是 `IOException` 的子类，而超时可能只是服务器慢，不断定成离线（同 iOS 的 `.timedOut`）。
         * 其余 `IOException` 一律当离线：公共代码分不出断网和证书错误这类少见情况，界面上两种都给重试，所以不细分。
         * Never pass a coroutine `CancellationException` in: cancelling is not a failure, so callers catch and rethrow
         * it first, or it would show up as [Unknown]. Timeouts come before network errors: all three timeout types
         * subclass `IOException`, and a timeout may just be a slow server, so it is not called offline (as with
         * iOS's `.timedOut`). Every other `IOException` counts as offline: common code cannot tell a lost connection
         * from rare cases such as certificate errors, and the UI offers a retry for both, so they are not split.
         */
        fun from(error: Throwable): LoadError {
            val loadError = when (error) {
                // 超时 / Timeouts
                is HttpRequestTimeoutException -> Unknown
                is ConnectTimeoutException -> Unknown
                is SocketTimeoutException -> Unknown

                // 解码失败：Ktor 把 kotlinx.serialization 的错误包成 JsonConvertException；
                // 自己调 Json 解码时是 SerializationException；响应不是 JSON 时是 NoTransformationFoundException。
                // Decoding failures: Ktor wraps kotlinx.serialization errors in JsonConvertException;
                // decoding with Json directly throws SerializationException; a non-JSON response throws NoTransformationFoundException.
                is ContentConvertException -> Decoding
                is SerializationException -> Decoding
                is NoTransformationFoundException -> Decoding

                // 其余网络错误：断网、找不到主机、连接被断开 / Other network errors: offline, unknown host, dropped connection
                is IOException -> Offline

                else -> Unknown
            }
            return loadError
        }
    }
}
