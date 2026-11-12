// 改写自 android/nav3-recipes 的 multiplestacks 示例（Apache License 2.0）。
// Adapted from the multiplestacks recipe in android/nav3-recipes (Apache License 2.0).

package io.github.johnwes7.steal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer

/**
 * 记住一个 [NavKey] 状态，旋转屏幕和进程被杀后都能恢复，初始值是 [initialKey]。
 * Remembers a [NavKey] state, starting at [initialKey], that survives configuration changes and process death.
 *
 * 用来记当前选中的 tab；和 `rememberNavBackStack` 一样，只是存的是单个 key。
 * Used for the selected tab; like `rememberNavBackStack`, but for a single key.
 */
@Composable
fun rememberNavKey(initialKey: NavKey): MutableState<NavKey> {
    return rememberSerializable(stateSerializer = NavKeySerializer()) {
        mutableStateOf(initialKey)
    }
}
