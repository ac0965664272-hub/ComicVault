package com.comicvault.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.InputChip
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.comicvault.reader.data.Comic
import com.comicvault.reader.data.MAX_CHAPTERS
import com.comicvault.reader.data.chapterLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    comic: Comic,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onAddChapters: () -> Unit,
    onAuthor: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    var editingAuthor by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(-1) }
    var renameChapter by remember { mutableStateOf(-1) }
    var deleteChapter by remember { mutableStateOf(-1) }

    val category = vm.categories.find { it.id == comic.categoryId }?.name ?: "未分類"
    val location = vm.locationOf(comic)?.label ?: "未知位置"
    val curChapter = comic.chapterOf(comic.lastPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("設定類別") }, onClick = { menu = false; picking = true })
                        DropdownMenuItem(text = { Text("搬遷到…") }, onClick = { menu = false; moving = true })
                        DropdownMenuItem(text = { Text("刪除整部漫畫") }, onClick = { menu = false; deleting = true })
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(Modifier.padding(16.dp)) {
                    AsyncImage(
                        model = comic.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(110.dp)
                            .height(156.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(comic.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${comic.chapters.size} 話 · ${comic.pages.size} 頁", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("作者：", style = MaterialTheme.typography.bodyMedium)
                            if (comic.authors.isEmpty()) {
                                Text(
                                    "未設定",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { editingAuthor = true },
                                )
                            } else {
                                comic.authors.forEachIndexed { i, a ->
                                    if (i > 0) Text("、", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        a,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onAuthor(a) },
                                    )
                                }
                            }
                            IconButton(onClick = { editingAuthor = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Edit, "設定作者", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("類別：$category", style = MaterialTheme.typography.bodySmall)
                        Text("存放：$location", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onRead(comic.lastPage) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Text(
                            if (comic.lastPage > 0) " 繼續 ${chapterLabel(curChapter)}" else " 開始閱讀",
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(onClick = { onRead(0) }) { Text("從頭") }
                }
            }
            item {
                Text(
                    "標籤",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    comic.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { onTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close, "移除標籤",
                                    modifier = Modifier.size(16.dp).clickable { vm.removeTag(comic.id, tag) },
                                )
                            },
                        )
                    }
                    AssistChip(onClick = { addingTag = true }, label = { Text("＋ 標籤") })
                }
                Text(
                    "點標籤可搜尋同標籤的漫畫",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "章節（${comic.chapters.size}/$MAX_CHAPTERS）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAddChapters, enabled = comic.chapters.size < MAX_CHAPTERS) {
                        Icon(Icons.Outlined.Add, null)
                        Text("新增一話")
                    }
                }
                HorizontalDivider()
            }
            itemsIndexed(comic.chapters) { i, ch ->
                val label = chapterLabel(i)
                val sub = ch.name.takeIf { it.isNotBlank() && it != label }
                ListItem(
                    headlineContent = { Text(label + if (i == curChapter && comic.lastPage > 0) "  · 讀到這裡" else "") },
                    supportingContent = {
                        Text(
                            listOfNotNull(sub, "${ch.pages.size} 頁").joinToString(" · "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = ch.pages.firstOrNull()?.let { comic.fileOf(it) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp, 60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { vm.moveChapter(comic.id, i, i - 1) },
                                enabled = i > 0,
                            ) { Icon(Icons.Filled.KeyboardArrowUp, "上移") }
                            IconButton(
                                onClick = { vm.moveChapter(comic.id, i, i + 1) },
                                enabled = i < comic.chapters.lastIndex,
                            ) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                            Box {
                                IconButton(onClick = { chapterMenu = i }) { Icon(Icons.Outlined.MoreVert, "更多") }
                                DropdownMenu(expanded = chapterMenu == i, onDismissRequest = { chapterMenu = -1 }) {
                                    DropdownMenuItem(
                                        text = { Text("修改副標題") },
                                        onClick = { chapterMenu = -1; renameChapter = i },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("刪除這一話") },
                                        onClick = { chapterMenu = -1; deleteChapter = i },
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { onRead(comic.chapterStarts[i]) },
                )
            }
        }
    }

    if (editingAuthor) {
        TextInputDialog("作者（多位用「、」分隔）", comic.author, onDismiss = { editingAuthor = false }) {
            vm.setAuthor(listOf(comic.id), it)
            editingAuthor = false
        }
    }
    if (addingTag) {
        TextInputDialog("新增標籤（多個用空白或逗號分隔）", "", onDismiss = { addingTag = false }) {
            vm.addTags(listOf(comic.id), it.split(Regex("[\\s,，、]+")))
            addingTag = false
        }
    }
    if (renaming) {
        TextInputDialog("重新命名", comic.title, onDismiss = { renaming = false }) {
            vm.rename(comic.id, it)
            renaming = false
        }
    }
    if (picking) {
        CategoryPickerDialog(
            categories = vm.categories,
            current = comic.categoryId,
            onDismiss = { picking = false },
            onPick = { vm.setCategory(listOf(comic.id), it); picking = false },
            onCreate = { vm.addCategory(it, listOf(comic.id)); picking = false },
        )
    }
    if (moving) {
        LocationPickerDialog(
            title = "搬遷到…",
            locations = vm.locations,
            currentPaths = setOf(comic.root.absolutePath),
            onDismiss = { moving = false },
            onPick = { vm.move(listOf(comic.id), it); moving = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("刪除「${comic.title}」？") },
            text = { Text("全部 ${comic.chapters.size} 話、${comic.pages.size} 頁會永久刪除，無法復原。") },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.delete(listOf(comic.id)); onBack() }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
    }
    if (renameChapter in comic.chapters.indices) {
        val idx = renameChapter
        TextInputDialog("${chapterLabel(idx)} 的副標題", comic.chapters[idx].name, onDismiss = { renameChapter = -1 }) {
            vm.renameChapter(comic.id, idx, it)
            renameChapter = -1
        }
    }
    if (deleteChapter in comic.chapters.indices) {
        val idx = deleteChapter
        AlertDialog(
            onDismissRequest = { deleteChapter = -1 },
            title = { Text("刪除${chapterLabel(idx)}？") },
            text = { Text("這一話的 ${comic.chapters[idx].pages.size} 頁會永久刪除，後面的章節會往前遞補。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteChapter = -1
                    if (comic.chapters.size == 1) {
                        vm.delete(listOf(comic.id)); onBack()
                    } else {
                        vm.deleteChapter(comic.id, idx)
                    }
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteChapter = -1 }) { Text("取消") } },
        )
    }
}
