package dev.eta.relayhub.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 余额查询。
 *
 * 各家中转站的余额接口不统一，这里按常见路径逐个尝试，
 * 并从 JSON 里宽容地挑出余额字段，避免因字段命名差异而查不到。
 */
object BalanceApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 常见余额 / 用量接口路径，按顺序探测 */
    private val candidates = listOf(
        "/api/v1/usage",
        "/api/usage",
        "/api/v1/user/self",
        "/api/user/self",
        "/api/v1/keys",
        "/v1/dashboard/billing/subscription",
        "/api/v1/dashboard/billing/subscription",
        "/v1/dashboard/billing/credit_grants",
    )

    data class Balance(
        val text: String,
        val endpoint: String,
    )

    /** 结果：成功给出描述文本，失败给出原因。网络请求固定在 IO 线程执行。 */
    suspend fun query(
        baseUrl: String,
        apiKey: String,
        extraPaths: List<String> = emptyList(),
    ): Result<Balance> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("未填写 Key，无法查询"))
        }
        val root = rootOf(baseUrl)
        if (root.isBlank()) {
            return Result.failure(IllegalStateException("接口地址为空"))
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val paths = (extraPaths + candidates).distinct()
            var lastError: String? = null

            for (path in paths) {
                val url = root + path
                val res = request(url, apiKey)
                when (res) {
                    is HttpResult.Ok -> {
                        val text = extractBalance(res.body)
                        if (text != null) {
                            return@withContext Result.success(Balance(text, path))
                        }
                        lastError = "$path 未找到余额字段"
                    }
                    is HttpResult.Fail -> {
                        lastError = res.reason
                        // 鉴权失败无需继续试其他路径
                        if (res.authFailed) {
                            return@withContext Result.failure(IllegalStateException(res.reason))
                        }
                    }
                }
            }
            Result.failure(IllegalStateException(lastError ?: "所有已知接口都不可用"))
        }
    }

    private sealed interface HttpResult {
        data class Ok(val body: String) : HttpResult
        data class Fail(val reason: String, val authFailed: Boolean = false) : HttpResult
    }

    private fun request(url: String, apiKey: String): HttpResult {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RelayHub/1.0")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            when {
                code in 200..299 -> HttpResult.Ok(body)
                code == 401 || code == 403 ->
                    HttpResult.Fail("该站不支持用 API Key 查余额（需登录令牌）", authFailed = true)
                code == 404 -> HttpResult.Fail("无此接口（HTTP 404）")
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
    }

    /**
     * 从 JSON 中挑出余额信息。
     * 优先匹配名称里含 balance / remain / credit / quota / available 的数值字段，
     * 找不到时退回展示整体摘要。
     */
    private fun extractBalance(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val hits = mutableListOf<Pair<String, String>>()
        collectNumbers(root, "", hits)

        // 优先取余额类字段
        val priority = listOf("balance", "remain", "credit", "quota", "available", "left")
        for (key in priority) {
            hits.firstOrNull { it.first.lowercase().contains(key) }?.let { (name, value) ->
                return "${value} (${name})"
            }
        }
        // 其次取用量类字段
        val usageKeys = listOf("usage", "used", "cost", "spent")
        for (key in usageKeys) {
            hits.firstOrNull { it.first.lowercase().contains(key) }?.let { (name, value) ->
                return "${value} (${name})"
            }
        }
        return null
    }

    /** 递归收集数值型叶子字段，最多 60 条，避免大响应拖慢解析 */
    private fun collectNumbers(
        element: JsonElement,
        prefix: String,
        out: MutableList<Pair<String, String>>,
    ) {
        if (out.size >= 60) return
        when (element) {
            is JsonObject -> element.forEach { (k, v) ->
                collectNumbers(v, if (prefix.isEmpty()) k else "$prefix.$k", out)
            }
            is JsonArray -> element.forEachIndexed { i, v ->
                if (i < 5) collectNumbers(v, "$prefix[$i]", out)
            }
            is JsonPrimitive -> {
                val raw = element.contentOrNull ?: return
                val numeric = raw.toDoubleOrNull()
                if (numeric != null) {
                    out += prefix to formatNumber(raw)
                }
            }
            else -> Unit
        }
    }

    private fun formatNumber(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        return when {
            d == d.toLong().toDouble() -> d.toLong().toString()
            else -> String.format("%.4f", d).trimEnd('0').trimEnd('.')
        }
    }

    /** 取站点根地址：去掉 /v1 之类的版本段和路径 */
    fun rootOf(baseUrl: String): String {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return ""
        val fixed = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return runCatching {
            val u = URL(fixed)
            val port = if (u.port > 0) ":${u.port}" else ""
            "${u.protocol}://${u.host}$port"
        }.getOrElse { fixed.trimEnd('/') }
    }
}
