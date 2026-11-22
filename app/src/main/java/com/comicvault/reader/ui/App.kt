package com.comicvault.reader.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    // 簡單的返回堆疊：「畫面|參數1|參數2」
    var stack by rememberSaveable { mutableStateOf(listOf("library")) }
    val top = stack.last().split("|")
    val screen = top[0]
    fun go(vararg parts: Any) { stack = stack + parts.joinToString("|") }
    fun replaceTop(vararg parts: Any) { stack = stack.dropLast(1) + parts.joinToString("|") }
    var sheetOpen by remember { mutableStateOf(false) }
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.prepare(ImportSource.Docs(uris), importTarget)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.prepare(ImportSource.Tree(uri), importTarget)
    }
    val pickZips = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.prepare(ImportSource.Zips(uris), importTarget)
    }

    fun openImport(target: String?) {
        importTarget = target
        sheetOpen = true
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
        vm.refresh()
    }

    val session = vm.session
    if (session != null) {
        ImportScreen(vm, session, onCancel = { vm.cancelImport() })
    } else {
        BackHandler(enabled = screen != "library") { back() }
        when (screen) {
            "reader" -> {
                val comic = vm.comics.find { it.id == top.getOrNull(1) }
                if (comic == null) {
                    LaunchedEffect(Unit) { back() }
                } else {
                    val start = top.getOrNull(2)?.toIntOrNull() ?: -1
                    ReaderScreen(
                        comic = comic,
                        startPage = if (start >= 0) start else comic.lastPage,
                        onBack = { back() },
                        onPage = { vm.setLastPage(comic.id, it) },
                    )
                }
            }
            "detail" -> {
                val comic = vm.comics.find { it.id == top.getOrNull(1) }
                if (comic == null) {
                    LaunchedEffect(vm.comics) { if (vm.comics.isNotEmpty()) back() }
                } else {
                    DetailScreen(
                        vm = vm,
                        comic = comic,
                        onBack = { back() },
                        onRead = { page -> go("reader", comic.id, page) },
                        onAddChapters = { openImport(comic.id) },
                        onAuthor = { go("author", it) },
                        onTag = { tag ->
                            vm.searchQuery = tag
                            stack = listOf("library")
                        },
                    )
                }
            }
            "authors" -> AuthorsScreen(vm, onBack = { back() }, onAuthor = { go("author", it) })
            "author" -> {
                val name = top.drop(1).joinToString("|")
                AuthorScreen(
                    vm = vm,
                    author = name,
                    onBack = { back() },
                    onOpen = { go("detail", it) },
                    onRead = { go("reader", it, -1) },
                    onRenamed = { replaceTop("author", it) },
                )
            }
            "hidden" -> HiddenScanScreen(vm, onBack = { back() })
            "settings" -> SettingsScreen(vm, onBack = { back() })
            else -> LibraryScreen(
                vm = vm,
                onOpen = { go("detail", it) },
                onRead = { go("reader", it, -1) },
                onImport = { openImport(null) },
                onScanHidden = { go("hidden") },
                onSettings = { go("settings") },
                onAuthors = { go("authors") },
                onAuthor = { go("author", it) },
            )
        }
    }

    if (sheetOpen) {
        val appending = importTarget != null
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    if (appending) "新增章節" else "匯入漫畫",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SheetItem(
                    Icons.Outlined.Folder,
                    "選擇資料夾",
                    if (appending) "子資料夾會變成各話" else "可一次匯入多部；沒有圖片的資料夾會自動略過",
                ) {
                    sheetOpen = false
                    pickFolder.launch(null)
                }
                SheetItem(Icons.Outlined.Image, "選擇圖片", "可多選，依檔名自動排序") {
                    sheetOpen = false
                    pickImages.launch(arrayOf("image/*"))
                }
                SheetItem(Icons.Outlined.FolderZip, "匯入 ZIP / CBZ", "可一次選多個壓縮檔") {
                    sheetOpen = false
                    pickZips.launch(
                        arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/octet-stream")
                    )
                }
                if (!appending) {
                    SheetItem(Icons.Outlined.VisibilityOff, "掃描隱藏相簿", "找出相簿看不到的圖片資料夾") {
                        sheetOpen = false
                        go("hidden")
                    }
                }
            }
        }
    }

    vm.busyText?.let { BusyDialog(it, vm.busyProgress) }
}

@Composable
fun SheetItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun BusyDialog(text: String, progress: Float?) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text, style = MaterialTheme.typography.titleMedium)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}
