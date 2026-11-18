package io.github.johnwes7.steal.shared.services

import io.github.johnwes7.steal.shared.config.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 一条 HTTP 日志最多保留的字符数。
 * The most characters one HTTP log message keeps.
 *
 * 响应体可能很大（Steam `appdetails` 上百 KB），Logcat 一条也有长度上限，所以截断，同 iOS 截断响应体。
 * Bodies can be large (Steam `appdetails` runs past 100 KB) and a Logcat entry has a length limit,
 * so messages are cut, as iOS cuts response bodies.
 */
private const val MAX_HTTP_LOG_LENGTH = 4000

/**
 * 全 App 共用的 JSON 编解码配置。
 * The JSON encoding and decoding configuration shared by the whole app.
 *
 * Kotlin 的默认规则比 Swift `Codable` 严：多出来的字段、缺了的可空字段都会报错，所以这里改成和 iOS 一样宽松；
 * 值是 `null` 而属性非空仍然报错（同 iOS），靠 DTO 把可能为 `null` 的字段声明成可空。
 * Kotlin's defaults are stricter than Swift's `Codable`: extra fields and missing nullable fields both fail,
 * so this relaxes them to match iOS; a `null` for a non-null property still fails (as on iOS), and DTOs
 * declare fields that may be `null` as nullable.
 */
internal val appJson: Json = Json {
    // 忽略 DTO 里没写的字段：接口加了字段也不会解码失败。
    // Ignore fields the DTO does not declare, so new fields from the API never break decoding.
    ignoreUnknownKeys = true
    // 可空属性在 JSON 里缺了就是 null；编码时值为 null 的属性不输出。
    // A missing nullable property decodes as null; properties whose value is null are left out when encoding.
    explicitNulls = false
    // 有默认值的属性也编码出去，请求体里写了默认值的字段才不会被漏掉。
    // Encode properties that hold their default value too, so request fields with defaults are not dropped.
    encodeDefaults = true
}

/**
 * 创建全 App 共用的 `HttpClient`。
 * Creates the `HttpClient` the whole app shares.
 *
 * 只建一个，交给所有 Service：它持有连接池和线程，复用比每个 Service 各建一个省资源（Ktor 文档的建议）。
 * 引擎由 `ktor-client-engine-defaults` 按平台选：Android 是 OkHttp，iOS 是 Darwin（即 `NSURLSession`）。
 * Create one and hand it to every service: it holds connection pools and threads, so reuse beats one per
 * service (the Ktor docs' advice). `ktor-client-engine-defaults` picks the engine per platform: OkHttp on
 * Android, Darwin (`NSURLSession`) on iOS.
 *
 * [logsRequests] 为 `true` 时把每个请求与响应的原文打到 [AppLog.http]，调试版传 `true`、正式版传 `false`。
 * When [logsRequests] is `true`, the raw text of every request and response goes to [AppLog.http]; debug builds
 * pass `true` and release builds `false`.
 */
fun createHttpClient(logsRequests: Boolean): HttpClient {
    val client = HttpClient {
        installAppPlugins(logsRequests)
    }
    return client
}

/**
 * 给 client 装上全 App 统一的插件与设置。
 * Installs the app-wide plugins and settings on a client.
 *
 * 和 [createHttpClient] 分开，测试才能把同一套配置装到 Ktor 的假引擎上。
 * Kept apart from [createHttpClient] so tests can put the same configuration on Ktor's mock engine.
 */
internal fun HttpClientConfig<*>.installAppPlugins(logsRequests: Boolean) {
    // 不让 Ktor 按状态码抛它自己的异常：每个 Service 自己查状态码、抛自己的错误类型。
    // Keep Ktor from throwing its own exceptions by status code: each service checks the status and throws its own error type.
    expectSuccess = false

    // 用 appJson 编解码 JSON 请求体与响应体。
    // Encode and decode JSON bodies with appJson.
    install(ContentNegotiation) {
        json(appJson)
    }

    if (logsRequests) {
        install(Logging) {
            logger = KermitHttpLogger
            level = LogLevel.ALL
            // key 不进日志，同 iOS 把 `ITAD-API-Key` 打成星号。
            // Keep keys out of the logs, as iOS masks `ITAD-API-Key`.
            sanitizeHeader { headerName -> isSecretHeader(headerName) }
        }
    }
}

/**
 * 判断一个请求头是否带秘密，带的话日志里只显示占位符。
 * Tells whether a request header carries a secret, in which case the logs show only a placeholder.
 *
 * 按名字匹配而不是写死 `ITAD-API-Key`，以后的 LLM 请求（`Authorization`）也一样被遮住。
 * Matches by name instead of hard-coding `ITAD-API-Key`, so future LLM requests (`Authorization`) are masked too.
 */
private fun isSecretHeader(headerName: String): Boolean {
    val isAuthorization = headerName.equals(HttpHeaders.Authorization, ignoreCase = true)
    val isKey = headerName.contains("key", ignoreCase = true)
    return isAuthorization || isKey
}

/**
 * 把 Ktor 日志插件的输出转给 Kermit。
 * Forwards the Ktor logging plugin's output to Kermit.
 */
private object KermitHttpLogger : Logger {

    override fun log(message: String) {
        val truncated = message.take(MAX_HTTP_LOG_LENGTH)
        AppLog.http.d { truncated }
    }
}
