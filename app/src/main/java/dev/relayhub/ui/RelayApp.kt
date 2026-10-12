package dev.relayhub.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.relayhub.data.Endpoint
import dev.relayhub.ui.comp.ConfirmDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayApp(vm: RelayViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val themePrefs by vm.theme.collectAsStateWithLifecycle()
    val maskKeys by vm.maskKeys.collectAsStateWithLifecycle()
    val autoClear by vm.autoClearClipboard.collectAsStateWithLifecycle()
    val revealed by vm.revealedIds.collectAsStateWithLifecycle()
    val ready by vm.isReady.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    var editing: Endpoint? by remember { mutableStateOf(null) }
    var showEdit by remember { mutableStateOf(false) }
    var openDetail: Endpoint? by remember { mutableStateOf(null) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<Long>?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var tokenUrl by remember { mutableStateOf<String?>(null) }
    var tokenSink by remember { mutableStateOf<((String, Boolean, String) -> Unit)?>(null) }
    // 启动遮罩是否已经退场（只在本次进程内显示一次）
    var splashDone by remember { mutableStateOf(false) }
    // 首页卡片默认收起，点击展开看详情
    var expandedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 从 Download 等位置选择备份文件导入
    val openDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importFromUri(uri) { msg -> toast(msg) } }

    val selectionMode = ui.selectedIds.isNotEmpty()

    // 多选时拦截返回手势：先退出多选；面板打开时交给面板自己处理
    BackHandler(
        enabled = selectionMode && !showEdit && openDetail == null &&
            !showSettings && tokenUrl == null && confirmDelete == null,
    ) {
        vm.clearSelection()
    }

    // 详情页跟随列表数据实时刷新（测活结果、收藏状态等）
    LaunchedEffect(openDetail?.id, ui.items) {
        val id = openDetail?.id ?: return@LaunchedEffect
        val fresh = ui.items.firstOrNull { it.id == id }
        if (fresh != null && fresh != openDetail) openDetail = fresh
        if (fresh == null) openDetail = null
    }

    fun openUrl(url: String) {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed))) }
            .onFailure { toast("没有可打开该链接的应用") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // 启动遮罩：首页数据就绪后淡出
    if (!splashDone) {
        dev.relayhub.ui.comp.LaunchOverlay(
            ready = ready,
            onFinished = { splashDone = true },
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = ui.selectedIds.size,
                    total = ui.items.size,
                    allSelected = ui.items.isNotEmpty() &&
                        ui.selectedIds.containsAll(ui.items.map { it.id }),
                    onClose = { vm.clearSelection() },
                    onToggleAll = { vm.toggleSelectAll() },
                    onMenuSelect = { vm.selectBy(it) },
                    onCopy = {
                        vm.copySelectedAsJson(emptyList())
                        toast("已复制 ${ui.selectedIds.size} 条配置到剪贴板")
                    },
                    onCheck = { vm.checkAlive(ui.items.filter { it.id in ui.selectedIds }) },
                    onBalance = {
                        vm.refreshBalance(ui.items.filter { it.id in ui.selectedIds }) { msg -> toast(msg) }
                    },
                    onDelete = { confirmDelete = ui.selectedIds.toList() },
                )
            } else {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "中转站管理",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                buildString {
                                    append("${ui.total} 个站点")
                                    if (ui.reachable > 0) append(" · ${ui.reachable} 可达")
                                    if (ui.unreachable > 0) append(" · ${ui.unreachable} 异常")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.checkAll() }) {
                            Icon(Icons.Outlined.Bolt, contentDescription = "全部测活")
                        }
                        IconButton(onClick = {
                            vm.refreshAllBalances { msg -> toast(msg) }
                        }) {
                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "查询全部余额")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; showEdit = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("添加") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchAndSort(
                query = ui.query,
                onQuery = vm::setQuery,
                sort = ui.sort,
                onSort = vm::setSort,
                stats = ui,
                onStatsClick = { showStats = !showStats },
            )

            if (showStats && ui.total > 0) {
                StatsBar(
                    total = ui.total,
                    reachable = ui.reachable,
                    unreachable = ui.unreachable,
                    unchecked = ui.unchecked,
                )
            }

            if (!ready) {
                // 启动遮罩还盖着的时候不用显示占位，减一层闪烁
                if (splashDone) ListLoadingPlaceholder()
            } else if (ui.items.isEmpty()) {
                EndpointListEmpty(isSearching = ui.query.isNotBlank()) {
                    editing = null
                    showEdit = true
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = ui.items,
                        key = { it.id },
                    ) { item ->
                        EndpointCard(
                            modifier = Modifier.animateItem(),
                            item = item,
                            selected = item.id in ui.selectedIds,
                            selectionMode = selectionMode,
                            checking = item.id in ui.checking,
                            maskKey = maskKeys,
                            showKey = item.id in revealed,
                            expanded = item.id in expandedIds,
                            onToggleExpand = {
                                expandedIds = if (item.id in expandedIds) {
                                    expandedIds - item.id
                                } else {
                                    expandedIds + item.id
                                }
                            },
                            onOpen = { openDetail = item },
                            onToggleSelect = { vm.toggleSelect(item.id) },
                            onEdit = { editing = item; showEdit = true },
                            onCopyKey = {
                                if (item.apiKey.isBlank()) toast("这条还没有密钥") else {
                                    vm.copy("API Key", item.apiKey, sensitive = true)
                                    toast("已复制 ${item.name} 的 Key")
                                }
                            },
                            onCopyUrl = {
                                vm.copy("接口地址", item.url)
                                toast("已复制接口地址")
                            },
                            onCopyAll = {
                                vm.copyAll(item)
                                toast("已复制完整配置")
                            },
                            onOpenUrl = { openUrl(item.panelUrl) },
                            onToggleKeyVisible = { vm.toggleReveal(item.id) },
                            onToggleFavorite = { vm.toggleFavorite(item) },
                            onCheck = { vm.checkAlive(listOf(item)) },
                            onDuplicate = { vm.duplicate(item); toast("已创建副本") },
                            onDelete = { confirmDelete = listOf(item.id) },
                            balancing = item.id in ui.balancing,
                            onRefreshBalance = {
                                vm.refreshBalance(listOf(item)) { msg -> toast(msg) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        EditSheet(
            editing = editing,
            readClipboard = { Clip.read(context) },
            onDismiss = { showEdit = false },
            onMessage = { toast(it) },
            onFetchCredential = { address, sink ->
                // 用编辑框里当前的地址（新增时也生效），并统一去掉 /v1 之类的版本段
                val root = dev.relayhub.data.BalanceApi.rootOf(address)
                if (root.isBlank()) {
                    toast("先填好接口地址，再获取登录凭证")
                } else {
                    tokenSink = sink
                    // 智谱的登录令牌在控制台页面里，API 域名下没有，改开控制台
                    tokenUrl = if (root.contains("bigmodel.cn")) {
                        "https://open.bigmodel.cn/login"
                    } else {
                        root
                    }
                }
            },
            onSave = { draft ->
                vm.save(draft) {}
                showEdit = false
                toast(if (draft.id == 0L) "已添加「${draft.name}」" else "已保存修改")
            },
        )
    }

    openDetail?.let { detail ->
        DetailSheet(
            item = detail,
            maskKey = maskKeys,
            checking = detail.id in ui.checking,
            onDismiss = { openDetail = null },
            onEdit = { editing = detail; openDetail = null; showEdit = true },
            onToggleFavorite = { vm.toggleFavorite(detail) },
            onToggleMask = { vm.setMaskKeys(!maskKeys) },
            onCopy = { label, text, sensitive -> vm.copy(label, text, sensitive); toast("已复制$label") },
            onOpenUrl = { openUrl(detail.panelUrl) },
            onCheck = { vm.checkAlive(listOf(detail)) },
            onDuplicate = { vm.duplicate(detail); openDetail = null; toast("已创建副本") },
            onCopyAll = { vm.copyAll(detail); toast("已复制完整配置") },
            onDelete = { confirmDelete = listOf(detail.id); openDetail = null },
        )
    }

    tokenUrl?.let { url ->
        TokenFetcherSheet(
            startUrl = url,
            onDismiss = { tokenUrl = null; tokenSink = null },
            onToken = { value, isCookie, userId ->
                tokenSink?.invoke(value, isCookie, userId)
                tokenUrl = null
                tokenSink = null
                toast(
                    when {
                        isCookie && userId.isNotBlank() -> "已获取登录 Cookie 和账户 id，保存后即可查余额"
                        isCookie -> "已获取登录 Cookie，保存后即可查余额"
                        else -> "已获取令牌，保存后即可查余额"
                    },
                )
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            prefs = themePrefs,
            maskKeys = maskKeys,
            autoClearClipboard = autoClear,
            onDismiss = { showSettings = false },
            onThemeMode = vm::setThemeMode,
            onColorSource = vm::setColorSource,
            onAccent = vm::setAccent,
            onStyle = vm::setStyle,
            onPureBlack = vm::setPureBlack,
            onMaskKeys = vm::setMaskKeys,
            onAutoClearClipboard = vm::setAutoClearClipboard,
            onBackup = {
                val path = vm.backupToDownload()
                toast(if (path != null) "已导出到 $path" else "导出失败")
            },
            onImportFile = { openDoc.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onImport = { vm.importFromClipboard { msg -> toast(msg) } },
        )
    }

    confirmDelete?.let { ids ->
        val names = ui.items.filter { it.id in ids }.joinToString("、") { it.name }.take(60)
        ConfirmDialog(
            title = if (ids.size == 1) "删除这条记录？" else "删除 ${ids.size} 条记录？",
            message = if (ids.size == 1) "「$names」将被永久删除，密钥与备注会一并移除。"
            else "所选记录将被永久删除，密钥与备注会一并移除。",
            onConfirm = {
                vm.delete(ids)
                toast("已删除 ${ids.size} 条")
            },
            onDismiss = { confirmDelete = null },
        )
    }
    }
}

@Composable
private fun SearchAndSort(
    query: String,
    onQuery: (String) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    stats: HomeUiState,
    onStatsClick: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索名称、地址、备注、标签") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        Box {
            Surface(
                onClick = { sortMenu = true },
                shape = MaterialTheme.shapes.large,
                color = if (stats.query.isBlank() && stats.total > 0)
                    MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(
                    Icons.Outlined.Sort,
                    contentDescription = "排序",
                    modifier = Modifier.padding(14.dp).size(22.dp),
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = { onSort(mode); sortMenu = false },
                        leadingIcon = if (mode == sort) {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsBar(total: Int, reachable: Int, unreachable: Int, unchecked: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip("共 $total", MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
        StatChip("$reachable 可达", MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f))
        StatChip("$unreachable 异常", MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f))
        StatChip("$unchecked 未测", MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    total: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onMenuSelect: ((Endpoint) -> Boolean) -> Unit,
    onCopy: () -> Unit,
    onCheck: () -> Unit,
    onBalance: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { it / 2 } + fadeIn(tween(160))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(120)))
                        } else {
                            (slideInVertically { -it / 2 } + fadeIn(tween(160))) togetherWith
                                (slideOutVertically { it / 2 } + fadeOut(tween(120)))
                        }
                    },
                    label = "selCount",
                ) { value ->
                    Text(
                        "已选择 $value / $total",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    if (count == 0) "长按任意卡片进入多选" else "点卡片继续增减，返回手势退出多选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "退出多选")
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    // 全选状态切换时给个轻微的形变，提示状态已变
                    val scale by animateFloatAsState(
                        targetValue = if (allSelected) 1f else 0.86f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                        label = "allScale",
                    )
                    Icon(
                        Icons.Outlined.DoneAll,
                        contentDescription = "选择",
                        modifier = Modifier.scale(scale),
                        tint = if (allSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = {
                            AnimatedContent(
                                targetState = allSelected,
                                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                                label = "allLabel",
                            ) { all ->
                                Text(if (all) "取消全选" else "全选")
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (allSelected) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                                contentDescription = null,
                            )
                        },
                        onClick = { menu = false; onToggleAll() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("选中常用站点") },
                        onClick = { menu = false; onMenuSelect { it.favorite } },
                    )
                    DropdownMenuItem(
                        text = { Text("选中已填写密钥的") },
                        onClick = { menu = false; onMenuSelect { it.apiKey.isNotBlank() } },
                    )
                    DropdownMenuItem(
                        text = { Text("选中未测活的") },
                        onClick = { menu = false; onMenuSelect { it.lastCheckAt <= 0L } },
                    )
                    DropdownMenuItem(
                        text = { Text("选中异常的") },
                        onClick = { menu = false; onMenuSelect { it.lastCheckOk == false } },
                    )
                    DropdownMenuItem(
                        text = { Text("选中没有密钥的") },
                        onClick = { menu = false; onMenuSelect { it.apiKey.isBlank() } },
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.Share, contentDescription = "导出到剪贴板")
            }
            IconButton(onClick = onCheck) {
                Icon(Icons.Outlined.Bolt, contentDescription = "批量测活")
            }
            IconButton(onClick = onBalance) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "批量查余额")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "批量删除", tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
