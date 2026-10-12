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
 * 智谱（BigModel）控制台余额查询。
 *
 * 智谱没有开放「用 API Key 查余额」的接口，余额只在控制台里显示。
 * 但控制台前端自己调的是一个普通 HTTP 接口：
 *
 *     GET https://open.bigmodel.cn/api/biz/account/getAccountBalanceEnough
 *     Authorization: Bearer <控制台登录令牌>
 *
 * 这个令牌存放在控制台页面的 localStorage（键名 bigmodel_token_production），
 * 不是 HttpOnly Cookie，所以应用内置网页登录后可以由抓取脚本直接取到，
 * 再带上来查余额。
 */
object ZhipuApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 控制台余额接口（从前端 bundle 里提取并经请求验证存在） */
    private const val BALANCE_PATH = "/api/biz/account/getAccountBalanceEnough"

    /** 控制台登录令牌在 localStorage 里的键名 */
    const val TOKEN_STORAGE_KEY = "bigmodel_token_production"

    data class Balance(
        val display: String,
    )

    /** 登录令牌失效 */
    class Expired(message: String) : IllegalStateException(message)

    suspend fun query(baseUrl: String, token: String): Balance = withContext(Dispatchers.IO) {
        val root = BalanceApi.rootOf(baseUrl).ifBlank { "https://open.bigmodel.cn" }
        if (token.isBlank()) throw Expired("未获取到控制台登录令牌")

        val (code, body) = get(root + BALANCE_PATH, token)

        if (code == null) throw IllegalStateException(body)
        if (code == 401 || code == 403) throw Expired("登录令牌已失效，请重新在应用内登录智谱控制台")
        if (code !in 200..299) throw IllegalStateException("HTTP $code")

        val parsed = parse(body)
            ?: throw Expired("登录令牌已失效，请重新在应用内登录智谱控制台")
        parsed
    }

    /**
     * 解析余额。
     *
     * 正常返回形如：
     *   {"code":200,"msg":"成功","data":{"balanceEnough":true,...},"success":true}
     * 令牌无效时 code=1001，msg 里会说明缺少身份参数。
     */
    private fun parse(body: String): Balance? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null

        // 未登录 / 令牌失效：接口统一返回 code=1001 + 说明
        val code = (root["code"] as? JsonPrimitive)?.contentOrNull
        if (code != null && code != "200" && code != "0") {
            val msg = (root["msg"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (msg.contains("身份验证") || msg.contains("Authorization")) return null
            throw IllegalStateException(msg.ifBlank { "接口返回 code=$code" })
        }

        val data = root["data"] as? JsonObject ?: return null

        // 该接口主要返回「余额是否充足」的布尔值，能拿到数字金额时一并展示
        val amount = listOf("balance", "amount", "availableAmount", "remainAmount", "totalAmount")
            .firstNotNullOfOrNull { key ->
                (data[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            }
        if (amount != null) {
            return Balance("¥" + format(amount))
        }

        val enough = (data["balanceEnough"] as? JsonPrimitive)?.contentOrNull
            ?: (data["enough"] as? JsonPrimitive)?.contentOrNull
        return when (enough?.lowercase()) {
            "true" -> Balance("余额充足（控制台未返回具体金额）")
            "false" -> Balance("余额不足")
            else -> null
        }
    }

    private fun format(v: Double): String =
        String.format("%.4f", v).trimEnd('0').trimEnd('.')

    private fun get(url: String, token: String): Pair<Int?, String> {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                // 控制台前端使用的实际请求头，缺了 zai-version 可能被网关拒绝
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("zai-version", "1.0.0")
                setRequestProperty("Set-Language", "zh")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/126.0.0.0 Mobile Safari/537.36",
                )
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
    }
}
