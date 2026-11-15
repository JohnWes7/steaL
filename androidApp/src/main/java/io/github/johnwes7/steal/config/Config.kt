package io.github.johnwes7.steal.config

import io.github.johnwes7.steal.BuildConfig

/**
 * App 的配置值：构建时注入的第三方 key。
 * The app's configuration values: third-party keys injected at build time.
 *
 * key 来自仓库根的 secrets.properties（不进 git），由 build.gradle.kts 写进 `BuildConfig`。
 * `shared` 里的 Service 不读这里，由创建它的地方把值传进去，这样 iOS 以后也能用同一个 Service。
 * Keys come from secrets.properties in the repository root (kept out of git) and reach `BuildConfig`
 * through build.gradle.kts. Services in `shared` never read this; whoever creates them passes the value
 * in, so iOS can use the same services later.
 */
object Config {

    /**
     * ITAD API key，没配置时是空串。
     * The ITAD API key, or an empty string when it is not configured.
     */
    const val ITAD_API_KEY: String = BuildConfig.ITAD_API_KEY
}
