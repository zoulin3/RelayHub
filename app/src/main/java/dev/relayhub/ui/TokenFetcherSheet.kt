package dev.relayhub.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.relayhub.ui.comp.BottomSheetSurface
import dev.relayhub.ui.comp.SheetPad
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 抓取到的候选项：可能是一段令牌，也可能是一串登录 Cookie */
data class FoundToken(
    val source: String,
    val key: String,
    val value: String,
    /** true 表示这是整串 Cookie，需要按 Cookie 方式保存 */
    val isCookie: Boolean = false,
    /** 旧版 new-api 需要的账户 id（来自 localStorage 的 user 对象） */
    val userId: String = "",
)

/**
 * 内置网页登录，用于自动获取中转站的登录凭证。
 *
 * 流程：在这里打开站点并正常登录 → 点「抓取凭证」→
 * 先由应用原生读取 Cookie（HttpOnly 的也能拿到），
 * 再从 localStorage / sessionStorage / 可见 Cookie 里找像令牌的字段 → 选择填入。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TokenFetcherSheet(
    startUrl: String,
    onDismiss: () -> Unit,
    onToken: (value: String, isCookie: Boolean, userId: String) -> Unit,
) {
    var address by remember { mutableStateOf(startUrl) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var candidates by remember { mutableStateOf<List<FoundToken>>(emptyList()) }
    var hint by remember { mutableStateOf<String?>(null) }
    var scanned by remember { mutableStateOf(false) }

    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }

    /** 原生读取 Cookie：HttpOnly 的 document.cookie 读不到，但应用可以直接查 */
    fun readCookie(target: String): String {
        val cm = CookieManager.getInstance()
        val urls = listOf(target, target.trimEnd('/') + "/", "${target.trimEnd('/')}/api/user/auth")
        val all = LinkedHashSet<String>()
        for (u in urls) {
            val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
            raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { all.add(it) }
        }
        return all.joinToString("; ")
    }

    fun scan() {
        val wv = webView
        if (wv == null) {
            hint = "页面还没准备好"
            return
        }
        hint = null
        wv.evaluateJavascript(SNIFFER_JS) { raw ->
            val sniff = parseSniff(raw, json)
            val jsList = sniff.first
            // 账户 id 由页面脚本单独取出，不受候选条数截断影响
            val userId = sniff.second.ifBlank {
                jsList.firstOrNull { it.key.equals("user", ignoreCase = true) }
                    ?.value
                    ?.let { extractUserId(it) }
                    .orEmpty()
            }

            // 原生 Cookie（可含 HttpOnly）优先展示
            val cookieText = readCookie(currentUrl.ifBlank { address })
            val nativeList = buildList {
                if (cookieText.isNotBlank()) {
                    val hasRefresh = cookieText.contains("new_api_refresh") ||
                        cookieText.contains("session=") ||
                        cookieText.contains("sessionid")
                    add(
                        FoundToken(
                            source = if (hasRefresh) "登录 Cookie（含 HttpOnly）" else "整串 Cookie",
                            key = if (userId.isNotBlank()) "全部 Cookie（含账户 id $userId）" else "全部 Cookie",
                            value = cookieText,
                            isCookie = true,
                            userId = userId,
                        ),
                    )
                    cookieText.split(';').map { it.trim() }.filter { it.contains('=') }
                        .forEach { pair ->
                            val name = pair.substringBefore('=').trim()
                            val value = pair.substringAfter('=', "")
                            if (value.length >= 8) {
                                add(FoundToken("Cookie（原生读取）", name, value))
                            }
                        }
                }
            }

            val merged = (nativeList + jsList).distinctBy { it.value }
            candidates = merged
            scanned = true
            hint = when {
                merged.isEmpty() -> null
                merged.any { it.key.contains("bigmodel_token") } ->
                    "已抓到智谱控制台令牌，点选填入后即可查余额"
                merged.any { it.isCookie && it.userId.isNotBlank() } ->
                    "已抓到登录 Cookie 和账户 id，点第一项填入即可查余额"
                else -> "抓到 ${merged.size} 个候选凭据"
            }
        }
    }

    BottomSheetSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // ---- 标题 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "登录并获取凭据",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "在下方页面正常登录，然后点「抓取凭据」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            // ---- 地址栏 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("站点地址") },
                    shape = MaterialTheme.shapes.large,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = {
                    val url = normalizeAddress(address)
                    webView?.loadUrl(url)
                }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "打开")
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }

            // ---- 网页 ----
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(14.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(14.dp),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadsImagesAutomatically = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = userAgentString
                                    .replace("; wv", "")
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    loading = true
                                    progress = 0f
                                    currentUrl = url ?: currentUrl
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    currentUrl = url ?: currentUrl
                                    address = url ?: address
                                    CookieManager.getInstance().flush()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = false
                            }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    loading = newProgress < 100
                                }
                            }

                            loadUrl(normalizeAddress(startUrl))
                            webView = this
                        }
                    },
                    update = { webView = it },
                )
            }

            // ---- 结果与操作 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (hint != null) {
                    Text(
                        hint!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (candidates.isNotEmpty()) {
                    Text(
                        "找到 ${candidates.size} 个候选，点选填入：",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        candidates.forEach { c ->
                            Surface(
                                onClick = { onToken(c.value, c.isCookie, c.userId) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (c.isCookie) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        if (c.isCookie) Icons.Outlined.Download else Icons.Outlined.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${c.source} · ${c.key}",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            c.value.take(52) + if (c.value.length > 52) "…" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = "使用",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (scanned && hint == null) {
                    Text(
                        "没找到凭据。确认已经登录成功，再试一次；或从未登录状态重新登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = { webView?.goBack() },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "后退", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalButton(
                        onClick = { webView?.goForward() },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = "前进", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalButton(
                        onClick = { scan() },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("抓取凭据")
                    }
                }
            }
        }
    }
}

/** 补全协议 */
private fun normalizeAddress(input: String): String {
    val t = input.trim()
    return when {
        t.isEmpty() -> "https://"
        t.startsWith("http://") || t.startsWith("https://") -> t
        else -> "https://$t"
    }
}

/**
 * 在页面里搜集可疑的令牌字段。
 * 覆盖 localStorage、sessionStorage 与可见 Cookie，并对值里的 JSON 做二次展开。
 * （HttpOnly 的 Cookie 由应用原生读取，脚本看不到。）
 */
private const val SNIFFER_JS = """
(function () {
  var out = [];
  var seen = {};
  function push(source, key, value) {
    if (!value || typeof value !== 'string') return;
    if (value.length < 8) return;
    var id = source + '|' + key + '|' + value;
    if (seen[id]) return;
    seen[id] = 1;
    out.push({ source: source, key: key, value: value });
  }
  function scan(store, name) {
    try {
      if (!store) return;
      for (var i = 0; i < store.length; i++) {
        var k = store.key(i);
        var v = store.getItem(k);
        if (!v) continue;
        push(name, k, v);
        // 值本身是 JSON 时，展开里面的 token 字段
        if (v.charAt(0) === '{' || v.charAt(0) === '[') {
          try {
            var o = JSON.parse(v);
            if (o && typeof o === 'object') {
              for (var p in o) {
                if (!Object.prototype.hasOwnProperty.call(o, p)) continue;
                if (typeof o[p] === 'string') push(name, k + '.' + p, o[p]);
              }
            }
          } catch (e) {}
        }
      }
    } catch (e) {}
  }
  scan(window.localStorage, 'localStorage');
  scan(window.sessionStorage, 'sessionStorage');
  try {
    var ck = document.cookie || '';
    ck.split(';').forEach(function (pair) {
      var idx = pair.indexOf('=');
      if (idx < 0) return;
      var k = pair.substring(0, idx).trim();
      var v = pair.substring(idx + 1).trim();
      push('cookie', k, v);
    });
  } catch (e) {}
  // 旧版 new-api 需要 New-Api-User 头（账户 id），放在 user 对象里。
  // 这里单独取出来，避免被后续的候选截断丢弃。
  var userId = '';
  try {
    var u = window.localStorage.getItem('user');
    if (u) {
      var o = JSON.parse(u);
      if (o && o.id) userId = String(o.id);
    }
  } catch (e) {}
  return JSON.stringify({ items: out, userId: userId });
})();
"""

/**
 * 从 localStorage 的 user 值里取出账户 id。
 * 该值一般是 {"id":123,"username":"...","role":1,...} 形式的 JSON，
 * 也可能是被二次转义的字符串。
 */
private fun extractUserId(raw: String): String {
    val text = raw.trim().let {
        // 可能多包了一层引号
        if (it.length > 1 && it.startsWith("\"") && it.endsWith("\"")) {
            it.substring(1, it.length - 1).replace("\\\"", "\"")
        } else it
    }
    val parsed = runCatching {
        Json { ignoreUnknownKeys = true; isLenient = true }
            .parseToJsonElement(text) as? JsonObject
    }.getOrNull() ?: return ""
    val id = (parsed["id"] as? JsonPrimitive)?.contentOrNull ?: return ""
    return if (id.isNotBlank() && id != "0") id else ""
}

/**
 * 解析注入脚本返回的内容：候选凭据 + 账户 id。
 *
 * evaluateJavascript 回来的是一层 JSON 字符串，里面才是真正的对象，
 * 所以先按 JSON 解出内层文本，再解一次，交给标准解析器处理转义。
 */
private fun parseSniff(raw: String?, json: Json): Pair<List<FoundToken>, String> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList<FoundToken>() to ""

    val inner = runCatching {
        (json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull
    }.getOrNull() ?: raw

    val root = runCatching { json.parseToJsonElement(inner) as? JsonObject }.getOrNull()
        ?: return emptyList<FoundToken>() to ""

    val userId = (root["userId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val arr = root["items"] as? JsonArray ?: return emptyList<FoundToken>() to userId

    val list = arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val source = (obj["source"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val key = (obj["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        FoundToken(source, key, value)
    }

    // 名称里含 token / auth / jwt 的优先，其次是长度像令牌的
    val sorted = list.sortedByDescending { c ->
        val k = c.key.lowercase()
        var score = 0
        // 智谱控制台令牌：抓到它才能查余额，权重给最高
        if (k.contains("bigmodel_token")) score += 160
        if (k.contains("access_token") || k.contains("accesstoken")) score += 100
        if (k.contains("auth_token") || k.contains("authtoken")) score += 95
        if (k.contains("refresh")) score -= 30
        if (k.contains("token")) score += 60
        if (k.contains("auth") || k.contains("jwt") || k.contains("session")) score += 40
        // JWT 形态：三段点号分隔
        if (c.value.count { it == '.' } == 2 && c.value.length > 40) score += 50
        if (c.value.length > 60) score += 10
        score
    }.take(12)

    return sorted to userId
}
