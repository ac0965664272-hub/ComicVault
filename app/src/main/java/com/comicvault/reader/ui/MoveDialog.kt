package com.comicvault.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comicvault.reader.data.StorageLocation

/**
 * 搬遷：先選目的地，再勾要搬的漫畫。
 * 已經在目的地的漫畫會變灰、不能勾選。搬完會刪除原位置的檔案。
 */
@Composable
fun MoveDialog(
    vm: MainViewModel,
    ids: List<String>,
    initialTarget: StorageLocation? = null,
    onDismiss: () -> Unit,
) {
    val comics = ids.mapNotNull { id -> vm.comics.find { it.id == id } }
    val locations = vm.locations
    var targetPath by remember {
        mutableStateOf(
            initialTarget?.path
                ?: locations.firstOrNull { loc -> comics.any { it.root.absolutePath != loc.path } }?.path
                ?: locations.firstOrNull()?.path
        )
    }
    // 使用者手動取消勾選的
    val unchecked = remember { mutableStateMapOf<String, Boolean>() }
    val target = locations.find { it.path == targetPath }
    fun isThere(rootPath: String) = rootPath == targetPath
    val movable = comics.filter { !isThere(it.root.absolutePath) }
    val chosen = movable.filter { unchecked[it.id] != true }
    val needBytes = remember(chosen.map { it.id }) { chosen.sumOf { c -> c.pages.sumOf { c.fileOf(it).length() } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搬遷漫畫") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("搬到：", style = MaterialTheme.typography.labelLarge)
                locations.forEach { loc ->
                    Row(
                        Modifier.fillMaxWidth().clickable { targetPath = loc.path },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = loc.path == targetPath, onClick = { targetPath = loc.path })
                        Column {
                            Text(loc.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "剩餘 ${formatBytes(loc.freeBytes)} · 已有 ${vm.comics.count { it.root.absolutePath == loc.path }} 部",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "要搬的漫畫（${chosen.size}/${movable.size}）",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { unchecked.clear() }) { Text("全選") }
                    TextButton(onClick = { movable.forEach { unchecked[it.id] = true } }) { Text("全不選") }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(comics, key = { it.id }) { c ->
                        val there = isThere(c.root.absolutePath)
                        val checked = !there && unchecked[c.id] != true
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (there) 0.45f else 1f)
                                .clickable(enabled = !there) { unchecked[c.id] = checked }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = !there,
                                onCheckedChange = { unchecked[c.id] = !it },
                            )
                            AsyncImage(
                                model = c.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp, 42.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (there) "已經在這個位置" else "目前在：${vm.locationOf(c)?.label ?: "未知位置"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (there) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                val notEnough = target != null && needBytes > target.freeBytes
                Text(
                    when {
                        movable.isEmpty() -> "全部都已經在這個位置了。"
                        notEnough -> "需要 ${formatBytes(needBytes)}，目的地只剩 ${formatBytes(target!!.freeBytes)}，可能會失敗（失敗的漫畫會留在原位置）。"
                        else -> "共 ${formatBytes(needBytes)}。搬完會刪除原位置的檔案。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notEnough) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && chosen.isNotEmpty(),
                onClick = {
                    if (target != null) vm.move(chosen.map { it.id }, target)
                    onDismiss()
                },
            ) { Text("搬遷 ${chosen.size} 部") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
