package dev.relayhub.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.relayhub.data.AccountApi
import dev.relayhub.data.AccentPreset
import dev.relayhub.data.ColorSource
import dev.relayhub.data.Endpoint
import dev.relayhub.data.EndpointDao
import dev.relayhub.data.PrefsRepo
import dev.relayhub.data.RelayDatabase
import dev.relayhub.data.SchemeStyle
import dev.relayhub.data.ThemeMode
import dev.relayhub.data.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

enum class SortMode(val label: String) {
    Updated("最近更新"),
    Name("名称"),
    Created("添加时间"),
    Status("连通状态"),
}

data class HomeUiState(
    val items: List<Endpoint> = emptyList(),
    val query: String = "",
    val sort: SortMode = SortMode.Updated,
    val selectedIds: Set<Long> = emptySet(),
    val checking: Set<Long> = emptySet(),
    val balancing: Set<Long> = emptySet(),
) {
    val total: Int get() = items.size
    val reachable: Int get() = items.count { it.lastCheckOk == true }
    val unreachable: Int get() = items.count { it.lastCheckOk == false }
    val unchecked: Int get() = items.count { it.lastCheckAt <= 0L }
}

@Serializable
private data class TransferItem(
    val name: String,
    val url: String,
    val apiKey: String = "",
    val memo: String = "",
    val tags: String = "",
    val favorite: Boolean = false,
)

@Serializable
private data class TransferFile(
    val app: String = "RelayHub",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val items: List<TransferItem> = emptyList(),
)

class RelayViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: EndpointDao = RelayDatabase.get(app).endpointDao()
    private val prefs = PrefsRepo(app)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    val theme: StateFlow<ThemePrefs> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePrefs())

    val maskKeys: StateFlow<Boolean> = prefs.maskKeys
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoClearClipboard: StateFlow<Boolean> = prefs.autoClearClipboard
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortMode.Updated)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val checking = MutableStateFlow<Set<Long>>(emptySet())
    private val balancing = MutableStateFlow<Set<Long>>(emptySet())

    /** 卡片上临时点开的密钥（仅本次会话内） */
    private val revealed = MutableStateFlow<Set<Long>>(emptySet())

    /** 数据库首次读取完成前，界面显示占位而不是「还没有中转站」 */
    private val ready = MutableStateFlow(false)

    val isReady: StateFlow<Boolean> = ready

    private val all: StateFlow<List<Endpoint>> = dao.observeAll()
        .onEach { ready.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ui: StateFlow<HomeUiState> = combine(
        combine(all, query, sort, selected, checking) { list, q, s, sel, chk ->
            Quad(list, q, s, sel, chk)
        },
        balancing,
    ) { quad, bal ->
        HomeUiState(
            items = quad.list.filterBy(quad.q).sortedBy(quad.s),
            query = quad.q,
            sort = quad.s,
            selectedIds = quad.sel,
            checking = quad.chk,
            balancing = bal,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val revealedIds: StateFlow<Set<Long>> = revealed

    fun isRevealed(id: Long): Boolean = id in revealed.value

    fun toggleReveal(id: Long) {
        revealed.value = revealed.value.let { if (id in it) it - id else it + id }
    }

    private fun List<Endpoint>.filterBy(q: String): List<Endpoint> {
        if (q.isBlank()) return this
        val key = q.trim().lowercase()
        return filter { e ->
            e.name.lowercase().contains(key) ||
                e.url.lowercase().contains(key) ||
                e.memo.lowercase().contains(key) ||
                e.tags.lowercase().contains(key) ||
                e.apiKey.lowercase().contains(key)
        }
    }

    private fun List<Endpoint>.sortedBy(mode: SortMode): List<Endpoint> = when (mode) {
        SortMode.Updated -> sortedWith(
            compareByDescending<Endpoint> { it.favorite }.thenByDescending { it.updatedAt },
        )
        SortMode.Name -> sortedWith(
            compareByDescending<Endpoint> { it.favorite }.thenBy { it.name.lowercase() },
        )
        SortMode.Created -> sortedWith(
            compareByDescending<Endpoint> { it.favorite }.thenByDescending { it.createdAt },
        )
        SortMode.Status -> sortedWith(
            compareByDescending<Endpoint> { it.favorite }
                .thenBy { it.lastCheckOk == false }
                .thenBy { it.lastCheckOk == null },
        )
    }

    fun setQuery(q: String) { query.value = q }
    fun setSort(mode: SortMode) { sort.value = mode }

    // ---------- 选择 ----------

    fun toggleSelect(id: Long) {
        selected.value = selected.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { selected.value = emptySet() }

    fun selectAll() { selected.value = ui.value.items.map { it.id }.toSet() }

    fun toggleSelectAll() {
        val visible = ui.value.items.map { it.id }.toSet()
        selected.value =
            if (visible.isNotEmpty() && selected.value.containsAll(visible)) emptySet() else visible
    }

    fun selectBy(predicate: (Endpoint) -> Boolean) {
        selected.value = ui.value.items.filter(predicate).map { it.id }.toSet()
    }

    // ---------- 增删改 ----------

    fun save(draft: Endpoint, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (draft.id == 0L) {
                dao.insert(draft.copy(createdAt = now, updatedAt = now))
            } else {
                val old = dao.findById(draft.id)
                // 手动改动令牌后，旧的过期时间不再可信
                val tokenChanged = old != null && old.authToken != draft.authToken
                dao.update(
                    draft.copy(
                        createdAt = old?.createdAt ?: draft.createdAt,
                        updatedAt = now,
                        lastCheckAt = old?.lastCheckAt ?: draft.lastCheckAt,
                        lastCheckOk = old?.lastCheckOk ?: draft.lastCheckOk,
                        lastCheckDetail = old?.lastCheckDetail ?: draft.lastCheckDetail,
                        tokenExpiresAt = if (tokenChanged) 0L else (old?.tokenExpiresAt ?: draft.tokenExpiresAt),
                    ),
                )
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun duplicate(item: Endpoint) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insert(
                item.copy(
                    id = 0L,
                    name = "${item.name} 副本",
                    createdAt = now,
                    updatedAt = now,
                    lastCheckAt = 0L,
                    lastCheckOk = null,
                    lastCheckDetail = "",
                ),
            )
        }
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.deleteByIds(ids.toList())
            selected.value = selected.value - ids.toSet()
            revealed.value = revealed.value - ids.toSet()
        }
    }

    fun toggleFavorite(item: Endpoint) {
        viewModelScope.launch { dao.setFavorite(item.id, !item.favorite) }
    }

    fun rename(item: Endpoint, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.update(item.copy(name = trimmed, updatedAt = System.currentTimeMillis())) }
    }

    // ---------- 复制 / 导出 / 导入 ----------

    /** 复制文本；开启自动清除时延时擦除剪贴板，避免密钥长期驻留 */
    fun copy(label: String, text: String, sensitive: Boolean = false) {
        val ctx = getApplication<Application>()
        Clip.write(ctx, label, text)
        if (sensitive && autoClearClipboard.value) {
            viewModelScope.launch {
                delay(60_000)
                val now = Clip.read(ctx).text
                if (now == text) Clip.clear(ctx)
            }
        }
    }

    fun copyAll(item: Endpoint) {
        val text = buildString {
            appendLine("名称: ${item.name}")
            appendLine("地址: ${item.url}")
            if (item.apiKey.isNotBlank()) appendLine("API Key: ${item.apiKey}")
            if (item.tags.isNotBlank()) appendLine("标签: ${item.tags}")
            if (item.memo.isNotBlank()) appendLine("备注: ${item.memo}")
        }.trim()
        copy("中转站配置", text, sensitive = true)
    }

    fun exportJson(items: List<Endpoint>): String = json.encodeToString(
        TransferFile(
            items = items.map {
                TransferItem(it.name, it.url, it.apiKey, it.memo, it.tags, it.favorite)
            },
        ),
    )

    fun copySelectedAsJson(fallback: List<Endpoint>) {
        val ids = selected.value
        val picked = ui.value.items.filter { it.id in ids }.ifEmpty { fallback }
        if (picked.isEmpty()) return
        copy("中转站备份", exportJson(picked), sensitive = true)
    }

    /**
     * 导出备份到系统下载目录（Download/RelayHub/）。
     * 用 MediaStore 写入，Android 10 以上无需申请存储权限，
     * 文件管理器和电脑上都能直接看到。
     */
    fun backupToDownload(): String? = runCatching {
        val ctx = getApplication<Application>()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val fileName = "relayhub-$stamp.json"
        val body = exportJson(all.value).toByteArray(Charsets.UTF_8)

        val resolver = ctx.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
            put(
                android.provider.MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/RelayHub",
            )
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return null

        resolver.openOutputStream(uri)?.use { it.write(body) }
        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        "Download/RelayHub/$fileName"
    }.onFailure { it.printStackTrace() }.getOrNull()

    /** 从用户选中的文件（如 Download 里的备份）导入 */
    fun importFromUri(uri: android.net.Uri?, onResult: (String) -> Unit) {
        if (uri == null) {
            onResult("没有选择文件")
            return
        }
        val ctx = getApplication<Application>()
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            onResult("读取文件失败，或文件为空")
            return
        }
        importFromText(text, onResult)
    }

    /** 兼容旧入口：导出到应用私有目录 */
    fun backupToFile(): String? = backupToDownload()

    fun importFromText(text: String?, onResult: (String) -> Unit) {
        if (text.isNullOrBlank()) {
            onResult("剪贴板里没有可导入的内容")
            return
        }
        viewModelScope.launch {
            val parsed = parseTransfer(text)
            if (parsed.isNullOrEmpty()) {
                onResult("无法解析，需要 RelayHub 导出的 JSON")
                return@launch
            }
            val now = System.currentTimeMillis()
            dao.insertAll(
                parsed.map {
                    Endpoint(
                        name = it.name.ifBlank { "导入站点" },
                        url = it.url,
                        apiKey = it.apiKey,
                        memo = it.memo,
                        tags = it.tags,
                        favorite = it.favorite,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            onResult("已导入 ${parsed.size} 条")
        }
    }

    private fun parseTransfer(text: String): List<TransferItem>? {
        runCatching { json.decodeFromString<TransferFile>(text) }.getOrNull()
            ?.let { return it.items }
        runCatching { json.decodeFromString<List<TransferItem>>(text) }.getOrNull()
            ?.let { return it }
        runCatching { listOf(json.decodeFromString<TransferItem>(text)) }.getOrNull()
            ?.let { return it }
        return null
    }

    fun importFromClipboard(onResult: (String) -> Unit) {
        val res = Clip.read(getApplication())
        if (!res.ok) {
            onResult(res.error ?: "读取剪贴板失败")
            return
        }
        importFromText(res.text, onResult)
    }

    // ---------- 测活 ----------

    fun checkAlive(items: Collection<Endpoint>) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }.toSet()
        checking.value = checking.value + ids
        items.forEach { item ->
            viewModelScope.launch {
                val (ok, detail) = ping(item)
                dao.setCheckResult(item.id, System.currentTimeMillis(), ok, detail)
                checking.value = checking.value - item.id
            }
        }
    }

    fun checkAll() = checkAlive(ui.value.items)

    private suspend fun ping(item: Endpoint): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val raw = item.url.trim()
        if (raw.isBlank()) return@withContext false to "URL 为空"
        val target = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        runCatching {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "RelayHub/1.0")
                if (item.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${item.apiKey}")
                }
            }
            val started = System.currentTimeMillis()
            val code = conn.responseCode
            val cost = System.currentTimeMillis() - started
            runCatching { conn.inputStream?.close() }
            runCatching { conn.errorStream?.close() }
            conn.disconnect()
            when {
                code in 200..399 -> true to "HTTP $code · ${cost}ms"
                code == 401 || code == 403 -> true to "HTTP $code 需鉴权 · ${cost}ms"
                code == 404 -> true to "HTTP 404 可达 · ${cost}ms"
                code in 500..599 -> false to "HTTP $code 服务端异常"
                else -> false to "HTTP $code"
            }
        }.getOrElse {
            val reason = when (it) {
                is java.net.UnknownHostException -> "域名解析失败"
                is java.net.SocketTimeoutException -> "连接超时"
                is javax.net.ssl.SSLException -> "证书错误"
                else -> it.javaClass.simpleName
            }
            false to reason
        }
    }

    // ---------- 余额 ----------

    /**
     * 刷新余额。
     *
     * 中转站的余额接口只认登录令牌，API Key 调不通，
     * 所以流程是：优先用已缓存的令牌 → 失效则用账号密码重新登录 → 读取账户信息。
     */
    fun refreshBalance(items: Collection<Endpoint>, onResult: (String) -> Unit = {}) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }.toSet()
        balancing.value = balancing.value + ids
        items.forEach { item ->
            viewModelScope.launch {
                val message = refreshOne(item)
                balancing.value = balancing.value - item.id
                withContext(Dispatchers.Main) { onResult("${item.name}：$message") }
            }
        }
    }

    private suspend fun refreshOne(item: Endpoint): String {
        val base = item.panelUrl.ifBlank { item.url }

        // 1) 官方服务商：能查余额的（DeepSeek、Moonshot、硅基流动、OpenRouter）直接查；
        //    没开放余额接口的（智谱、MiMo、百炼等）只校验 Key，并说明去哪看余额
        val official = dev.relayhub.data.OfficialApi.query(item.url, item.apiKey)
        if (official != null) {
            val d = official.display
            val isBalance = !d.startsWith("未填写") &&
                !d.startsWith("API Key 无效") &&
                !d.startsWith("HTTP") &&
                !d.startsWith("该站不提供") &&
                !d.contains("未找到") &&
                !d.contains("请到")
            dao.setBalanceAndToken(
                item.id,
                if (isBalance) d else "",
                System.currentTimeMillis(),
                // 没有余额接口不算失败，如实展示说明即可，不要标成红色错误
                if (isBalance) "" else d,
                item.authToken,
                item.tokenExpiresAt,
            )
            return if (isBalance) "${official.vendor} ${d}" else "${official.vendor}：$d"
        }

        // 1.5) 智谱：官方没开放 API Key 查余额，但控制台接口可以用登录令牌调，
        //      令牌存在 localStorage（非 HttpOnly），能在应用内登录后抓到
        if (item.url.lowercase().let { it.contains("bigmodel.cn") } && item.authToken.isNotBlank()) {
            val res = runCatching {
                dev.relayhub.data.ZhipuApi.query(item.url, item.authToken)
            }
            res.getOrNull()?.let { bal ->
                dao.setBalanceAndToken(
                    item.id,
                    bal.display,
                    System.currentTimeMillis(),
                    "",
                    item.authToken,
                    item.tokenExpiresAt,
                )
                return "智谱 ${bal.display}"
            }
            val err = res.exceptionOrNull()
            if (err is dev.relayhub.data.ZhipuApi.Expired) {
                // 令牌失效：清掉，回到下面的官方分支给出「去哪看余额」的说明
                dao.setBalanceAndToken(
                    item.id, "", System.currentTimeMillis(), "", "", 0L,
                )
            }
        }

        // 2) 网页面板 Cookie。new-api 有两个版本，依次尝试：
        //    新版（有 /api/user/auth/refresh，轮换 new_api_refresh）
        //    旧版（session Cookie + New-Api-User 头，查 /api/user/self）
        if (item.siteCookie.isNotBlank()) {
            var lastMsg: String? = null

            val modern = runCatching {
                dev.relayhub.data.SessionApi.refresh(base, item.siteCookie)
            }
            val fresh = modern.getOrNull()
            if (fresh != null && fresh.display.isNotBlank()) {
                dao.setBalanceAndCookie(
                    item.id,
                    fresh.display,
                    System.currentTimeMillis(),
                    "",
                    fresh.accessToken,
                    fresh.expiresAt,
                    fresh.newCookie ?: item.siteCookie,
                    item.siteUserId,
                )
                val who = fresh.account?.let { " · $it" } ?: ""
                return "面板余额 ${fresh.display}$who"
            }

            val modernErr = modern.exceptionOrNull()
            when (modernErr) {
                // 新版接口不存在 → 说明是旧版 new-api，走会话方式
                is dev.relayhub.data.SessionApi.NotSupported -> {
                    if (item.siteUserId.isNotBlank()) {
                        val legacy = runCatching {
                            dev.relayhub.data.SessionApi.selfBySession(
                                base, item.siteCookie, item.siteUserId,
                            )
                        }
                        val session = legacy.getOrNull()
                        if (session != null && session.display.isNotBlank()) {
                            dao.setBalanceAndCookie(
                                item.id, session.display, System.currentTimeMillis(), "",
                                item.authToken, item.tokenExpiresAt, item.siteCookie, item.siteUserId,
                            )
                            val who = session.account?.let { " · $it" } ?: ""
                            return "面板余额 ${session.display}$who"
                        }
                        lastMsg = legacy.exceptionOrNull()?.message ?: "会话查询失败"
                    } else {
                        lastMsg = "该站需要在应用内重新登录一次，以便同时取得账户 id"
                    }
                }
                else -> lastMsg = modernErr?.message ?: "Cookie 查询失败"
            }

            val msg = lastMsg ?: "Cookie 查询失败"
            // 凭证彻底失效时清掉，避免一直报同一个错
            val expired = modernErr is dev.relayhub.data.SessionApi.Expired
            dao.setBalanceAndCookie(
                item.id,
                "",
                System.currentTimeMillis(),
                msg,
                item.authToken,
                item.tokenExpiresAt,
                if (expired) "" else item.siteCookie,
                item.siteUserId,
            )
            // 没有账号密码可退时，直接把原因返回
            if (item.account.isBlank() || item.accountPassword.isBlank()) return msg
        }

        // 3) 第三方中转站：余额接口只认登录令牌
        val hasAccount = item.account.isNotBlank() && item.accountPassword.isNotBlank()
        if (!hasAccount && item.authToken.isBlank()) {
            val hint = "未配置面板账号、登录令牌或登录 Cookie，无法查余额"
            dao.setBalanceAndToken(item.id, "", System.currentTimeMillis(), hint, item.authToken, item.tokenExpiresAt)
            return hint
        }

        // 1) 先用缓存令牌
        val cached = item.authToken
        val notExpired = item.tokenExpiresAt == 0L || item.tokenExpiresAt > System.currentTimeMillis()
        if (cached.isNotBlank() && notExpired) {
            val res = AccountApi.fetchBalance(base, cached)
            res.onSuccess { bal ->
                dao.setBalanceAndToken(item.id, bal.display, System.currentTimeMillis(), "", cached, item.tokenExpiresAt)
                return bal.display
            }
            if (res.exceptionOrNull() !is AccountApi.Unauthorized) {
                val err = res.exceptionOrNull()?.message ?: "查询失败"
                dao.setBalanceAndToken(item.id, "", System.currentTimeMillis(), err, cached, item.tokenExpiresAt)
                return err
            }
        }

        // 2) 令牌缺失或失效，重新登录
        val login = AccountApi.login(base, item.account, item.accountPassword)
        val loginResult = login.getOrElse { err ->
            val msg = err.message ?: "登录失败"
            dao.setBalanceAndToken(item.id, "", System.currentTimeMillis(), msg, "", 0L)
            return msg
        }

        val balRes = AccountApi.fetchBalance(base, loginResult.token)
        return balRes.fold(
            onSuccess = { bal ->
                dao.setBalanceAndToken(
                    item.id, bal.display, System.currentTimeMillis(), "",
                    loginResult.token, loginResult.expiresAt,
                )
                bal.display
            },
            onFailure = { err ->
                val msg = err.message ?: "读取账户信息失败"
                dao.setBalanceAndToken(
                    item.id, "", System.currentTimeMillis(), msg,
                    loginResult.token, loginResult.expiresAt,
                )
                msg
            },
        )
    }

    fun refreshAllBalances(onResult: (String) -> Unit = {}) =
        refreshBalance(ui.value.items.filter { it.hasLoginMaterial }, onResult)

    // ---------- 主题 ----------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setColorSource(source: ColorSource) = viewModelScope.launch { prefs.setColorSource(source) }
    fun setAccent(accent: AccentPreset) = viewModelScope.launch { prefs.setAccent(accent) }
    fun setStyle(style: SchemeStyle) = viewModelScope.launch { prefs.setStyle(style) }
    fun setPureBlack(on: Boolean) = viewModelScope.launch { prefs.setPureBlack(on) }
    fun setMaskKeys(on: Boolean) = viewModelScope.launch { prefs.setMaskKeys(on) }
    fun setAutoClearClipboard(on: Boolean) = viewModelScope.launch { prefs.setAutoClearClipboard(on) }
}

@Suppress("unused")
private val appContextHint: Context? = null

/** combine 最多支持 5 个流，这里打包中间结果再与余额状态合并 */
private data class Quad<A, B, C, D, E>(
    val list: A,
    val q: B,
    val s: C,
    val sel: D,
    val chk: E,
)
