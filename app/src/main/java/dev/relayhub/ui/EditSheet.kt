package dev.relayhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.relayhub.data.Endpoint
import dev.relayhub.ui.comp.BottomSheetSurface
import dev.relayhub.ui.comp.SheetPad

/** 添加 / 编辑中转站的底部表单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    editing: Endpoint?,
    readClipboard: () -> Clip.ReadResult,
    onFetchCredential: (address: String, onResult: (String, Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Endpoint) -> Unit,
    onMessage: (String) -> Unit,
) {
    val base = editing ?: Endpoint(name = "", url = "")
    var name by remember(base.id) { mutableStateOf(base.name) }
    var url by remember(base.id) { mutableStateOf(base.url) }
    var key by remember(base.id) { mutableStateOf(base.apiKey) }
    var memo by remember(base.id) { mutableStateOf(base.memo) }
    var tags by remember(base.id) { mutableStateOf(base.tags) }
    var favorite by remember(base.id) { mutableStateOf(base.favorite) }
    var token by remember(base.id) { mutableStateOf(base.authToken) }
    var cookie by remember(base.id) { mutableStateOf(base.siteCookie) }
    var userId by remember(base.id) { mutableStateOf(base.siteUserId) }
    var pasteHint by remember(base.id) { mutableStateOf<String?>(null) }

    val canSave = name.isNotBlank() || url.isNotBlank()
    val panelGuess = dev.relayhub.data.BalanceApi.rootOf(url)

    /** 读取剪贴板并按内容自动归类 */
    fun smartPaste() {
        val res = readClipboard()
        if (!res.ok) {
            pasteHint = res.error ?: "剪贴板里没有可用文本"
            onMessage(pasteHint!!)
            return
        }
        val guess = parseClipboard(res.text.orEmpty())
        if (guess.isEmpty) {
            // 识别不出结构就整段贴进备注，至少不丢内容
            val raw = res.text.orEmpty()
            memo = listOf(memo, raw).filter { it.isNotBlank() }.joinToString("\n")
            pasteHint = "未识别出地址或 Key，已填入备注"
            return
        }
        var filled = mutableListOf<String>()
        guess.url?.let {
            url = it
            filled += "地址"
        }
        guess.apiKey?.let {
            key = it
            filled += "Key"
        }
        if (name.isBlank()) {
            guess.name?.let {
                name = it
                filled += "名称"
            }
        }
        pasteHint = "已识别并填入：${filled.joinToString("、")}"
    }

    BottomSheetSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(SheetPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (editing == null) "添加中转站" else "编辑中转站",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "密钥只保存在本机，可随时复制到其他应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { smartPaste() },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("智能粘贴")
                }
            }

            AnimatedVisibility(visible = pasteHint != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pasteHint.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("例：某某中转 · 主力") },
                leadingIcon = { Icon(Icons.Outlined.Sell, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text("接口地址") },
                placeholder = { Text("https://api.example.com/v1") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("API Key") },
                placeholder = { Text("sk-…") },
                leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = "从剪贴板粘贴",
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(20.dp)
                            .clickable { smartPaste() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("记忆 / 备注") },
                placeholder = { Text("可用模型、额度、限速、价格、注意事项…") },
                leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                minLines = 3,
                maxLines = 8,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("标签") },
                placeholder = { Text("逗号分隔，例：主力,gpt,便宜") },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            val vendor = dev.relayhub.data.OfficialApi.detect(url)
            // 官方厂商分两种：能用 API Key 直接查余额的（DeepSeek 等）不需要凭证；
            // 余额只在控制台里的（智谱、MiMo 等）仍然需要登录后抓凭证。
            val directBalance = dev.relayhub.data.OfficialApi.supportsBalance(url)
            val needsCredential = !directBalance

            Text(
                if (needsCredential) "登录凭证" else "余额查询",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    directBalance && vendor != null ->
                        "识别为 ${vendor.label}，用上面的 API Key 即可直接查询余额，无需登录。"

                    vendor != null ->
                        "识别为 ${vendor.label}。该厂商没有开放 API Key 查余额，余额只在控制台里显示；" +
                            "在应用内登录一次拿到凭证后即可读取。"

                    else ->
                        "第三方中转站的余额只有登录后才能读取，API Key 查不到。在应用内登录一次即可自动取得凭证。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (directBalance) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )


            if (needsCredential) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("登录令牌（可选）") },
                    placeholder = { Text("登录后从浏览器复制的 access token") },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ContentPaste,
                                contentDescription = "从剪贴板粘贴",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        val t = readClipboard().text?.trim().orEmpty()
                                        if (t.isNotEmpty()) token = t
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = "打开网页获取",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onFetchCredential(url) { v, isCookie, uid ->
                                        if (isCookie) {
                                            cookie = v
                                            if (uid.isNotBlank()) userId = uid
                                        } else token = v
                                    } },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            if (vendor != null) {
                                "可粘贴控制台里的登录令牌，或点右侧地球图标在应用内登录后自动抓取。"
                            } else {
                                "可粘贴浏览器里的令牌，或点右侧地球图标在应用内登录后自动抓取。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = { onFetchCredential(url) { v, isCookie, uid ->
                                        if (isCookie) {
                                            cookie = v
                                            if (uid.isNotBlank()) userId = uid
                                        } else token = v
                                    } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("在应用内登录并自动获取令牌")
                }

                // 网页登录 Cookie（HttpOnly，脚本抓不到，由应用原生读取）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (cookie.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录 Cookie",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            when {
                                cookie.isBlank() ->
                                    "未获取。点上方按钮在应用内登录，会自动取得 HttpOnly 的登录凭证。"

                                userId.isNotBlank() ->
                                    "已获取（${cookie.split(';').size} 项）· 账户 id $userId"

                                else ->
                                    "已获取（${cookie.split(';').size} 项，含 HttpOnly）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (cookie.isNotBlank()) {
                        TextButton(onClick = { cookie = ""; userId = "" }) { Text("清除") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalIconButton(onClick = { favorite = !favorite }) {
                    Icon(
                        imageVector = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "常用",
                    )
                }
                Text(
                    text = if (favorite) "已标记为常用，会置顶显示" else "标记为常用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        onSave(
                            base.copy(
                                name = name.ifBlank { "未命名中转" }.trim(),
                                url = url.trim(),
                                apiKey = key.trim(),
                                memo = memo.trim(),
                                tags = tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
                                    .joinToString(","),
                                favorite = favorite,
                                authToken = token.trim(),
                                siteCookie = cookie.trim(),
                                siteUserId = userId.trim(),
                            ),
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1.6f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (editing == null) "保存并添加" else "保存修改")
                }
            }
        }
    }
}
