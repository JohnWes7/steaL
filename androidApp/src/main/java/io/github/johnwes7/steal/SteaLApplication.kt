package io.github.johnwes7.steal

import android.app.Application
import io.github.johnwes7.steal.config.Config
import io.github.johnwes7.steal.shared.di.AppContainer

/**
 * 自定义的 `Application`：持有全 App 唯一的 [AppContainer]。
 * The custom `Application`; it holds the app's single [AppContainer].
 *
 * `Application` 在 App 进程启动时建一次，活得比所有 Activity 都长（转屏、深色模式切换重建 Activity 时它不变），
 * 所以容器放这里，同 Android 官方「手写依赖注入」的写法。对应 iOS `RootView` 用 `@State` 持有 Service。
 * The `Application` is created once when the app process starts and outlives every Activity (it stays when
 * rotation or a dark-mode switch recreates the Activity), so the container lives here, as in Android's
 * "Manual dependency injection" guide. The counterpart of iOS `RootView` holding services in `@State`.
 */
class SteaLApplication : Application() {

    /**
     * 全 App 共用的 Service 与 ViewModel 工厂；调试版打印 HTTP 请求与响应。
     * The services and view model factories the whole app shares; debug builds log HTTP requests and responses.
     */
    val appContainer: AppContainer = AppContainer(
        logsRequests = BuildConfig.DEBUG,
        itadApiKey = Config.ITAD_API_KEY,
    )
}
