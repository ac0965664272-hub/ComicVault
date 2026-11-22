package com.comicvault.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comicvault.reader.data.Category
import com.comicvault.reader.data.Comic
import com.comicvault.reader.data.MAX_CHAPTERS
import com.comicvault.reader.data.StorageLocation

private const val ALL = "__all__"
private const val NONE = "__none__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpen: (String) -> Unit,
    onRead: (String) -> Unit,
    onImport: () -> Unit,
    onScanHidden: () -> Unit,
    onSettings: () -> Unit,
    onAuthors: () -> Unit,
    onAuthor: (String) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(vm.searchQuery.isNotEmpty()) }
    val query = vm.searchQuery
    var showTag by remember { mutableStateOf(false) }
    var showAuthor by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    var overflow by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }

    LaunchedEffect(vm.comics) {
        val validIds = vm.comics.map { it.id }.toSet()
        selected.retainAll { it in validIds }
    }

    val searchActive = searching && query.isNotBlank()
    val matchedAuthors = if (searchActive) {
        vm.authors.filter { (name, _) -> com.comicvault.reader.util.Search.authorMatches(name, query) }
    } else {
        emptyList()
    }
    val shown = if (searchActive) vm.comics.filter { vm.matches(it, query) } else vm.comics.filter {
        when (filter) {
            ALL -> true
            NONE -> it.categoryId == null || vm.categories.none { c -> c.id == it.categoryId }
            else -> it.categoryId == filter
        }
    }
    val selectedComics = selected.mapNotNull { id -> vm.comics.find { it.id == id } }

    BackHandler(enabled = selecting) { selected.clear() }
    BackHandler(enabled = !selecting && searching) { searching = false; vm.searchQuery = "" }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Outlined.Close, "取消選取") }
                    },
                    title = { Text("已選 ${selected.size} 部") },
                    actions = {
                        TextButton(onClick = { showMerge = true }, enabled = selected.size >= 2) { Text("合併") }
                        TextButton(onClick = { showCategory = true }) { Text("類別") }
                        TextButton(onClick = { showMove = true }) { Text("搬遷") }
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(text = { Text("全選") }, onClick = {
                                overflow = false
                                shown.forEach { if (it.id !in selected) selected.add(it.id) }
                            })
                            if (selected.size == 1) {
                                DropdownMenuItem(text = { Text("重新命名") }, onClick = { overflow = false; showRename = true })
                            }
                            DropdownMenuItem(text = { Text("加標籤") }, onClick = { overflow = false; showTag = true })
                            DropdownMenuItem(text = { Text("設定作者") }, onClick = { overflow = false; showAuthor = true })
                            DropdownMenuItem(text = { Text("刪除") }, onClick = { overflow = false; showDelete = true })
                        }
                    },
                )
            } else if (searching) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "關閉搜尋")
                        }
                    },
                    title = {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        TextField(
                            value = query,
                            onValueChange = { vm.searchQuery = it },
                            placeholder = { Text("搜尋名稱、作者、標籤") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { vm.searchQuery = "" }) { Icon(Icons.Outlined.Close, "清除") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("我的漫畫") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "搜尋") }
                        IconButton(onClick = onAuthors) { Icon(Icons.Outlined.Person, "作者") }
                        IconButton(onClick = onScanHidden) { Icon(Icons.Outlined.VisibilityOff, "掃描隱藏相簿") }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "設定") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("匯入漫畫") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            if (searching) {
                if (query.isBlank()) {
                    Text(
                        "輸入漫畫名稱、標籤，或作者名字的任一個字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else if (matchedAuthors.isNotEmpty()) {
                    Text(
                        "作者",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matchedAuthors, key = { it.first }) { (name, n) ->
                            AssistChip(
                                onClick = { onAuthor(name) },
                                label = { Text("$name（$n 部）") },
                                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                            )
                        }
                    }
                }
            }
            // 類別篩選
            if (!searching) LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == ALL, onClick = { filter = ALL }, label = { Text("全部 ${vm.comics.size}") }) }
                items(vm.categories, key = { it.id }) { c ->
                    val n = vm.comics.count { it.categoryId == c.id }
                    FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text("${c.name} $n") })
                }
                if (vm.categories.isNotEmpty()) {
                    item { FilterChip(selected = filter == NONE, onClick = { filter = NONE }, label = { Text("未分類") }) }
                }
                item {
                    AssistChip(
                        onClick = { showCategoryManager = true },
                        label = { Text(if (vm.categories.isEmpty()) "＋ 新增類別" else "管理類別") },
                    )
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            vm.comics.isEmpty() -> "還沒有漫畫\n點右下角「匯入漫畫」開始"
                            searching && query.isBlank() -> ""
                            searching -> "找不到符合「$query」的漫畫"
                            else -> "這個類別還沒有漫畫"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                if (!selecting && !searching) {
                    Text(
                        "長按可多選（合併、分類、標籤、搬遷）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp,
                        bottom = pad.calculateBottomPadding() + 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { c ->
                        val isSel = c.id in selected
                        ComicCard(
                            c = c,
                            selected = isSel,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    if (isSel) selected.remove(c.id) else selected.add(c.id)
                                } else {
                                    onOpen(c.id)
                                }
                            },
                            onLongClick = { if (!isSel) selected.add(c.id) },
                            onRead = { onRead(c.id) },
                        )
                    }
                }
            }
        }
    }

    if (showMerge && selectedComics.size >= 2) {
        MergeDialog(
            comics = selectedComics,
            categories = vm.categories,
            onDismiss = { showMerge = false },
            onConfirm = { ids, title, cat ->
                showMerge = false
                vm.merge(ids, title, cat)
                selected.clear()
            },
        )
    }

    if (showCategory) {
        CategoryPickerDialog(
            categories = vm.categories,
            current = selectedComics.map { it.categoryId }.distinct().singleOrNull(),
            onDismiss = { showCategory = false },
            onPick = { cat ->
                showCategory = false
                vm.setCategory(selected.toList(), cat)
                selected.clear()
            },
            onCreate = { name ->
                showCategory = false
                vm.addCategory(name, selected.toList())
                selected.clear()
            },
        )
    }

    if (showMove) {
        LocationPickerDialog(
            title = "搬遷 ${selected.size} 部漫畫到…",
            locations = vm.locations,
            currentPaths = selectedComics.map { it.root.absolutePath }.toSet(),
            onDismiss = { showMove = false },
            onPick = { loc ->
                showMove = false
                vm.move(selected.toList(), loc)
                selected.clear()
            },
        )
    }

    if (showRename && selectedComics.size == 1) {
        TextInputDialog(
            title = "重新命名",
            initial = selectedComics[0].title,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                vm.rename(selectedComics[0].id, it)
                selected.clear()
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("刪除 ${selected.size} 部漫畫？") },
            text = { Text(selectedComics.joinToString("、") { it.title } + "\n\n圖片會從 App 中永久刪除，無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete(selected.toList())
                    selected.clear()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }

    if (showTag) {
        TextInputDialog("為 ${selected.size} 部加標籤（多個用空白或逗號分隔）", "", onDismiss = { showTag = false }) {
            vm.addTags(selected.toList(), it.split(Regex("[\\s,，、]+")))
            showTag = false
            selected.clear()
        }
    }
    if (showAuthor) {
        TextInputDialog(
            "設定 ${selected.size} 部的作者",
            selectedComics.map { it.author }.distinct().singleOrNull().orEmpty(),
            onDismiss = { showAuthor = false },
        ) {
            vm.setAuthor(selected.toList(), it)
            showAuthor = false
            selected.clear()
        }
    }

    if (showCategoryManager) {
        CategoryManagerDialog(vm, onDismiss = { showCategoryManager = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(
    c: Comic,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRead: () -> Unit,
) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
            AsyncImage(
                model = c.cover,
                contentDescription = c.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (c.chapters.size > 1) {
                Text(
                    "${c.chapters.size} 話",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (selecting) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(if (selected) Color.White else Color.Black.copy(alpha = 0.3f), CircleShape),
                )
            } else if (c.lastPage > 0) {
                Text(
                    "繼續",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .clickable(onClick = onRead)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
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
            listOfNotNull(c.author.takeIf { it.isNotBlank() }, "${c.pages.size} 頁").joinToString(" · "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 合併：調整順序、命名、選類別 */
@Composable
private fun MergeDialog(
    comics: List<Comic>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, String, String?) -> Unit,
) {
    val order = remember { mutableStateListOf<Comic>().apply { addAll(comics) } }
    var title by remember { mutableStateOf(comics.first().title) }
    var category by remember { mutableStateOf(comics.first().categoryId) }
    var catMenu by remember { mutableStateOf(false) }
    val total = order.sumOf { it.chapters.size }
    val tooMany = total > MAX_CHAPTERS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合併成一部漫畫") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("新的漫畫名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("依下面的順序排成各話（可用箭頭調整）：", style = MaterialTheme.typography.bodySmall)
                var chapterNo = 1
                order.forEachIndexed { i, c ->
                    val from = chapterNo
                    val to = chapterNo + c.chapters.size - 1
                    chapterNo = to + 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (from == to) "→ 第${from}話" else "→ 第${from}～${to}話",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (to > MAX_CHAPTERS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "上移")
                        }
                        IconButton(onClick = { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "下移")
                        }
                    }
                }
                Text(
                    if (tooMany) "合併後共 $total 話，超過上限 $MAX_CHAPTERS 話，請少選幾部。"
                    else "合併後共 $total 話（上限 $MAX_CHAPTERS 話）。原本的 ${order.size} 部會合成這一部。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooMany) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    TextButton(onClick = { catMenu = true }) {
                        Text("類別：" + (categories.find { it.id == category }?.name ?: "未分類"))
                    }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        DropdownMenuItem(text = { Text("未分類") }, onClick = { category = null; catMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c.id; catMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !tooMany && title.isNotBlank(),
                onClick = { onConfirm(order.map { it.id }, title.trim(), category) },
            ) { Text("合併") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定類別") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                (listOf<Category?>(null) + categories).forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(c?.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == c?.id, onClick = { onPick(c?.id) })
                        Text(c?.name ?: "未分類")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("新增類別") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onCreate(newName.trim()) }, enabled = newName.isNotBlank()) { Text("建立") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun LocationPickerDialog(
    title: String,
    locations: List<StorageLocation>,
    currentPaths: Set<String>,
    onDismiss: () -> Unit,
    onPick: (StorageLocation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                locations.forEach { loc ->
                    val here = currentPaths.size == 1 && loc.path in currentPaths
                    ListItem(
                        headlineContent = { Text(loc.label + if (here) "（目前位置）" else "") },
                        supportingContent = { Text("可用空間 ${formatBytes(loc.freeBytes)}") },
                        modifier = Modifier.clickable(enabled = !here) { onPick(loc) },
                    )
                }
                Text(
                    "要搬到其他資料夾，請先到「設定 → 存放位置」新增自訂資料夾。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CategoryManagerDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("類別") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.categories.isEmpty()) {
                    Text("還沒有類別。建立後，可以長按漫畫 →「類別」把漫畫分類。", style = MaterialTheme.typography.bodySmall)
                }
                vm.categories.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${c.name}（${vm.comics.count { it.categoryId == c.id }}）",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { renaming = c }) { Icon(Icons.Outlined.Edit, "重新命名") }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Outlined.Delete, "刪除") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("新類別名稱") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { vm.addCategory(newName.trim()); newName = "" },
                        enabled = newName.isNotBlank(),
                    ) { Text("新增") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
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
