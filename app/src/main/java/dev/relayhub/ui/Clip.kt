package dev.relayhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * 剪贴板读写。Android 10+ 出于隐私限制，只有当前获得焦点的应用才能读剪贴板，
 * 因此这里统一走「焦点应用」路径读取，并把失败原因显式返回，方便界面提示用户。
 */
object Clip {

    data class ReadResult(val text: String?, val error: String?) {
        val ok: Boolean get() = !text.isNullOrBlank()
    }

    fun read(context: Context): ReadResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ReadResult(null, "系统剪贴板不可用")
        val clip = runCatching { cm.primaryClip }.getOrNull()
            ?: return ReadResult(null, "剪贴板为空或未授权读取")
        if (clip.itemCount == 0) return ReadResult(null, "剪贴板为空")
        val text = runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
        if (text.isNullOrBlank()) return ReadResult(null, "剪贴板内容不是文本")
        return ReadResult(text.trim(), null)
    }

    fun write(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 标记为敏感内容，避免系统在剪贴板预览中直接展示密钥
            runCatching { clip.description.extras = PersistableBundle() }
        }
        runCatching { cm.setPrimaryClip(clip) }
    }

    fun clear(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

/** 从任意文本中识别出的中转站线索 */
data class ClipboardGuess(
    val url: String? = null,
    val apiKey: String? = null,
    val name: String? = null,
) {
    val isEmpty: Boolean get() = url == null && apiKey == null && name == null
}

/**
 * 解析剪贴板文本，把 https:// 开头的认成接口地址，sk- 之类的认成 API Key，
 * 其余行若能当名称用就作为名称。
 */
fun parseClipboard(raw: String?): ClipboardGuess {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return ClipboardGuess()

    val lines = text.split('\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    var url: String? = null
    var key: String? = null
    val nameCandidates = mutableListOf<String>()

    for (line in lines) {
        // 「名称: 值」这类结构化写法
        val pair = Regex("""^([^:：=]{1,24})\s*[:：=]\s*(.+)$""").find(line)
        if (pair != null) {
            val label = pair.groupValues[1].trim().lowercase()
            val value = pair.groupValues[2].trim()
            when {
                label.contains("url") || label.contains("地址") || label.contains("接口") -> {
                    if (looksLikeUrl(value)) url = normalizeUrl(value)
                }
                label.contains("key") || label.contains("密钥") || label.contains("token") -> {
                    if (key == null && looksLikeKey(value)) key = value
                }
                label.contains("name") || label.contains("名称") || label.contains("备注") -> {
                    nameCandidates += value
                }
                else -> Unit
            }
            if (url != null || key != null) continue
        }

        when {
            looksLikeUrl(line) -> if (url == null) url = normalizeUrl(line)
            looksLikeKey(line) -> if (key == null) key = extractKey(line)
            else -> nameCandidates += line
        }
    }

    return ClipboardGuess(
        url = url,
        apiKey = key,
        name = nameCandidates.firstOrNull { it.length <= 40 }?.takeIf { url == null },
    )
}

private fun looksLikeUrl(value: String): Boolean {
    val v = value.trim().lowercase()
    return v.startsWith("http://") || v.startsWith("https://") ||
        v.startsWith("www.") ||
        Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+(:[0-9]+)?(/.*)?$""").matches(v)
}

private fun normalizeUrl(value: String): String {
    val v = value.trim()
    val withScheme = when {
        v.startsWith("http://", true) || v.startsWith("https://", true) -> v
        v.startsWith("www.", true) || Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+""", RegexOption.IGNORE_CASE).containsMatchIn(v) ->
            "https://$v"
        else -> v
    }
    return withScheme.trimEnd('/').ifBlank { v }
}

/** sk-、sk_ 前缀，或长随机串，都当作密钥 */
private fun looksLikeKey(value: String): Boolean {
    val v = value.trim()
    if (v.contains(' ') || v.contains('：')) return false
    if (Regex("""^(sk|pk|rk|ak)[-_][A-Za-z0-9\-_]{12,}$""", RegexOption.IGNORE_CASE).matches(v)) return true
    if (Regex("""^[A-Za-z0-9\-_]{24,}$""").matches(v)) return true
    return false
}

private fun extractKey(line: String): String {
    val pair = Regex("""^[^:：=]{1,24}\s*[:：=]\s*(.+)$""").find(line)
    return (pair?.groupValues?.get(1) ?: line).trim().trim('"', '\'')
}
