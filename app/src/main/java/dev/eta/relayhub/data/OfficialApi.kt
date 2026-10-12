package dev.eta.relayhub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 官方大模型服务商的余额查询。
 *
 * 只有一部分厂商开放了「用 API Key 查余额」的接口（见 [Style] 里带解析逻辑的那些）；
 * 其余厂商的余额只在自己的控制台里显示，需要登录会话，API Key 调不到。
 * 这类厂商仍然登记在册：至少能做 Key 有效性校验，并给出明确说明，
 * 避免用户看到一句没头没尾的 HTTP 404。
 */
object OfficialApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 一个已知的官方服务商 */
    data class Vendor(
        val key: String,
        val label: String,
        /** 域名关键字，用于从 URL 判断厂商 */
        val hostKeywords: List<String>,
        /** 余额接口路径；为空表示该厂商不提供 API 余额查询 */
        val path: String = "",
        /** 结果解析方式 */
        val style: Style = Style.None,
        /** 用于校验 Key 是否有效的模型列表接口 */
        val modelsPath: String = "/v1/models",
        /** 控制台地址，查不到余额时告知用户去哪里看 */
        val consoleUrl: String = "",
    )

    enum class Style {
        /** DeepSeek：balance_infos 数组 */
        DeepSeek,
        /** Moonshot：data.available_balance */
        Moonshot,
        /** SiliconFlow：带 code/data 包装 */
        SiliconFlow,
        /** OpenRouter：data.total_credits - data.total_usage */
        OpenRouter,
        /** 该厂商没有余额接口，只做 Key 校验 */
        None,
    }

    val vendors: List<Vendor> = listOf(
        // ---------- 支持用 API Key 查余额 ----------
        Vendor(
            key = "deepseek",
            label = "DeepSeek 深度求索",
            hostKeywords = listOf("api.deepseek.com", "deepseek.com"),
            path = "/user/balance",
            style = Style.DeepSeek,
            modelsPath = "/models",
            consoleUrl = "platform.deepseek.com",
        ),
        Vendor(
            key = "moonshot",
            label = "Moonshot 月之暗面",
            hostKeywords = listOf("api.moonshot.cn", "moonshot.cn", "api.moonshot.ai", "kimi.com"),
            path = "/v1/users/me/balance",
            style = Style.Moonshot,
            consoleUrl = "platform.moonshot.cn",
        ),
        Vendor(
            key = "siliconflow",
            label = "SiliconFlow 硅基流动",
            hostKeywords = listOf("api.siliconflow.cn", "siliconflow.cn", "api.siliconflow.com"),
            path = "/v1/user/info",
            style = Style.SiliconFlow,
            consoleUrl = "cloud.siliconflow.cn",
        ),
        Vendor(
            key = "openrouter",
            label = "OpenRouter",
            hostKeywords = listOf("openrouter.ai"),
            path = "/api/v1/credits",
            style = Style.OpenRouter,
            modelsPath = "/api/v1/models",
            consoleUrl = "openrouter.ai",
        ),

        // ---------- 已登记：Key 可校验，但厂商未开放余额接口 ----------
        Vendor(
            key = "zhipu",
            label = "智谱 GLM",
            hostKeywords = listOf("open.bigmodel.cn", "bigmodel.cn"),
            modelsPath = "/api/paas/v4/models",
            consoleUrl = "bigmodel.cn/finance/overview",
        ),
        Vendor(
            key = "mimo",
            label = "小米 MiMo",
            hostKeywords = listOf("api.xiaomimimo.com", "xiaomimimo.com"),
            modelsPath = "/v1/models",
            consoleUrl = "xiaomimimo.com",
        ),
        Vendor(
            key = "volces",
            label = "火山方舟（豆包）",
            hostKeywords = listOf("ark.cn-beijing.volces.com", "volces.com", "volcengineapi.com"),
            modelsPath = "",
            consoleUrl = "console.volcengine.com/ark",
        ),
        Vendor(
            key = "dashscope",
            label = "阿里云百炼（通义）",
            hostKeywords = listOf("dashscope.aliyuncs.com", "aliyuncs.com"),
            modelsPath = "/compatible-mode/v1/models",
            consoleUrl = "bailian.console.aliyun.com",
        ),
        Vendor(
            key = "qianfan",
            label = "百度千帆（文心）",
            hostKeywords = listOf("qianfan.baidubce.com", "baidubce.com"),
            modelsPath = "",
            consoleUrl = "console.bce.baidu.com/qianfan",
        ),
        Vendor(
            key = "hunyuan",
            label = "腾讯混元",
            hostKeywords = listOf("api.hunyuan.cloud.tencent.com", "hunyuan.cloud.tencent.com"),
            modelsPath = "",
            consoleUrl = "console.cloud.tencent.com/hunyuan",
        ),
        Vendor(
            key = "minimax",
            label = "MiniMax 海螺",
            hostKeywords = listOf("api.minimax.chat", "api.minimaxi.com", "minimax.chat", "minimaxi.com"),
            modelsPath = "",
            consoleUrl = "platform.minimaxi.com",
        ),
        Vendor(
            key = "stepfun",
            label = "阶跃星辰 StepFun",
            hostKeywords = listOf("api.stepfun.com", "stepfun.com"),
            modelsPath = "/v1/models",
            consoleUrl = "platform.stepfun.com",
        ),
        Vendor(
            key = "lingyi",
            label = "零一万物 Yi",
            hostKeywords = listOf("api.lingyiwanwu.com", "lingyiwanwu.com"),
            modelsPath = "/v1/models",
            consoleUrl = "platform.lingyiwanwu.com",
        ),
        Vendor(
            key = "baichuan",
            label = "百川智能",
            hostKeywords = listOf("api.baichuan-ai.com", "baichuan-ai.com"),
            modelsPath = "/v1/models",
            consoleUrl = "platform.baichuan-ai.com",
        ),
        Vendor(
            key = "sensenova",
            label = "商汤 SenseNova",
            hostKeywords = listOf("api.sensenova.cn", "sensenova.cn"),
            modelsPath = "/v1/models",
            consoleUrl = "platform.sensenova.cn",
        ),
        Vendor(
            key = "xfyun",
            label = "讯飞星火",
            hostKeywords = listOf("spark-api-open.xf-yun.com", "xf-yun.com"),
            modelsPath = "",
            consoleUrl = "console.xfyun.cn",
        ),
        Vendor(
            key = "baidu",
            label = "百度智能云",
            hostKeywords = listOf("aip.baidubce.com"),
            modelsPath = "",
            consoleUrl = "console.bce.baidu.com",
        ),
    )

    data class Result(
        val display: String,
        val vendor: String,
    )

    /** 根据地址判断是否命中已知官方厂商 */
    fun detect(baseUrl: String): Vendor? {
        val lower = baseUrl.lowercase()
        return vendors.firstOrNull { v -> v.hostKeywords.any { lower.contains(it) } }
    }

    /** 该厂商是否能直接查出余额 */
    fun supportsBalance(baseUrl: String): Boolean =
        detect(baseUrl)?.style?.let { it != Style.None } ?: false

    /**
     * 用 API Key 查询官方余额。
     * 地址不匹配任何已知厂商时返回 null，由调用方回退到中转站流程。
     */
    suspend fun query(baseUrl: String, apiKey: String): Result? = withContext(Dispatchers.IO) {
        val vendor = detect(baseUrl) ?: return@withContext null
        if (apiKey.isBlank()) {
            return@withContext Result("未填写 API Key", vendor.label)
        }

        val root = BalanceApi.rootOf(baseUrl)

        // 该类厂商没有余额接口：只校验 Key，并说明去哪里看余额
        if (vendor.style == Style.None || vendor.path.isBlank()) {
            return@withContext Result(probeKeyOnly(root, vendor, apiKey), vendor.label)
        }

        val url = root + vendor.path
        val (code, body) = get(url, apiKey)

        if (code == null) {
            return@withContext Result(body, vendor.label)
        }
        if (code == 401 || code == 403) {
            return@withContext Result("API Key 无效", vendor.label)
        }
        if (code == 404) {
            // 接口没上线 / 换了路径：退化为 Key 校验，别只丢一句 404
            return@withContext Result(probeKeyOnly(root, vendor, apiKey), vendor.label)
        }
        if (code !in 200..299) {
            return@withContext Result("HTTP $code", vendor.label)
        }

        val text = parse(vendor.style, body) ?: "返回中未找到余额"
        Result(text, vendor.label)
    }

    /**
     * 没有余额接口时的兜底：调模型列表判断 Key 是否可用，
     * 再告诉用户余额要去哪个控制台看。
     */
    private fun probeKeyOnly(root: String, vendor: Vendor, apiKey: String): String {
        val console = vendor.consoleUrl.ifBlank { "厂商控制台" }
        if (vendor.modelsPath.isBlank()) {
            return "该站不提供余额查询，请到 $console 查看"
        }
        val (code, _) = get(root + vendor.modelsPath, apiKey)
        return when {
            code == null -> "该站不提供余额查询，请到 $console 查看"
            code in 200..299 -> "Key 有效 · 余额请到 $console 查看"
            code == 401 || code == 403 -> "API Key 无效"
            else -> "该站不提供余额查询，请到 $console 查看"
        }
    }

    /** 按厂商风格解析余额 */
    private fun parse(style: Style, body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = (root["data"] as? JsonObject) ?: root

        return when (style) {
            Style.DeepSeek -> {
                val infos = root["balance_infos"]?.jsonArray
                    ?: data["balance_infos"]?.jsonArray
                val first = infos?.firstOrNull()?.jsonObject
                if (first == null) {
                    null
                } else {
                    val currency = (first["currency"] as? JsonPrimitive)?.contentOrNull ?: "CNY"
                    val total = (first["total_balance"] as? JsonPrimitive)?.contentOrNull
                    val granted = (first["granted_balance"] as? JsonPrimitive)?.contentOrNull
                    val topped = (first["topped_up_balance"] as? JsonPrimitive)?.contentOrNull
                    buildString {
                        append(symbol(currency)).append(total ?: "?")
                        val parts = mutableListOf<String>()
                        topped?.takeIf { it != "0.00" && it != "0" }?.let { parts += "充值 $it" }
                        granted?.takeIf { it != "0.00" && it != "0" }?.let { parts += "赠送 $it" }
                        if (parts.isNotEmpty()) append("（${parts.joinToString("，")}）")
                    }
                }
            }

            Style.Moonshot -> {
                val available = (data["available_balance"] as? JsonPrimitive)?.contentOrNull
                val voucher = (data["voucher_balance"] as? JsonPrimitive)?.contentOrNull
                val cash = (data["cash_balance"] as? JsonPrimitive)?.contentOrNull
                available?.let {
                    buildString {
                        append("¥").append(it)
                        val parts = mutableListOf<String>()
                        cash?.takeIf { c -> c != "0" }?.let { parts += "现金 ¥$it" }
                        voucher?.takeIf { v -> v != "0" }?.let { parts += "代金券 ¥$it" }
                        if (parts.isNotEmpty()) append("（${parts.joinToString("，")}）")
                    }
                }
            }

            Style.SiliconFlow -> {
                val charge = (data["totalBalance"] as? JsonPrimitive)?.contentOrNull
                    ?: (data["balance"] as? JsonPrimitive)?.contentOrNull
                charge?.let { "¥$it" }
            }

            Style.OpenRouter -> {
                val credits = (data["total_credits"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                val usage = (data["total_usage"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                if (credits != null) {
                    val left = usage?.let { credits - it } ?: credits
                    "\$${format(left)}（已用 \$${format(usage ?: 0.0)}）"
                } else null
            }

            Style.None -> null
        }
    }

    private fun symbol(currency: String): String = when (currency.uppercase()) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        else -> "$currency "
    }

    private fun format(v: Double): String =
        String.format("%.4f", v).trimEnd('0').trimEnd('.')

    private fun get(url: String, key: String): Pair<Int?, String> {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RelayHub/1.0")
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

/** 供解析复用的数值收集器 */
internal object AccountApiCollected {
    fun collect(
        element: kotlinx.serialization.json.JsonElement,
        prefix: String,
        out: MutableList<Pair<String, String>>,
    ) {
        if (out.size >= 60) return
        when (element) {
            is JsonObject -> element.forEach { (k, v) ->
                collect(v, if (prefix.isEmpty()) k else "$prefix.$k", out)
            }
            is kotlinx.serialization.json.JsonArray -> element.forEachIndexed { i, v ->
                if (i < 5) collect(v, "$prefix[$i]", out)
            }
            is JsonPrimitive -> {
                val raw = element.contentOrNull ?: return
                if (raw.toDoubleOrNull() != null) out += prefix to raw
            }
            else -> Unit
        }
    }
}
