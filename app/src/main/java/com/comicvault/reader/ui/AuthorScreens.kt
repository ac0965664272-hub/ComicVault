package com.comicvault.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comicvault.reader.util.Search

/** 作者一覽：每位作者有幾部作品，可用任一個字篩選 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorsScreen(vm: MainViewModel, onBack: () -> Unit, onAuthor: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val list = vm.authors.filter { q.isBlank() || Search.authorMatches(it.first, q) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("作者（${vm.authors.size}）") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text("輸入作者名字的任一個字") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (vm.authors.isEmpty()) {
                item {
                    Text(
                        "還沒有作者資料。\n匯入時名稱像「[作者] 標題」會自動辨識，也可以在漫畫頁面或長按多選後設定作者。",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(list, key = { it.first }) { (name, n) ->
                val cover = vm.worksOf(name).firstOrNull()?.cover
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text("$n 部作品") },
                    leadingContent = {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp, 60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    },
                    modifier = Modifier.clickable { onAuthor(name) },
                )
            }
        }
    }
}

/** 單一作者的所有作品 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    vm: MainViewModel,
    author: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRead: (String) -> Unit,
    onRenamed: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    val works = vm.worksOf(author)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(author)
                        Text("${works.size} 部作品", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "修改作者名稱") }
                },
            )
        },
    ) { pad ->
        if (works.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("這位作者目前沒有作品", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(works, key = { it.id }) { c ->
                    ComicCard(
                        c = c,
                        selected = false,
                        selecting = false,
                        onClick = { onOpen(c.id) },
                        onLongClick = { onOpen(c.id) },
                        onRead = { onRead(c.id) },
                    )
                }
            }
        }
    }
    if (renaming) {
        TextInputDialog("修改作者名稱（所有作品一起改）", author, onDismiss = { renaming = false }) {
            renaming = false
            if (it.isNotBlank() && it != author) {
                vm.renameAuthor(author, it)
                onRenamed(it)
            }
        }
    }
}
