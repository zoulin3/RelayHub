package dev.eta.relayhub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 中转站账户查询。
 *
 * 各站的余额接口只认登录会话令牌，API Key 只能调推理接口，
 * 因此这里先用账号密码换取 access_token，再读取账户信息。
 */
object AccountApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class LoginResult(
        val token: String,
        val userId: String?,
        val expiresAt: Long,
    )

    data class Balance(
        val amount: Double?,
        val display: String,
        val email: String?,
        val concurrency: Int?,
        val raw: String,
    )

    // ---------------- 登录 ----------------

    /** 登录路径候选，按顺序尝试 */
    private val loginPaths = listOf(
        "/api/v1/auth/login",
        "/api/user/login",
        "/api/v1/user/login",
        "/auth/login",
    )

    /** 账户信息路径候选 */
    private val profilePaths = listOf(
        "/api/v1/auth/me",
        "/api/user/self",
        "/api/v1/user/self",
        "/api/user/info",
    )

    suspend fun login(
        baseUrl: String,
        account: String,
        password: String,
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        val root = BalanceApi.rootOf(baseUrl)
        if (root.isBlank()) {
            return@withContext Result.failure(IllegalStateException("接口地址为空"))
        }
        if (account.isBlank() || password.isBlank()) {
            return@withContext Result.failure(IllegalStateException("未填写登录账号或密码"))
        }

        var lastError: String? = null
        for (path in loginPaths) {
            // 同一个路径尝试几种常见的字段命名
            val payloads = listOf(
                """{"email":"$account","password":"$password"}""",
                """{"username":"$account","password":"$password"}""",
                """{"account":"$account","password":"$password"}""",
            )
            for (payload in payloads) {
                val res = post(root + path, payload)
                if (res is HttpResult.Ok) {
                    // 有些站点会返回 200 但内容里说明被拦截（如需要人机验证）
                    val blocked = detectBlock(res.body)
                    if (blocked != null) {
                        return@withContext Result.failure(IllegalStateException(blocked))
                    }
                    val parsed = parseLogin(res.body)
                    if (parsed != null) return@withContext Result.success(parsed)
                    lastError = "$path 返回中未找到令牌"
                } else if (res is HttpResult.Fail) {
                    lastError = res.reason
                    // 404 说明该路径不存在，换下一个路径；其他错误直接反馈
                    if (!res.notFound) break
                }
            }
        }
        Result.failure(IllegalStateException(lastError ?: "登录接口不可用"))
    }

    /**
     * 识别被站点风控拦截的响应。
     * 返回可读提示，没有拦截则返回 null。
     */
    private fun detectBlock(body: String): String? {
        val lower = body.lowercase()
        return when {
            lower.contains("turnstile") || lower.contains("captcha") ->
                "该站登录需要人机验证，请改用登录令牌"
            lower.contains("verify") && lower.contains("required") ->
                "该站登录需要额外验证，请改用登录令牌"
            lower.contains("2fa") || lower.contains("two-factor") ->
                "该站启用了两步验证，请改用登录令牌"
            else -> null
        }
    }

    /** 从登录响应中取出令牌与过期时间 */
    private fun parseLogin(body: String): LoginResult? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = root["data"] as? JsonObject ?: root

        val token = listOf("access_token", "token", "accessToken", "auth_token")
            .firstNotNullOfOrNull { (data[it] as? JsonPrimitive)?.contentOrNull }
            ?: return null

        val userId = listOf("user_id", "id", "uid")
            .firstNotNullOfOrNull { (data[it] as? JsonPrimitive)?.contentOrNull }

        val expiresIn = (data["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val expiresAt = if (expiresIn != null) {
            System.currentTimeMillis() + expiresIn * 1000L
        } else {
            // 没有过期信息时按 7 天保守估计
            System.currentTimeMillis() + 7L * 24 * 3600 * 1000
        }

        return LoginResult(token, userId, expiresAt)
    }

    // ---------------- 余额 ----------------

    suspend fun fetchBalance(
        baseUrl: String,
        token: String,
    ): Result<Balance> = withContext(Dispatchers.IO) {
        val root = BalanceApi.rootOf(baseUrl)
        var lastError: String? = null

        for (path in profilePaths) {
            val res = get(root + path, token)
            when (res) {
                is HttpResult.Ok -> {
                    val parsed = parseBalance(res.body)
                    if (parsed != null) return@withContext Result.success(parsed)
                    lastError = "$path 未找到余额字段"
                }
                is HttpResult.Fail -> {
                    lastError = res.reason
                    if (res.unauthorized) {
                        return@withContext Result.failure(Unauthorized(lastError))
                    }
                    if (!res.notFound) break
                }
            }
        }
        Result.failure(IllegalStateException(lastError ?: "未找到账户信息接口"))
    }

    /** 令牌失效，调用方应重新登录 */
    class Unauthorized(message: String) : IllegalStateException(message)

    private fun parseBalance(body: String): Balance? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = root["data"] as? JsonObject ?: root

        val amount = listOf("balance", "quota", "remain", "credit", "amount")
            .firstNotNullOfOrNull { (data[it] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() }

        val currency = (data["currency"] as? JsonPrimitive)?.contentOrNull
        val email = (data["email"] as? JsonPrimitive)?.contentOrNull
        val concurrency = (data["concurrency"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

        if (amount == null && email == null) return null

        val display = when {
            amount == null -> "已登录（未返回余额）"
            currency == "CNY" -> "¥" + String.format("%.4f", amount).trimEnd('0').trimEnd('.')
            else -> "$" + String.format("%.4f", amount).trimEnd('0').trimEnd('.')
        }

        return Balance(
            amount = amount,
            display = display,
            email = email,
            concurrency = concurrency,
            raw = body,
        )
    }

    // ---------------- HTTP ----------------

    private sealed interface HttpResult {
        data class Ok(val body: String) : HttpResult
        data class Fail(
            val reason: String,
            val unauthorized: Boolean = false,
            val notFound: Boolean = false,
        ) : HttpResult
    }

    private fun get(url: String, token: String? = null): HttpResult =
        call(url, "GET", null, token)

    private fun post(url: String, payload: String, token: String? = null): HttpResult =
        call(url, "POST", payload, token)

    private fun call(url: String, method: String, payload: String?, token: String?): HttpResult {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RelayHub/1.0")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (payload != null) {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            when {
                code in 200..299 -> HttpResult.Ok(body)
                code == 401 || code == 403 ->
                    HttpResult.Fail("账号或密码不对，或登录已过期", unauthorized = true)
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
    }

    /** 兼容旧逻辑：从任意 JSON 里挑余额类数值 */
    @Suppress("unused")
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
                if (raw.toDoubleOrNull() != null) out += prefix to raw
            }
            else -> Unit
        }
    }
}
