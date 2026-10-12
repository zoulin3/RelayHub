package dev.relayhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relayhub.data.Endpoint
import dev.relayhub.ui.comp.EmptyState
import dev.relayhub.ui.comp.MonoLine
import dev.relayhub.ui.comp.Pill
import dev.relayhub.ui.comp.StatusDot

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EndpointCard(
    modifier: Modifier = Modifier,
    item: Endpoint,
    selected: Boolean,
    selectionMode: Boolean,
    checking: Boolean,
    maskKey: Boolean,
    showKey: Boolean,
    /** 是否展开为详细卡片；默认收起，只显示名称、余额和测速 */
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onCopyKey: () -> Unit,
    onCopyUrl: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenUrl: () -> Unit,
    onToggleKeyVisible: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCheck: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    balancing: Boolean = false,
    onRefreshBalance: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }

    // 常用（置顶）卡片的强调色：在普通底色上掺一层 tertiaryContainer，
    // 比直接换成容器色柔和，也能跟着动态取色一起变。
    val scheme = MaterialTheme.colorScheme
    val favoriteWash = lerp(scheme.surfaceContainerLow, scheme.tertiaryContainer, 0.45f)

    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> scheme.primaryContainer
            item.favorite -> favoriteWash
            else -> scheme.surfaceContainerLow
        },
        animationSpec = tween(220),
        label = "cardContainer",
    )

    // 描边：常用但没被选中时画一圈强调色，星标状态一眼可见
    val outlineColor by animateColorAsState(
        targetValue = if (item.favorite && !selected) {
            scheme.tertiary.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "cardOutline",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, outlineColor, MaterialTheme.shapes.large)
            .combinedClickable(
                // 收起时点一下展开，展开后点一下收起；多选模式下点击即勾选
                onClick = { if (selectionMode) onToggleSelect() else onToggleExpand() },
                onLongClick = onToggleSelect,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            // 常用的卡片稍微抬起一点，多一档层次
            defaultElevation = when {
                selected -> 0.dp
                item.favorite -> 3.dp
                else -> 1.dp
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 14.dp,
                end = 6.dp,
                top = 8.dp,
                bottom = if (expanded) 14.dp else 8.dp,
            ),
        ) {
            // ---- 标题行：收起时右侧只放余额和测速 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.88f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "checkScale",
                    )
                    val checkAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.55f,
                        animationSpec = tween(180),
                        label = "checkAlpha",
                    )
                    Icon(
                        imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(scale)
                            .alpha(checkAlpha),
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Avatar(item.initial)
                    Spacer(Modifier.width(12.dp))
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.favorite) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "常用",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(ok = item.lastCheckOk, checking = checking)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = checkSummary(item, checking),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (expanded) {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        CardMenu(
                            expanded = menu,
                            item = item,
                            onDismiss = { menu = false },
                            onOpen = onOpen,
                            onEdit = onEdit,
                            onCopyKey = onCopyKey,
                            onCopyUrl = onCopyUrl,
                            onCopyAll = onCopyAll,
                            onOpenUrl = onOpenUrl,
                            onRefreshBalance = onRefreshBalance,
                            onCheck = onCheck,
                            onToggleFavorite = onToggleFavorite,
                            onDuplicate = onDuplicate,
                            onDelete = onDelete,
                        )
                    }
                } else if (selectionMode) {
                    // 多选时收起态只留勾选框，右边不放按钮，避免与点选抢手势
                    Spacer(Modifier.width(4.dp))
                } else {
                    // 收起状态：余额 + 测速 + 展开提示
                    val balanceError = item.balanceError.isNotBlank()
                    Surface(
                        onClick = onRefreshBalance,
                        shape = MaterialTheme.shapes.small,
                        color = if (balanceError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = if (balanceError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            if (balancing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.6.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "余额",
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = when {
                                    balancing -> "查询中"
                                    item.balanceError.isNotBlank() -> "查询失败"
                                    item.balanceText.isNotBlank() -> item.balanceText
                                    else -> "查余额"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 92.dp),
                            )
                        }
                    }

                    IconButton(onClick = onCheck, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Bolt,
                            contentDescription = "测速",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "展开详情",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }

            // ---- 收起时不渲染下面的详情，展开后才有 ----
            AnimatedVisibility(visible = expanded) {
                Column {
                    // ---- 地址 ----
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = item.shortUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (item.url.isNotBlank()) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = "打开",
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onOpenUrl)
                                        .padding(5.dp)
                                        .size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---- 密钥 ----
                    if (item.apiKey.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.VpnKey,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (maskKey && !showKey) item.maskedKey else item.apiKey,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (maskKey) {
                                    IconButton(onClick = onToggleKeyVisible, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                IconButton(onClick = onCopyKey, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = "复制密钥",
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ---- 余额 ----
                    if (item.balanceText.isNotBlank() || item.balanceError.isNotBlank() || balancing) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = if (item.balanceError.isNotBlank())
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (item.balanceError.isNotBlank())
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        balancing -> "正在查询余额…"
                                        item.balanceError.isNotBlank() -> item.balanceError
                                        else -> "余额 ${item.balanceText}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = onRefreshBalance, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = "刷新余额",
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ---- 备注 ----
                    if (item.memo.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = item.memo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // ---- 标签 ----
                    if (item.tagList.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            item.tagList.take(3).forEach { tag ->
                                Pill(text = tag)
                            }
                            if (item.tagList.size > 3) {
                                Pill(text = "+${item.tagList.size - 3}")
                            }
                        }
                    }

                    AnimatedVisibility(visible = checking) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 展开后的操作菜单 */
@Composable
private fun CardMenu(
    expanded: Boolean,
    item: Endpoint,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopyKey: () -> Unit,
    onCopyUrl: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenUrl: () -> Unit,
    onRefreshBalance: () -> Unit,
    onCheck: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("详情") },
            leadingIcon = { Icon(Icons.Outlined.Hub, null) },
            onClick = { onDismiss(); onOpen() },
        )
        DropdownMenuItem(
            text = { Text("编辑") },
            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = { onDismiss(); onEdit() },
        )
        DropdownMenuItem(
            text = { Text("复制 Key") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = { onDismiss(); onCopyKey() },
        )
        DropdownMenuItem(
            text = { Text("复制地址") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = { onDismiss(); onCopyUrl() },
        )
        DropdownMenuItem(
            text = { Text("复制完整配置") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = { onDismiss(); onCopyAll() },
        )
        DropdownMenuItem(
            text = { Text("打开面板") },
            leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
            onClick = { onDismiss(); onOpenUrl() },
        )
        DropdownMenuItem(
            text = { Text("查询余额") },
            leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null) },
            onClick = { onDismiss(); onRefreshBalance() },
        )
        DropdownMenuItem(
            text = { Text("测试连通性") },
            leadingIcon = { Icon(Icons.Outlined.Bolt, null) },
            onClick = { onDismiss(); onCheck() },
        )
        DropdownMenuItem(
            text = { Text(if (item.favorite) "取消常用" else "设为常用") },
            leadingIcon = {
                Icon(if (item.favorite) Icons.Outlined.StarOutline else Icons.Outlined.StarBorder, null)
            },
            onClick = { onDismiss(); onToggleFavorite() },
        )
        DropdownMenuItem(
            text = { Text("创建副本") },
            leadingIcon = { Icon(Icons.Outlined.Hub, null) },
            onClick = { onDismiss(); onDuplicate() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

/** 名称首字母圆角角标 */
@Composable
private fun Avatar(initial: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** 卡片副标题里的测活摘要 */
internal fun checkSummary(item: Endpoint, checking: Boolean): String = when {
    checking -> "正在测试连通性…"
    item.lastCheckAt <= 0L -> "尚未测活"
    item.lastCheckOk == true -> "可达 · ${item.lastCheckDetail}".trim(' ', '·')
    item.lastCheckOk == false -> "不可达 · ${item.lastCheckDetail}".trim(' ', '·')
    else -> "尚未测活"
}

@Composable
fun ListLoadingPlaceholder() {
    // 数据库首帧之前的占位：避免列表先显示「还没有中转站」再突然刷出数据
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "正在读取…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EndpointListEmpty(isSearching: Boolean, onCreate: () -> Unit) {
    if (isSearching) {
        EmptyState(
            icon = Icons.Outlined.Hub,
            title = "没有匹配的结果",
            desc = "换个关键词试试，或者检查拼写。",
        )
    } else {
        EmptyState(
            icon = Icons.Outlined.Hub,
            title = "还没有中转站",
            desc = "把常用的第三方 API 中转站存进来，名称、地址、密钥和备注都会留在本机。",
            action = {
                Surface(
                    onClick = onCreate,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        "添加第一个",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            },
        )
    }
}
