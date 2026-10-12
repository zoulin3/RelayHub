package dev.eta.relayhub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一个第三方中转站（Relay / 代理 API）的配置记录。
 */
@Entity(tableName = "endpoints")
data class Endpoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 显示名称，例如「某某中转」「A 站备用」 */
    val name: String,
    /** 接口基址，例如 https://api.example.com/v1 */
    val url: String,
    /** 密钥，例如 sk-xxxx */
    val apiKey: String = "",
    /** 记忆 / 备注，可写模型、额度、限速、用途等 */
    val memo: String = "",
    /** 标签，逗号分隔 */
    val tags: String = "",
    /** 是否为常用站点 */
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 最近一次测活时间，0 表示未测 */
    val lastCheckAt: Long = 0L,
    /** 最近一次测活结果：null 未测 / true 可达 / false 不可达 */
    val lastCheckOk: Boolean? = null,
    val lastCheckDetail: String = "",

    // ---- 余额 ----
    /** 最近一次查询到的余额描述，例如「$12.34」或「剩余 500000 额度」 */
    val balanceText: String = "",
    /** 余额查询时间，0 表示未查过 */
    val balanceAt: Long = 0L,
    /** 余额查询失败原因，成功时为空 */
    val balanceError: String = "",

    // ---- 网页面板账号（用于读取余额，非推理用的 API Key）----
    /** 登录账号，通常是邮箱 */
    val account: String = "",
    /** 登录密码 */
    val accountPassword: String = "",
    /** 登录后拿到的访问令牌，用于免密查余额 */
    val authToken: String = "",
    /** 令牌过期时间戳，0 表示未知 */
    val tokenExpiresAt: Long = 0L,
    /**
     * 网页面板的登录 Cookie（如 new-api 的 new_api_refresh）。
     *
     * 部分中转站把登录凭证放在 HttpOnly Cookie 里，网页脚本读不到，
     * 只能由应用从 WebView 的 Cookie 存储中取出。查询余额时用它换取短期访问令牌。
     */
    val siteCookie: String = "",
    /**
     * 部分 new-api 站点还要求一个 New-Api-User 头（值为账户 id）。
     * 这个 id 在网页登录后存在 localStorage 的 user 对象里，抓取凭证时一并取出。
     */
    val siteUserId: String = "",
) {
    val tagList: List<String>
        get() = tags.split(',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 脱敏后的密钥：只保留前缀和末尾几位，中间用点补齐 */
    val maskedKey: String
        get() {
            val k = apiKey.trim()
            return when {
                k.isEmpty() -> "未填写密钥"
                k.length <= 12 -> k.take(4) + "•".repeat(4)
                else -> k.take(7) + "•".repeat((k.length - 11).coerceIn(4, 20)) + k.takeLast(4)
            }
        }

    /** 列表里展示的地址：去掉协议前缀，保留完整路径（含 /v1） */
    val shortUrl: String
        get() {
            val raw = url.trim()
            if (raw.isEmpty()) return "未填写地址"
            return raw.removePrefix("https://").removePrefix("http://").trimEnd('/')
        }

    /**
     * 站点根地址：去掉 API 版本段，用于访问网页面板。
     * 例：https://api.example.com/v1 → https://api.example.com
     */
    val panelUrl: String
        get() {
            val raw = url.trim()
            if (raw.isEmpty()) return ""
            var s = raw.trimEnd('/')
            while (true) {
                val seg = s.substringAfterLast('/', "")
                if (seg.matches(Regex("""v\d+[a-zA-Z0-9._-]*""", RegexOption.IGNORE_CASE))) {
                    s = s.substringBeforeLast('/')
                } else break
            }
            return s
        }

    /** 是否已经具备查询余额所需的登录信息 */
    val hasLoginMaterial: Boolean
        get() = siteCookie.isNotBlank() ||
            authToken.isNotBlank() ||
            (account.isNotBlank() && accountPassword.isNotBlank())

    /** Cookie 的简短描述，用于在界面上显示状态而不暴露内容 */
    val cookieHint: String
        get() {
            if (siteCookie.isBlank()) return "未获取"
            val names = siteCookie.split(';')
                .mapNotNull { it.substringBefore('=').trim().ifEmpty { null } }
                .joinToString("、")
            return "已获取（$names）"
        }

    /** 站点主域名，用于卡片左上角的角标首字母 */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase()
            ?: shortUrl.firstOrNull()?.uppercase()?.toString()
            ?: "#"
}
