package rj.qmce.lite.ui.wear

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScreenScaffoldDefaults
import androidx.wear.compose.material3.ScrollIndicator
import rj.qmce.lite.ui.theme.LocalQmceAdaptive

/**
 * 统一列表页 Scaffold 封装。
 *
 * 与 [ScreenScaffold] 的唯一区别：contentPadding **强制**取
 * [LocalQmceAdaptive].screenContentPadding，使"边缘安全区"开关/强度对所有页面生效。
 * 直接使用 [ScreenScaffold] 会走 [ScreenScaffoldDefaults.contentPadding]
 * （固定 5.2% / 10% 百分比，不读设置），导致各页面安全区不统一。
 *
 * 参数签名与 [ScreenScaffold] 兼容，可直接机械替换调用点。
 */
@Composable
fun QmceScreenScaffold(
    scrollState: TransformingLazyColumnState,
    modifier: Modifier = Modifier,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    edgeButtonSpacing: Dp = ScreenScaffoldDefaults.EdgeButtonSpacing,
    timeText: (@Composable () -> Unit)? = null,
    scrollIndicator: (@Composable BoxScope.() -> Unit)? = { ScrollIndicator(scrollState) },
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val contentPadding = LocalQmceAdaptive.current.screenContentPadding
    if (edgeButton != null) {
        ScreenScaffold(
            scrollState = scrollState,
            modifier = modifier,
            contentPadding = contentPadding,
            timeText = timeText,
            scrollIndicator = scrollIndicator,
            edgeButtonSpacing = edgeButtonSpacing,
            edgeButton = edgeButton,
            content = content,
        )
    } else {
        ScreenScaffold(
            scrollState = scrollState,
            modifier = modifier,
            contentPadding = contentPadding,
            timeText = timeText,
            scrollIndicator = scrollIndicator,
            content = content,
        )
    }
}

/** 无列表状态的全屏 Scaffold（如录音页），同样强制 [LocalQmceAdaptive] 安全区。 */
@Composable
fun QmceScreenScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val contentPadding = LocalQmceAdaptive.current.screenContentPadding
    ScreenScaffold(
        modifier = modifier,
        contentPadding = contentPadding,
        content = content,
    )
}
