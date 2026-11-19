package io.github.johnwes7.steal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import io.github.johnwes7.steal.views.MainTabView
import io.github.johnwes7.steal.views.theme.SteaLTheme

/**
 * App 唯一的 Activity，只负责提供 [LocalAppContainer]、套上主题、显示主框架。
 * The app's only Activity; it just provides [LocalAppContainer], applies the theme, and shows the main frame.
 *
 * 对应 iOS 的 `steaLApp`。Android 官方架构建议单 Activity，页面切换都在 Compose 的导航里做。
 * The counterpart of iOS `steaLApp`. Android's architecture guide recommends a single Activity, with
 * screen changes handled by Compose navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容画到系统栏下面，由各页面自己处理留白。
        // Draw behind the system bars; screens handle their own insets.
        enableEdgeToEdge()
        // 容器在 SteaLApplication 里，Activity 重建（转屏等）时拿到的还是同一份。
        // The container lives in SteaLApplication, so a recreated Activity (rotation and so on) gets the same one.
        val appContainer = (application as SteaLApplication).appContainer
        setContent {
            // 在根上放一次，所有页面都能读到，同 SwiftUI 在 RootView 上 .environment(...)。
            // Provided once at the root so every screen can read it, like .environment(...) on SwiftUI's RootView.
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                SteaLTheme {
                    MainTabView()
                }
            }
        }
    }
}
