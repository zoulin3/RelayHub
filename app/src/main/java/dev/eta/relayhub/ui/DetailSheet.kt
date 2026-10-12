package dev.eta.relayhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.eta.relayhub.data.Endpoint
import dev.eta.relayhub.ui.comp.Pill
import dev.eta.relayhub.ui.comp.BottomSheetSurface
import dev.eta.relayhub.ui.comp.SheetPad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailSheet(
    item: Endpoint,
    maskKey: Boolean,
    checking: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMask: () -> Unit,
    onCopy: (String, String, Boolean) -> Unit,
    onOpenUrl: () -> Unit,
    onCheck: () -> Unit,
    onDuplicate: () -> Unit,
    onCopyAll: () -> Unit,
    onDelete: () -> Unit,
) {
    BottomSheetSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(SheetPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onToggleFavorite,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        imageVector = if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "常用",
                        tint = if (item.favorite) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(9.dp).size(20.dp),
                    )
                }
            }

            if (item.tagList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tagList.forEach { Pill(it) }
                }
            }

            Spacer(Modifier.height(18.dp))

            FieldBlock(
                icon = Icons.Outlined.Link,
                label = "接口地址",
                value = item.url.ifBlank { "未填写" },
                mono = true,
                onCopy = { onCopy("接口地址", item.url, false) },
                onExtra = if (item.url.isNotBlank()) onOpenUrl else null,
            )

            Spacer(Modifier.height(10.dp))

            FieldBlock(
                icon = Icons.Outlined.VpnKey,
                label = "API Key",
                value = when {
                    item.apiKey.isBlank() -> "未填写"
                    maskKey -> item.maskedKey
                    else -> item.apiKey
                },
                mono = true,
                onCopy = { onCopy("API Key", item.apiKey, true) },
                extraLabel = if (maskKey) "显示" else "隐藏",
                onToggleMask = onToggleMask,
            )

            if (item.memo.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "记忆 / 备注",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.memo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            MetaRow("添加时间", formatTime(item.createdAt))
            MetaRow("最近修改", formatTime(item.updatedAt))
            MetaRow(
                "最近测活",
                if (item.lastCheckAt == 0L) "尚未测试"
                else "${formatTime(item.lastCheckAt)} · ${item.lastCheckDetail.ifBlank { "-" }}",
            )
            MetaRow(
                "账户余额",
                when {
                    item.balanceText.isNotBlank() -> item.balanceText
                    item.balanceError.isNotBlank() -> item.balanceError
                    item.hasLoginMaterial -> "尚未查询"
                    else -> "未配置面板账号"
                },
            )

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onCheck,
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (checking) "测试中…" else "测活")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCopyAll,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制配置")
                }
                OutlinedButton(
                    onClick = onDuplicate,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("创建副本")
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
                Text("删除这条记录", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FieldBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    mono: Boolean,
    onCopy: () -> Unit,
    onExtra: (() -> Unit)? = null,
    extraLabel: String = "打开",
    onToggleMask: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onExtra != null) {
                    TextButton(onClick = onExtra) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("打开", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (onToggleMask != null) {
                    TextButton(onClick = onToggleMask) {
                        Text(extraLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

internal fun formatTime(ts: Long): String =
    if (ts <= 0L) "-" else timeFormat.format(Date(ts))
