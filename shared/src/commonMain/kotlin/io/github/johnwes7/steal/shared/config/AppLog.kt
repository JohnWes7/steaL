package io.github.johnwes7.steal.shared.config

import co.touchlab.kermit.Logger

/**
 * 各模块的日志分类。
 * Log categories for each part of the app.
 *
 * 每个分类是一个带固定 tag 的 Kermit [Logger]：Android 写到 Logcat，iOS 写到系统日志，都能按 tag 筛选。
 * 失败用 `e`，请求与响应这类调试输出用 `d`。
 * Each category is a Kermit [Logger] with a fixed tag: Android writes to Logcat and iOS to the system log,
 * and both can be filtered by tag. Failures use `e`; debugging output such as requests and responses uses `d`.
 */
object AppLog {

    /**
     * Steam 请求。
     * Steam requests.
     */
    val steam: Logger = Logger.withTag("Steam")

    /**
     * ITAD 请求与解码。
     * ITAD requests and decoding.
     */
    val itad: Logger = Logger.withTag("ITAD")

    /**
     * 所有请求与响应的原文（只在调试版打开）。
     * The raw text of every request and response (debug builds only).
     *
     * 各 Service 共用一个 `HttpClient`，它的日志插件分不出请求属于哪个接口，所以单独一个分类；
     * 接口自己的失败仍记在 `steam` / `itad`。
     * Every service shares one `HttpClient`, whose logging plugin cannot tell which API a request belongs to,
     * so it gets its own category; each API's own failures still go to `steam` / `itad`.
     */
    val http: Logger = Logger.withTag("HTTP")
}
