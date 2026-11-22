package com.comicvault.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comicvault.reader.data.Comic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (String) -> Unit, onScanHidden: () -> Unit) {
    // 追加圖片時的目標漫畫；null = 新增一本
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Comic?>(null) }
    var renameFor by remember { mutableStateOf<Comic?>(null) }
    var deleteFor by remember { mutableStateOf<Comic?>(null) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.prepare(ImportSource.Docs(uris), target)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.prepare(ImportSource.Tree(uri), target)
    }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepare(ImportSource.Zip(uri), target)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的漫畫") },
                actions = {
                    IconButton(onClick = onScanHidden) {
                        Icon(Icons.Outlined.VisibilityOff, contentDescription = "掃描隱藏相簿")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { target = null; showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增漫畫") },
            )
        },
    ) { pad ->
        if (vm.comics.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "還沒有漫畫\n點右下角「新增漫畫」匯入圖片",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.comics, key = { it.id }) { c ->
                    ComicCard(c, onClick = { onOpen(c.id) }, onLongClick = { menuFor = c })
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    if (target == null) "新增漫畫" else "追加圖片",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SheetItem(Icons.Outlined.Image, "選擇圖片", "可多選，會依檔名自動排序") {
                    showAdd = false
                    pickImages.launch(arrayOf("image/*"))
                }
                SheetItem(Icons.Outlined.Folder, "選擇整個資料夾", "可選隱藏資料夾；子資料夾（第1話、第2話…）也會依名稱排好") {
                    showAdd = false
                    pickFolder.launch(null)
                }
                SheetItem(Icons.Outlined.FolderZip, "匯入 ZIP / CBZ 壓縮檔", "支援中文檔名（UTF-8 / Big5 / GBK）") {
                    showAdd = false
                    pickZip.launch(
                        arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/octet-stream")
                    )
                }
                if (target == null) {
                    SheetItem(Icons.Outlined.VisibilityOff, "掃描隱藏相簿", "找出相簿看不到的圖片資料夾（.nomedia、. 開頭）") {
                        showAdd = false
                        onScanHidden()
                    }
                }
            }
        }
    }

    menuFor?.let { c ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    SheetItem(Icons.Outlined.AutoStories, "閱讀", null) { menuFor = null; onOpen(c.id) }
                    SheetItem(Icons.Outlined.PlaylistAdd, "追加圖片", "新頁面會和原有頁面一起依檔名排序") {
                        menuFor = null; target = c.id; showAdd = true
                    }
                    SheetItem(Icons.Outlined.DriveFileRenameOutline, "重新命名", null) { menuFor = null; renameFor = c }
                    SheetItem(Icons.Outlined.Delete, "刪除", null) { menuFor = null; deleteFor = c }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("關閉") } },
        )
    }

    renameFor?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("重新命名") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.rename(c.id, name.trim()); renameFor = null }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("取消") } },
        )
    }

    deleteFor?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("刪除「${c.title}」？") },
            text = { Text("這本漫畫的 ${c.pages.size} 頁圖片會從 App 中永久刪除，無法復原。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c.id); deleteFor = null }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.combinedClickableCompat(onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null) =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
private fun ComicCard(c: Comic, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.combinedClickableCompat(onClick, onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = c.cover,
                contentDescription = c.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (c.lastPage > 0 && c.pages.size > 1) {
                LinearProgressIndicator(
                    progress = { (c.lastPage + 1f) / c.pages.size },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            buildString {
                append("${c.pages.size} 頁")
                if (c.lastPage > 0) append(" · 讀到 ${c.lastPage + 1}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
