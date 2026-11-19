package io.github.johnwes7.steal.shared.models.app

import io.github.johnwes7.steal.shared.services.ItadApiException
import io.github.johnwes7.steal.shared.services.SteamApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [LoadError.from] 的归类测试：直接构造各种错误交进去，不发请求。
 * Classification tests for [LoadError.from]: each kind of error is built directly and passed in, without requests.
 */
class LoadErrorTest {

    /**
     * 各 Service 的非 2xx 归成 `Server`，带上状态码。
     * Each service's non-2xx status becomes `Server`, carrying the status code.
     */
    @Test
    fun serviceFailuresAreServer() {
        val itadError = ItadApiException(statusCode = 429, body = "Too Many Requests")
        val steamError = SteamApiException(statusCode = 503, body = "Service Unavailable")

        assertEquals(LoadError.Server(429), LoadError.from(itadError))
        assertEquals(LoadError.Server(503), LoadError.from(steamError))
    }

    /**
     * 三种超时都归成 `Unknown`：超时可能只是服务器慢，不断定成离线，虽然它们都是 `IOException` 的子类。
     * All three timeouts become `Unknown`: a timeout may just be a slow server, so it is not called offline, even
     * though they all subclass `IOException`.
     */
    @Test
    fun timeoutsAreUnknown() {
        val requestTimeout = HttpRequestTimeoutException(url = "https://example.com", timeoutMillis = 1000)
        val connectTimeout = ConnectTimeoutException(message = "connect timeout")
        val socketTimeout = SocketTimeoutException(message = "socket timeout")

        assertEquals(LoadError.Unknown, LoadError.from(requestTimeout))
        assertEquals(LoadError.Unknown, LoadError.from(connectTimeout))
        assertEquals(LoadError.Unknown, LoadError.from(socketTimeout))
    }

    /**
     * 其余网络错误归成 `Offline`。
     * Other network errors become `Offline`.
     */
    @Test
    fun otherIOExceptionsAreOffline() {
        val networkError = IOException("Unable to resolve host")

        assertEquals(LoadError.Offline, LoadError.from(networkError))
    }

    /**
     * Ktor 包过的解码失败和直接用 Json 解码的失败都归成 `Decoding`。
     * Decoding failures wrapped by Ktor and those from decoding with Json directly both become `Decoding`.
     */
    @Test
    fun decodingFailuresAreDecoding() {
        val ktorDecodingError = JsonConvertException(message = "Illegal input")
        val jsonDecodingError = SerializationException("Missing field")

        assertEquals(LoadError.Decoding, LoadError.from(ktorDecodingError))
        assertEquals(LoadError.Decoding, LoadError.from(jsonDecodingError))
    }

    /**
     * 没归类的错误归成 `Unknown`。
     * Anything unclassified becomes `Unknown`.
     */
    @Test
    fun anythingElseIsUnknown() {
        val otherError = IllegalStateException("unexpected")

        assertEquals(LoadError.Unknown, LoadError.from(otherError))
    }
}
