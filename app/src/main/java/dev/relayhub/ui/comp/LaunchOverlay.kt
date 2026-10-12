package dev.relayhub.ui.comp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relayhub.R
import kotlinx.coroutines.delay

/**
 * 启动遮罩。
 *
 * 系统启动窗口先把品牌底色和图标显示出来，这里接着做一个淡入 + 轻微放大的入场，
 * 等首页数据读出来后整体淡出，避免「先空列表、再刷出内容」的突兀感。
 */
@Composable
fun LaunchOverlay(
    /** 首页是否已经准备好；准备好后遮罩会自动退场 */
    ready: Boolean,
    /** 退场动画播放完毕 */
    onFinished: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }

    // 入场：首帧之后开始，避免刚创建就播放导致跳帧
    LaunchedEffect(Unit) {
        withFrameNanos { }
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    // 数据就绪后停 300ms 再淡出，加上 200ms 退场，整体约 0.5 秒
    LaunchedEffect(ready) {
        if (!ready || finished) return@LaunchedEffect
        delay(300)
        fade.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        )
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    val p = appear.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(0.86f + 0.14f * p)
                    .alpha(p)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp),
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "中转站管理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .alpha(p)
                    .scale(0.96f + 0.04f * p),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "密钥、余额与备忘，一处归档",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(p),
            )
        }
    }
}
