package dev.relayhub.ui.comp

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 底部抽屉式面板。
 *
 * 特点：
 * - 遮罩淡入 + 面板从底部滑入，关闭时对称退场；
 * - 点遮罩空白处关闭；
 * - 顶部拖拽条可下拉关闭，松手按位移和速度决定回弹还是关闭；
 * - 支持系统预测性返回手势，返回时会跟随手指移动面板。
 *
 * 之所以不用 ModalBottomSheet：它的拖拽手势与内部滚动容器争抢嵌套滚动，
 * 上滑列表时会反复抽搐。
 */
@Composable
fun BottomSheetSurface(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // 面板可见进度：0 = 完全收起，1 = 完全展开
    val progress = remember { Animatable(0f) }
    // 手指拖拽的额外偏移（像素，向下为正）
    val dragOffset = remember { Animatable(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    // 偏移基准始终取屏幕高度：面板最高只占 90% 屏高，
    // 用整屏高度做位移量一定能把面板完整推到屏幕之外，
    // 也就不依赖测量结果，首帧不会出现跳动。
    val screenHeightPx = remember(configuration) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val effectiveHeight = if (panelHeightPx > 0f) panelHeightPx else screenHeightPx
    val hideOffsetPx = maxOf(screenHeightPx, panelHeightPx)

    // 等首帧布局完成再播放动画，保证面板从屏幕外滑入
    LaunchedEffect(Unit) {
        withFrameNanos { }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.92f,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    /** 播放退场动画后真正移除 */
    fun dismiss() {
        scope.launch {
            val remaining = hideOffsetPx.coerceAtLeast(1f)
            // 已经拖下一部分时，剩余的收起距离更短，动画也更快
            val fraction = (1f - (dragOffset.value / remaining)).coerceIn(0f, 1f)
            progress.animateTo(
                targetValue = 0f,
                // 已经拖下一部分时，剩余距离更短，动画也按比例更快
                animationSpec = tween(
                    durationMillis = (200 * fraction).roundToInt().coerceAtLeast(110),
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
            onDismiss()
        }
    }

    // 预测性返回：拖动过程实时跟手，取消则回弹
    PredictiveBackHandler(enabled = true) { backEvents ->
        var completed = false
        try {
            backEvents.collect { event ->
                val p = event.progress.coerceIn(0f, 1f)
                dragOffset.snapTo(p * effectiveHeight)
                progress.snapTo((1f - p * 0.25f).coerceIn(0f, 1f))
            }
            completed = true
        } finally {
            if (completed) {
                onDismiss()
            } else {
                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                progress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }

    val hiddenOffset = (1f - progress.value) * hideOffsetPx
    val totalOffset = hiddenOffset + dragOffset.value
    val scrimAlpha = (progress.value * 0.55f).coerceIn(0f, 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // 遮罩：点击空白关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { dismiss() }
                },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .offset { IntOffset(0, totalOffset.roundToInt()) }
                .onSizeChanged { panelHeightPx = it.height.toFloat() }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .navigationBarsPadding(),
            ) {
                // 顶部拖拽区：下拉关闭
                val draggableState = rememberDraggableState { delta ->
                    scope.launch {
                        dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val threshold = effectiveHeight * 0.28f
                                if (dragOffset.value > threshold || velocity > 1200f) {
                                    dismiss()
                                } else {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.85f,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            },
                        ),
                ) {
                    DragHandle()
                }
                content()
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** 面板标题行，可带副标题和右侧操作 */
@Composable
fun SheetTitleRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 面板内容内边距 */
val SheetPad = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 28.dp)
