package dev.relayhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relayhub.data.AccentPreset
import dev.relayhub.data.ColorSource
import dev.relayhub.data.SchemeStyle
import dev.relayhub.data.ThemeMode
import dev.relayhub.data.ThemePrefs
import dev.relayhub.ui.comp.BottomSheetSurface
import dev.relayhub.ui.comp.SectionLabel
import dev.relayhub.ui.comp.SheetPad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: ThemePrefs,
    maskKeys: Boolean,
    autoClearClipboard: Boolean,
    onDismiss: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onColorSource: (ColorSource) -> Unit,
    onAccent: (AccentPreset) -> Unit,
    onStyle: (SchemeStyle) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onMaskKeys: (Boolean) -> Unit,
    onAutoClearClipboard: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onImportFile: () -> Unit,
    onImport: () -> Unit,
) {
    BottomSheetSurface(onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = SheetPad,
        ) {
            item { SheetHeader() }
            item { Spacer(Modifier.height(20.dp)) }

            item {
                SectionLabel("深浅模式")
                ThemeModeRow(prefs.themeMode, onThemeMode)
            }

            item { Spacer(Modifier.height(20.dp)) }

            item {
                SectionLabel("配色来源")
                ColorSourceRow(prefs.colorSource, onColorSource)
            }

            if (prefs.colorSource == ColorSource.Dynamic) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "使用系统壁纸生成的 Material You 色板，Android 12 及以上可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(18.dp))
                    AccentPicker(prefs.accent, onAccent)
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("配色风格")
                    StyleChips(prefs.style, onStyle)
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            item {
                SectionLabel("安全")
                SwitchRow(
                    icon = Icons.Outlined.InvertColors,
                    title = "列表隐藏密钥",
                    desc = "默认打码显示，点卡片上的眼睛可临时查看",
                    checked = maskKeys,
                    onCheckedChange = onMaskKeys,
                )
                SwitchRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = "复制后自动清除剪贴板",
                    desc = "复制密钥 60 秒后自动擦除，避免被其他应用读取",
                    checked = autoClearClipboard,
                    onCheckedChange = onAutoClearClipboard,
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                SectionLabel("备份与导入")
                Text(
                    "备份文件保存在 Download/RelayHub，可以直接从那里选文件导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出备份")
                    }
                    FilledTonalButton(onClick = onImportFile, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导入文件")
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从剪贴板导入")
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            item {
                SectionLabel("其他")
                SwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "纯黑背景",
                    desc = "深色模式下使用 AMOLED 纯黑，更省电",
                    checked = prefs.pureBlack,
                    onCheckedChange = onPureBlack,
                )
            }

            item {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "中转站管理 1.0.0 · 数据仅保存在本机 Room 数据库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text("外观与设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val modes = listOf(
            ThemeMode.System to "跟随系统",
            ThemeMode.Light to "浅色",
            ThemeMode.Dark to "深色",
        )
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onPick(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSourceRow(current: ColorSource, onPick: (ColorSource) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val sources = listOf(
            ColorSource.Dynamic to "动态取色 Monet",
            ColorSource.Custom to "自定义强调色",
        )
        sources.forEachIndexed { index, (src, label) ->
            SegmentedButton(
                selected = current == src,
                onClick = { onPick(src) },
                shape = SegmentedButtonDefaults.itemShape(index, sources.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: AccentPreset, onPick: (AccentPreset) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "强调色",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccentPreset.entries.forEach { preset ->
            val selected = current == preset
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(preset.seed.toInt()))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onPick(preset) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleChips(current: SchemeStyle, onPick: (SchemeStyle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SchemeStyle.entries.forEach { style ->
            FilterChip(
                selected = current == style,
                onClick = { onPick(style) },
                label = { Text(style.displayName) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

val SchemeStyle.displayName: String
    get() = when (this) {
        SchemeStyle.TonalSpot -> "柔和"
        SchemeStyle.Vibrant -> "鲜艳"
        SchemeStyle.Expressive -> "表现"
        SchemeStyle.Neutral -> "中性"
        SchemeStyle.Monochrome -> "单色"
        SchemeStyle.Fidelity -> "原色"
    }
