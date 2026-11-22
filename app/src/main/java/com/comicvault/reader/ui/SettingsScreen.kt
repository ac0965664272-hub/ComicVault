package com.comicvault.reader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.comicvault.reader.data.Category
import com.comicvault.reader.data.HiddenScanner
import com.comicvault.reader.data.Importer
import com.comicvault.reader.data.StorageKind
import com.comicvault.reader.data.StorageLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var moveAllTo by remember { mutableStateOf<StorageLocation?>(null) }
    var needPermission by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val f = Importer.treeUriToFile(uri)
            if (f == null) vm.message = "無法使用這個位置，請從「內部儲存空間」或 SD 卡選擇資料夾"
            else vm.addCustomRoot(f)
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionTitle("存放位置") }
            item {
                Text(
                    "點選新漫畫要存在哪裡；「全部搬到這裡」可一鍵把所有漫畫搬過去（包含 SD 卡）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(vm.locations, key = { it.path }) { loc ->
                val isDefault = loc.path == vm.defaultRoot
                val count = vm.comics.count { it.root.absolutePath == loc.path }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                when {
                                    loc.kind == StorageKind.Internal -> Icons.Outlined.Lock
                                    loc.removable -> Icons.Outlined.SdCard
                                    loc.kind == StorageKind.Custom -> Icons.Outlined.Folder
                                    else -> Icons.Outlined.PhoneAndroid
                                },
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(loc.label) },
                        supportingContent = {
                            Text("${loc.detail}\n$count 部漫畫 · 剩餘 ${formatBytes(loc.freeBytes)}")
                        },
                        trailingContent = {
                            RadioButton(selected = isDefault, onClick = { vm.setDefaultRoot(loc) })
                        },
                        modifier = Modifier.clickable { vm.setDefaultRoot(loc) },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (loc.kind == StorageKind.Custom && count == 0) {
                            TextButton(onClick = { vm.removeCustomRoot(loc) }) { Text("移除") }
                        }
                        val others = vm.comics.count { it.root.absolutePath != loc.path }
                        OutlinedButton(onClick = { moveAllTo = loc }, enabled = others > 0) {
                            Text("全部搬到這裡（$others 部）")
                        }
                    }
                }
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.CreateNewFolder, null) },
                    headlineContent = { Text("新增自訂資料夾") },
                    supportingContent = { Text("可選手機或 SD 卡上的任何資料夾，解除安裝 App 後漫畫仍會保留") },
                    modifier = Modifier.clickable {
                        if (HiddenScanner.hasAccess(ctx)) pickFolder.launch(null) else needPermission = true
                    },
                )
                HorizontalDivider()
            }

            item { SectionTitle("類別") }
            items(vm.categories, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text("${vm.comics.count { it.categoryId == c.id }} 部") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { renaming = c }) { Icon(Icons.Outlined.Edit, "重新命名") }
                            IconButton(onClick = { deleting = c }) { Icon(Icons.Outlined.Delete, "刪除") }
                        }
                    },
                )
            }
            item {
                TextButton(onClick = { newCategory = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("＋ 新增類別")
                }
            }
        }
    }

    moveAllTo?.let { loc ->
        AlertDialog(
            onDismissRequest = { moveAllTo = null },
            title = { Text("全部搬到「${loc.label}」？") },
            text = {
                Text(
                    "會把其他位置的 ${vm.comics.count { it.root.absolutePath != loc.path }} 部漫畫搬過來，" +
                        "跨磁碟（例如 SD 卡）需要複製，可能要花一點時間。之後新匯入的漫畫也會存到這裡。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDefaultRoot(loc)
                    vm.moveAll(loc)
                    moveAllTo = null
                }) { Text("開始搬遷") }
            },
            dismissButton = { TextButton(onClick = { moveAllTo = null }) { Text("取消") } },
        )
    }

    if (needPermission) {
        AlertDialog(
            onDismissRequest = { needPermission = false },
            title = { Text("需要所有檔案存取權") },
            text = { Text("要把漫畫存到自訂資料夾（含 SD 卡），App 需要「所有檔案存取權」。授權後回到這裡再點一次「新增自訂資料夾」。") },
            confirmButton = {
                TextButton(onClick = {
                    needPermission = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { ctx.startActivity(HiddenScanner.accessIntent(ctx)) }
                            .onFailure { runCatching { ctx.startActivity(HiddenScanner.fallbackAccessIntent()) } }
                    } else {
                        legacyPermission.launch(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        )
                    }
                }) { Text("前往授權") }
            },
            dismissButton = { TextButton(onClick = { needPermission = false }) { Text("取消") } },
        )
    }

    if (newCategory) {
        TextInputDialog("新增類別", "", onDismiss = { newCategory = false }) {
            if (it.isNotBlank()) vm.addCategory(it)
            newCategory = false
        }
    }
    renaming?.let { c ->
        TextInputDialog("重新命名類別", c.name, onDismiss = { renaming = null }) {
            vm.renameCategory(c.id, it)
            renaming = null
        }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除類別「${c.name}」？") },
            text = { Text("漫畫不會被刪除，只會變成「未分類」。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(c.id); deleting = null }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
