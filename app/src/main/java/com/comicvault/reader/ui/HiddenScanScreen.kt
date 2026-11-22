package com.comicvault.reader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.comicvault.reader.data.HiddenScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenScanScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(HiddenScanner.hasAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = HiddenScanner.hasAccess(ctx) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = HiddenScanner.hasAccess(ctx)
    }
    val picked = remember { mutableStateListOf<String>() }
    LaunchedEffect(granted, vm.hiddenFolders == null) {
        if (granted && vm.hiddenFolders == null) vm.scanHidden()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隱藏相簿") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (granted) {
                        IconButton(onClick = { vm.scanHidden() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新掃描")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (picked.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            vm.prepare(ImportSource.Dirs(picked.map { File(it) }), null, fromHidden = true)
                            picked.clear()
                        },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text("匯入選取的 ${picked.size} 個資料夾") }
                }
            }
        },
    ) { pad ->
        val folders = vm.hiddenFolders
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                !granted -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("需要「所有檔案存取權」", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "相簿裡的「隱藏」通常是把照片搬進 . 開頭的資料夾，或放一個 .nomedia 讓相簿略過。" +
                            "要找出這些資料夾，App 需要讀取整個儲存空間的權限。\n\n" +
                            "不想開權限的話，也可以在「新增漫畫 → 選擇整個資料夾」裡手動選隱藏資料夾" +
                            "（在選擇器右上角選單開啟「顯示隱藏檔案」）。"
                    )
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching { ctx.startActivity(HiddenScanner.accessIntent(ctx)) }
                                .onFailure { runCatching { ctx.startActivity(HiddenScanner.fallbackAccessIntent()) } }
                        } else {
                            legacyPermission.launch(
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            )
                        }
                    }) { Text("前往授權") }
                }

                vm.scanStatus != null || folders == null -> Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        vm.scanStatus ?: "準備掃描…",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                folders.isEmpty() -> Text(
                    "沒有找到隱藏的圖片資料夾。\n\n注意：有加密的保險箱（例如三星安全資料夾、部分品牌的加密隱藏相簿）其他 App 無法讀取，需要先在原本的相簿 App 裡「取消隱藏」。",
                    modifier = Modifier.padding(24.dp),
                )

                else -> LazyColumn {
                    item {
                        Text(
                            "找到 ${folders.size} 個相簿看不到、而且有圖片的資料夾。勾選後按下方按鈕，可一次匯入多部：",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(folders, key = { it.dir.absolutePath }) { f ->
                        ListItem(
                            headlineContent = { Text(f.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${f.imageCount} 張 · ${f.reason}") },
                            leadingContent = {
                                val path = f.dir.absolutePath
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = path in picked,
                                    onCheckedChange = { if (it) picked.add(path) else picked.remove(path) },
                                )
                                AsyncImage(
                                    model = f.preview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                }
                            },
                            modifier = Modifier.clickable {
                                val path = f.dir.absolutePath
                                if (path in picked) picked.remove(path) else picked.add(path)
                            },
                        )
                    }
                }
            }
        }
    }
}
