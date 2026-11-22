package com.comicvault.reader.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comicvault.reader.util.NaturalOrder

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf("library") }
    var readerId by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }

    fun goLibrary() {
        if (screen == "reader") vm.refresh()
        screen = "library"
    }

    BackHandler(enabled = screen != "library") { goLibrary() }

    when (screen) {
        "reader" -> {
            val comic = vm.comics.find { it.id == readerId }
            if (comic == null) {
                LaunchedEffect(Unit) { screen = "library" }
            } else {
                ReaderScreen(
                    comic = comic,
                    onBack = { goLibrary() },
                    onPage = { vm.setLastPage(comic.id, it) },
                )
            }
        }
        "hidden" -> HiddenScanScreen(vm, onBack = { goLibrary() })
        else -> LibraryScreen(
            vm = vm,
            onOpen = { readerId = it; screen = "reader" },
            onScanHidden = { screen = "hidden" },
        )
    }

    vm.pending?.let { p ->
        ImportDialog(
            pending = p,
            targetTitle = p.targetId?.let { id -> vm.comics.find { it.id == id }?.title },
            onConfirm = { title, del -> vm.confirmImport(title, del) },
            onDismiss = { vm.cancelPending() },
        )
    }

    vm.busyText?.let { BusyDialog(it, vm.busyProgress) }
}

@Composable
private fun ImportDialog(
    pending: PendingImport,
    targetTitle: String?,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(pending) { mutableStateOf(pending.prepared.title) }
    var deleteOriginals by remember(pending) { mutableStateOf(false) }
    val names = remember(pending) {
        pending.prepared.candidates.map { it.name }.sortedWith(NaturalOrder.fileNameComparator)
    }
    val canDeleteOriginals = pending.prepared.candidates.any { it.deleteOriginal != null } ||
        pending.prepared.deleteSource != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (targetTitle != null) "追加到「$targetTitle」" else "新增漫畫") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (targetTitle == null) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("漫畫名稱") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("找到 ${names.size} 個檔案，已依檔名自動排序：", style = MaterialTheme.typography.bodyMedium)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp).heightIn(max = 180.dp)) {
                        val shown = if (names.size <= 8) names else names.take(6) + listOf("⋯") + names.takeLast(1)
                        shown.forEachIndexed { i, n ->
                            val idx = when {
                                n == "⋯" -> ""
                                names.size > 8 && i == shown.lastIndex -> "${names.size}. "
                                else -> "${i + 1}. "
                            }
                            Text(
                                idx + n,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (canDeleteOriginals) {
                    Row(
                        Modifier.fillMaxWidth().clickable { deleteOriginals = !deleteOriginals },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteOriginals, onCheckedChange = { deleteOriginals = it })
                        Column {
                            Text("匯入後刪除原始檔")
                            Text(
                                "勾選後手機相簿就看不到這些圖片，只能在本 App 裡看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "圖片會複製到 App 的私有空間，系統相簿與其他 App 都讀不到。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), deleteOriginals) }) { Text("匯入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
