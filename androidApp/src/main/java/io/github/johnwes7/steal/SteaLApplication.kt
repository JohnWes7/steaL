package io.github.johnwes7.steal

import android.app.Application
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * 让 Compose 界面读到全 App 的 [AppContainer]，在 [MainActivity] 根上提供一次。
 * Lets Compose screens read the app's [AppContainer]; provided once at the root in [MainActivity].
 *
 * 相当于 SwiftUI 的 `.environment`：祖先放一次，后代不用传参数就能读。只在页面函数体里读（用来取 ViewModel），
 * 往下的组件只接状态与回调。容器整个 App 只有一份、从不更换，所以用 `staticCompositionLocalOf`
 * （读取不被追踪；值一换，提供者里的整块内容都要重组，但容器从不换，这个代价不会发生）；
 * 不给默认值，忘了提供就直接报错，第一次启动就会发现。
 * The counterpart of SwiftUI's `.environment`: an ancestor provides it once and descendants read it without a
 * parameter. Only screen functions read it, to get their view model; components below take state and callbacks.
 * There is one container for the whole app and it never changes, so `staticCompositionLocalOf` is used (reads
 * are not tracked; changing the value would recompose the provider's whole content, but the container never
 * changes, so that cost never happens). There is no default value: forgetting to provide it fails right away,
 * on first launch.
 */
val LocalAppContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("No AppContainer provided") }
