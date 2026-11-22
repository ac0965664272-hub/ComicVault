package com.comicvault.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comicvault.reader.data.MAX_CHAPTERS
import com.comicvault.reader.data.PlannedComic
import java.io.File

/** 匯入前確認：列出偵測到的漫畫（只有含圖片的資料夾），可勾選、改名、設定類別與存放位置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: MainViewModel, session: ImportSession, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    val plans = session.plans
    val target = session.targetId?.let { id -> vm.comics.find { it.id == id } }
    // 預設全選；用 key 記錄「取消勾選」的項目
    val unchecked = remember(session) { mutableStateMapOf<String, Boolean>() }
    val titles = remember(session) { mutableStateMapOf<String, String>() }
    val authors = remember(session) { mutableStateMapOf<String, String>() }
    var category by remember(session) { mutableStateOf<String?>(null) }
    var rootPath by remember(session) { mutableStateOf(vm.defaultRoot) }
    var deleteOriginals by remember(session) { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var locMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedComic?>(null) }

    val selected = plans.filter { unchecked[it.key] != true }
    val newChapters = selected.sumOf { it.chapters.size }
    val room = if (target != null) MAX_CHAPTERS - target.chapters.size else MAX_CHAPTERS
    val overLimit = target != null && newChapters > room
    val canDelete = session.sources.any { s -> s.deleteSource != null || s.images.any { it.deleteOriginal != null } }
    val location = vm.locations.find { it.path == rootPath } ?: vm.locations.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "取消") } },
                title = {
                    Text(
                        if (target != null) "新增章節到「${target.title}」" else "找到 ${plans.size} 部漫畫",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (overLimit) {
                        Text(
                            "「${target?.title}」已有 ${target?.chapters?.size} 話，最多只能再加 $room 話，目前選了 $newChapters 話。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            vm.confirmImport(selected, titles.toMap(), authors.toMap(), category, File(location?.path ?: vm.defaultRoot), deleteOriginals)
                        },
                        enabled = selected.isNotEmpty() && !overLimit && location != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (target != null) "新增 $newChapters 話"
                            else "匯入 ${selected.size} 部（${selected.sumOf { it.imageCount }} 張）"
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "已自動略過沒有圖片的資料夾，圖片依檔名排序。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingRow(
                        title = "子資料夾當成同一部的各話",
                        subtitle = "例如「海賊王/第1話」「海賊王/第2話」→ 一部漫畫的兩話",
                    ) {
                        Switch(checked = session.chapterMode, onCheckedChange = { session.chapterMode = it })
                    }
                    if (target == null) {
                        Box {
                            SettingRow("類別", vm.categories.find { it.id == category }?.name ?: "未分類", onClick = { catMenu = true }) {}
                            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                DropdownMenuItem(text = { Text("未分類") }, onClick = { category = null; catMenu = false })
                                vm.categories.forEach { c ->
                                    DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c.id; catMenu = false })
                                }
                            }
                        }
                        Box {
                            SettingRow(
                                "存放位置",
                                (location?.label ?: "—") + location?.let { "（剩 ${formatBytes(it.freeBytes)}）" }.orEmpty(),
                                onClick = { locMenu = true },
                            ) {}
                            DropdownMenu(expanded = locMenu, onDismissRequest = { locMenu = false }) {
                                vm.locations.forEach { l ->
                                    DropdownMenuItem(
                                        text = { Text(l.label) },
                                        onClick = { rootPath = l.path; locMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    if (canDelete) {
                        SettingRow(
                            "匯入後刪除原始檔",
                            "相簿就看不到這些圖片，只存在 App 裡",
                            onClick = { deleteOriginals = !deleteOriginals },
                        ) {
                            Checkbox(checked = deleteOriginals, onCheckedChange = { deleteOriginals = it })
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { unchecked.clear() }) { Text("全選") }
                    TextButton(onClick = { plans.forEach { unchecked[it.key] = true } }) { Text("全不選") }
                }
            }
            items(plans, key = { it.key }) { p ->
                val checked = unchecked[p.key] != true
                val title = titles[p.key] ?: p.title
                val author = authors[p.key] ?: p.author
                ListItem(
                    overlineContent = if (target == null) {
                        { Text(if (author.isBlank()) "作者：未設定（點右邊鉛筆可填）" else "作者：$author") }
                    } else {
                        null
                    },
                    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val chapterInfo = if (p.chapters.size > 1) {
                            "${p.chapters.size} 話：" + p.chapters.joinToString("、") { it.name.ifBlank { "—" } }
                        } else {
                            "1 話"
                        }
                        Text("${p.imageCount} 張 · $chapterInfo", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { unchecked[p.key] = !it })
                            AsyncImage(
                                model = p.preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp, 60.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    },
                    trailingContent = if (target == null) {
                        { IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, "修改名稱與作者") } }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { unchecked[p.key] = checked },
                )
            }
        }
    }

    editing?.let { p ->
        TitleAuthorDialog(
            initialTitle = titles[p.key] ?: p.title,
            initialAuthor = authors[p.key] ?: p.author,
            onDismiss = { editing = null },
        ) { t, a ->
            if (t.isNotBlank()) titles[p.key] = t
            authors[p.key] = a
            editing = null
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun TitleAuthorDialog(
    initialTitle: String,
    initialAuthor: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var t by remember { mutableStateOf(initialTitle) }
    var a by remember { mutableStateOf(initialAuthor) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名稱與作者") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = t, onValueChange = { t = it }, label = { Text("漫畫名稱") }, singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = a, onValueChange = { a = it }, label = { Text("作者（多位用「、」分隔）") }, singleLine = true,
                )
                Text(
                    "作者名字會自動加成標籤。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(t.trim(), a.trim()) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
