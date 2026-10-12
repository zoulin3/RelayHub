package dev.eta.relayhub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网页面板 Cookie 会话（new-api 系站点）。
 *
 * 这类站点把登录凭证放在 HttpOnly Cookie（new_api_refresh）里，网页脚本读不到，
 * 应用可以从 WebView 的 Cookie 存储里取出来。用这个 Cookie 调
 * `POST /api/user/auth/refresh` 换取短期的 access_token，
 * 响应里同时带着账户信息（quota 等），因此一次请求就能拿到余额。
 *
 * 注意：服务端每次都会轮换 Cookie（Set-Cookie: new_api_refresh=...），
 * 必须把新值写回本地，否则下次就会 401。
 */
object SessionApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** new-api 默认每 1 美元对应 500000 额度 */
    private const val QUOTA_PER_UNIT = 500_000.0

    data class Session(
        /** 展示用的余额文本，例如 $0.2890 */
        val display: String,
        /** 账户名（邮箱或用户名），可能为空 */
        val account: String?,
        /** 访问令牌，可缓存复用 */
        val accessToken: String,
        /** 令牌过期时间戳 */
        val expiresAt: Long,
        /** 服务端轮换后的新 Cookie，需要写回本地 */
        val newCookie: String?,
    )

    /** Cookie 失效，需要用户重新在应用内登录 */
    class Expired(message: String) : IllegalStateException(message)

    /** 该站不是 new-api 风格，没有可用的会话接口 */
    class NotSupported(message: String) : IllegalStateException(message)

    /** 提取 Cookie 串里某个名字的值 */
    fun cookieValue(cookie: String, name: String): String? =
        cookie.split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }

    /**
     * 用 Cookie 换会话信息。
     * 失败时抛出 [Expired]（凭证失效）或 [NotSupported]（站点不是这个体系）。
     */
    suspend fun refresh(
        baseUrl: String,
        cookie: String,
    ): Session = withContext(Dispatchers.IO) {
        val root = BalanceApi.rootOf(baseUrl)
        if (root.isBlank()) throw IllegalStateException("接口地址为空")
        if (cookie.isBlank()) throw Expired("未获取到登录 Cookie")

        val refreshName = listOf("new_api_refresh", "session", "token", "access_token")
            .firstOrNull { cookieValue(cookie, it) != null }
            ?: cookie.substringBefore('=').trim()

        val res = post(
            url = "$root/api/user/auth/refresh",
            payload = null,
            cookie = cookie,
            origin = root,
        )

        when (res) {
            is HttpResult.Fail -> {
                if (res.unauthorized) throw Expired("登录凭证已失效，请在应用内重新登录后抓取")
                if (res.notFound) throw NotSupported("该站是旧版 new-api，改用会话方式查询")
                throw IllegalStateException(res.reason)
            }
            is HttpResult.Ok -> Unit
        }

        val body = (res as HttpResult.Ok).body
        val parsed = parse(body, res.setCookie)

        if (parsed == null) {
            val blocked = detectBlock(body)
            if (blocked != null) throw Expired(blocked)
            throw NotSupported("响应里没有访问令牌，可能不是该类型的站点")
        }

        // 服务端返回的新 Cookie 只包含被轮换的那个名字时，合并回原串
        val merged = mergeCookie(cookie, parsed.newCookie, refreshName)
        parsed.copy(newCookie = merged)
    }

    /**
     * 旧版 new-api 的查询方式。
     *
     * 这类站点没有 /api/user/auth/refresh，登录凭证是 `session` Cookie，
     * 并且额外要求一个 `New-Api-User` 请求头（值是账户 id，网页端存在
     * localStorage 的 user 对象里）。两者齐了才能读 /api/user/self。
     */
    suspend fun selfBySession(
        baseUrl: String,
        cookie: String,
        userId: String,
    ): Session = withContext(Dispatchers.IO) {
        val root = BalanceApi.rootOf(baseUrl)
        if (root.isBlank()) throw IllegalStateException("接口地址为空")
        if (cookie.isBlank()) throw Expired("未获取到登录 Cookie")
        if (userId.isBlank()) throw NotSupported("缺少账户 id（New-Api-User）")

        val (code, body) = getWithHeaders(
            url = "$root/api/user/self",
            cookie = cookie,
            extra = mapOf("New-Api-User" to userId),
            origin = root,
        )

        when (code) {
            null -> throw IllegalStateException(body)
            401, 403 -> throw Expired("登录凭证已失效，请在应用内重新登录后抓取")
            404 -> throw NotSupported("该站没有账户信息接口")
        }
        if (code !in 200..299) throw IllegalStateException("HTTP $code")

        val parsed = parseSelf(body)
            ?: throw Expired("登录已失效，请重新在应用内登录后抓取")
        parsed
    }

    /** 解析 /api/user/self 的响应（new-api 的数据在 data 里，quota 为额度整数） */
    private fun parseSelf(body: String): Session? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null

        val success = (root["success"] as? JsonPrimitive)?.contentOrNull
        if (success == "false") {
            val msg = (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (msg.contains("未登录") || msg.contains("New-Api-User") || msg.contains("access token")) {
                return null
            }
            throw IllegalStateException(msg.ifBlank { "接口返回失败" })
        }

        val data = root["data"] as? JsonObject ?: return null
        val quota = (data["quota"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        val used = (data["used_quota"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        val account = (data["username"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: (data["email"] as? JsonPrimitive)?.contentOrNull
            ?: (data["display_name"] as? JsonPrimitive)?.contentOrNull

        val display = when {
            quota != null -> formatMoney(quota / QUOTA_PER_UNIT)
            used != null -> "已用 " + formatMoney(used / QUOTA_PER_UNIT)
            else -> ""
        }
        if (display.isBlank()) return null

        return Session(
            display = display,
            account = account,
            accessToken = "",
            expiresAt = System.currentTimeMillis() + 24L * 3600 * 1000,
            newCookie = null,
        )
    }

    /** 带自定义请求头的 GET */
    private fun getWithHeaders(
        url: String,
        cookie: String,
        extra: Map<String, String>,
        origin: String?,
    ): Pair<Int?, String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Cookie", cookie)
            extra.forEach { (k, v) -> setRequestProperty(k, v) }
            if (!origin.isNullOrBlank()) {
                setRequestProperty("Origin", origin)
                setRequestProperty("Referer", "$origin/")
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        code to body
    }.getOrElse {
        null to when (it) {
            is java.net.UnknownHostException -> "域名解析失败"
            is java.net.SocketTimeoutException -> "连接超时"
            is javax.net.ssl.SSLException -> "证书错误"
            else -> it.javaClass.simpleName
        }
    }

    /** 把服务端下发的新 Cookie 合并进原 Cookie 串 */
    private fun mergeCookie(old: String, new: String?, rotatedName: String): String? {
        if (new.isNullOrBlank()) return null
        val value = cookieValue(new, rotatedName) ?: run {
            // Set-Cookie 里名字可能不完全一致，退化为取第一段的值
            new.substringBefore(';').substringAfter('=', "").takeIf { it.isNotBlank() }
        } ?: return null

        val pairs = old.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        val index = pairs.indexOfFirst {
            it.substringBefore('=').trim().equals(rotatedName, ignoreCase = true)
        }
        val entry = "$rotatedName=$value"
        if (index >= 0) pairs[index] = entry else pairs.add(entry)
        return pairs.joinToString("; ")
    }

    private fun parse(body: String, setCookie: String?): Session? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val data = root["data"] as? JsonObject ?: return null

        val token = (data["access_token"] as? JsonPrimitive)?.contentOrNull
            ?: (data["token"] as? JsonPrimitive)?.contentOrNull
            ?: return null

        val expiresIn = (data["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val accessExpiresAt = (data["access_expires_at"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val expiresAt = when {
            accessExpiresAt != null && accessExpiresAt > 1_000_000_000L -> accessExpiresAt * 1000L
            expiresIn != null -> System.currentTimeMillis() + expiresIn * 1000L
            else -> System.currentTimeMillis() + 30L * 60 * 1000
        }

        val user = data["user"] as? JsonObject
        val account = user?.let {
            (it["email"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() }
                ?: (it["username"] as? JsonPrimitive)?.contentOrNull
                ?: (it["display_name"] as? JsonPrimitive)?.contentOrNull
        }

        val quota = (user?.get("quota") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        val usedQuota = (user?.get("used_quota") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

        val display = when {
            quota != null -> formatMoney(quota / QUOTA_PER_UNIT)
            usedQuota != null -> "已用 " + formatMoney(usedQuota / QUOTA_PER_UNIT)
            else -> ""
        }

        return Session(
            display = display,
            account = account,
            accessToken = token,
            expiresAt = expiresAt,
            newCookie = extractRefreshCookie(setCookie),
        )
    }

    /** 从 Set-Cookie 头里取出被轮换的 refresh cookie（含名字与值） */
    private fun extractRefreshCookie(setCookie: String?): String? {
        if (setCookie.isNullOrBlank()) return null
        val pair = setCookie.split(';').firstOrNull()?.trim() ?: return null
        return pair.takeIf { it.contains('=') }
    }

    private fun formatMoney(usd: Double): String {
        val text = if (usd >= 1000) {
            String.format("%.2f", usd)
        } else {
            String.format("%.4f", usd)
        }
        return "$" + text.trimEnd('0').trimEnd('.')
    }

    private fun detectBlock(body: String): String? {
        val lower = body.lowercase()
        return when {
            lower.contains("token is expired") || lower.contains("invalid") ||
                lower.contains("unauthorized") -> "登录凭证已失效，请在应用内重新登录后抓取"
            lower.contains("turnstile") || lower.contains("captcha") -> "该站需要人机验证，请重新登录后抓取"
            else -> null
        }
    }

    // ---------------- HTTP ----------------

    private sealed interface HttpResult {
        data class Ok(val body: String, val setCookie: String?) : HttpResult
        data class Fail(
            val reason: String,
            val unauthorized: Boolean = false,
            val notFound: Boolean = false,
        ) : HttpResult
    }

    private fun post(url: String, payload: String?, cookie: String, origin: String?): HttpResult =
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Cookie", cookie)
                if (!origin.isNullOrBlank()) {
                    setRequestProperty("Origin", origin)
                    setRequestProperty("Referer", "$origin/")
                }
                setRequestProperty("Content-Length", "0")
                doOutput = true
            }
            conn.outputStream.use { /* 空请求体 */ }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val setCookie = conn.headerFields["Set-Cookie"]?.joinToString("; ")
            conn.disconnect()

            when {
                code in 200..299 -> HttpResult.Ok(body, setCookie)
                code == 401 || code == 403 -> HttpResult.Fail("登录已过期（HTTP $code）", unauthorized = true)
                code == 404 -> HttpResult.Fail("无此接口（HTTP 404）", notFound = true)
                code == 429 -> HttpResult.Fail("请求过于频繁，稍后再试")
                else -> HttpResult.Fail("HTTP $code")
            }
        }.getOrElse {
            HttpResult.Fail(
                when (it) {
                    is java.net.UnknownHostException -> "域名解析失败"
                    is java.net.SocketTimeoutException -> "连接超时"
                    is javax.net.ssl.SSLException -> "证书错误"
                    else -> it.javaClass.simpleName
                },
            )
        }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
}
