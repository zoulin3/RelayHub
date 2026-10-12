package dev.eta.relayhub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "relay_hub_prefs")

/** 主题模式 */
enum class ThemeMode { System, Light, Dark }

/** 配色来源 */
enum class ColorSource { Dynamic, Custom }

/** Material 3 配色风格 */
enum class SchemeStyle { TonalSpot, Vibrant, Expressive, Neutral, Monochrome, Fidelity }

/** 强调色预设 */
enum class AccentPreset(val label: String, val seed: Long) {
    Indigo("靛蓝", 0xFF4C5EDE),
    Violet("紫罗兰", 0xFF7C4DFF),
    Ocean("海蓝", 0xFF0061A4),
    Teal("青碧", 0xFF00696D),
    Forest("森绿", 0xFF2E6C3F),
    Amber("琥珀", 0xFF8A5300),
    Rose("玫红", 0xFFB3204B),
    Slate("石墨", 0xFF4A5A6A),
}

data class ThemePrefs(
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Dynamic,
    val accent: AccentPreset = AccentPreset.Indigo,
    val style: SchemeStyle = SchemeStyle.TonalSpot,
    val pureBlack: Boolean = false,
)

class PrefsRepo(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val colorSource = stringPreferencesKey("color_source")
        val accent = stringPreferencesKey("accent")
        val style = stringPreferencesKey("scheme_style")
        val pureBlack = booleanPreferencesKey("pure_black")
        val maskKeys = booleanPreferencesKey("mask_keys")
        val autoClear = booleanPreferencesKey("auto_clear_clipboard")
    }

    val theme: Flow<ThemePrefs> = context.dataStore.data.map { p ->
        ThemePrefs(
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            colorSource = p[Keys.colorSource]?.let { runCatching { ColorSource.valueOf(it) }.getOrNull() }
                ?: ColorSource.Dynamic,
            accent = p[Keys.accent]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
                ?: AccentPreset.Indigo,
            style = p[Keys.style]?.let { runCatching { SchemeStyle.valueOf(it) }.getOrNull() }
                ?: SchemeStyle.TonalSpot,
            pureBlack = p[Keys.pureBlack] ?: false,
        )
    }

    /** 列表中是否对密钥打码 */
    val maskKeys: Flow<Boolean> = context.dataStore.data.map { it[Keys.maskKeys] ?: true }

    /** 复制密钥后是否自动清除剪贴板 */
    val autoClearClipboard: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoClear] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setColorSource(src: ColorSource) = context.dataStore.edit { it[Keys.colorSource] = src.name }
    suspend fun setAccent(accent: AccentPreset) = context.dataStore.edit { it[Keys.accent] = accent.name }
    suspend fun setStyle(style: SchemeStyle) = context.dataStore.edit { it[Keys.style] = style.name }
    suspend fun setPureBlack(on: Boolean) = context.dataStore.edit { it[Keys.pureBlack] = on }
    suspend fun setMaskKeys(on: Boolean) = context.dataStore.edit { it[Keys.maskKeys] = on }
    suspend fun setAutoClearClipboard(on: Boolean) = context.dataStore.edit { it[Keys.autoClear] = on }
}
