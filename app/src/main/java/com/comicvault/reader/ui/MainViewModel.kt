package com.comicvault.reader.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comicvault.reader.data.Category
import com.comicvault.reader.data.Comic
import com.comicvault.reader.data.ComicRepository
import com.comicvault.reader.data.Detector
import com.comicvault.reader.data.HiddenFolder
import com.comicvault.reader.data.HiddenScanner
import com.comicvault.reader.data.Importer
import com.comicvault.reader.data.MAX_CHAPTERS
import com.comicvault.reader.data.PlannedComic
import com.comicvault.reader.data.Source
import com.comicvault.reader.data.StorageLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.comicvault.reader.util.NaturalOrder
import com.comicvault.reader.util.Search

sealed interface ImportSource {
    data class Docs(val uris: List<Uri>) : ImportSource
    data class Tree(val uri: Uri) : ImportSource
    data class Zips(val uris: List<Uri>) : ImportSource
    data class Dirs(val dirs: List<File>) : ImportSource
}

/** 匯入前的確認畫面狀態 */
class ImportSession(val sources: List<Source>, val targetId: String?, val fromHidden: Boolean) {
    /** true：同一層、名稱像章節的子資料夾合成同一部的各話 */
    var chapterMode by mutableStateOf(true)
    val plans: List<PlannedComic> by derivedStateOf { Detector.plan(sources, chapterMode) }
    fun cleanup() = sources.forEach { runCatching { it.cleanup() } }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ComicRepository(app)

    var comics by mutableStateOf<List<Comic>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var locations by mutableStateOf<List<StorageLocation>>(emptyList())
        private set
    var defaultRoot by mutableStateOf("")
        private set
    var busyText by mutableStateOf<String?>(null)
        private set
    var busyProgress by mutableStateOf<Float?>(null)
        private set
    var session by mutableStateOf<ImportSession?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var hiddenFolders by mutableStateOf<List<HiddenFolder>?>(null)
        private set
    var scanStatus by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                Triple(repo.list(), repo.categories(), repo.locations()) to repo.defaultRoot().absolutePath
            }
            comics = r.first.first
            categories = r.first.second
            locations = r.first.third
            defaultRoot = r.second
        }
    }

    fun locationOf(c: Comic): StorageLocation? = locations.find { it.path == c.root.absolutePath }

    /** 在背景執行，顯示進度視窗，完成後重新整理 */
    private fun work(title: String, block: suspend ((Float?) -> Unit) -> String?) {
        viewModelScope.launch {
            busyText = title
            busyProgress = null
            val r = withContext(Dispatchers.IO) { runCatching { block { busyProgress = it } } }
            busyText = null
            busyProgress = null
            r.onSuccess { if (it != null) message = it }
                .onFailure { message = it.message ?: it.javaClass.simpleName }
            refresh()
        }
    }

    // ───────────────────────────── 匯入

    fun prepare(source: ImportSource, targetId: String?, fromHidden: Boolean = false) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busyText = when (source) {
                is ImportSource.Zips -> "解壓縮中…"
                else -> "尋找圖片中…"
            }
            busyProgress = null
            val errors = ArrayList<String>()
            val sources = withContext(Dispatchers.IO) {
                fun attempt(label: String, f: () -> Source): Source? =
                    runCatching(f).onFailure { errors += "$label：${it.message ?: it.javaClass.simpleName}" }.getOrNull()
                when (source) {
                    is ImportSource.Docs -> listOfNotNull(attempt("圖片") { Importer.fromDocuments(ctx, source.uris) })
                    is ImportSource.Tree -> listOfNotNull(attempt("資料夾") { Importer.fromTree(ctx, source.uri) })
                    is ImportSource.Zips -> source.uris.mapNotNull { u ->
                        attempt(u.lastPathSegment?.substringAfterLast('/') ?: "壓縮檔") { Importer.fromZip(ctx, u) }
                    }
                    is ImportSource.Dirs -> source.dirs.mapNotNull { d -> attempt(d.name) { Importer.fromDirectory(ctx, d) } }
                }
            }
            busyText = null
            val usable = sources.filter { it.images.isNotEmpty() }
            sources.filter { it.images.isEmpty() }.forEach { it.cleanup() }
            if (usable.isEmpty()) {
                message = (listOf("沒有找到含有圖片的資料夾或檔案") + errors).joinToString("\n")
            } else {
                if (errors.isNotEmpty()) message = "部分來源讀取失敗：\n" + errors.joinToString("\n")
                session = ImportSession(usable, targetId, fromHidden)
            }
        }
    }

    fun cancelImport() {
        session?.cleanup()
        session = null
    }

    fun confirmImport(
        selected: List<PlannedComic>,
        titles: Map<String, String>,
        authors: Map<String, String>,
        categoryId: String?,
        root: File,
        deleteOriginals: Boolean,
    ) {
        val s = session ?: return
        session = null
        val allPlans = s.plans
        work("匯入中…") { progress ->
            try {
                val total = selected.sumOf { it.imageCount }.coerceAtLeast(1)
                var done = 0
                val onFile = { done++; progress(done.toFloat() / total) }
                var comicsAdded = 0
                var added = 0
                var skipped = 0
                var deleted = 0
                var failed = 0
                if (s.targetId != null) {
                    val r = repo.importComic(root, "", null, selected.flatMap { it.chapters }, s.targetId, deleteOriginals, onFile)
                    added += r.added; skipped += r.skipped; deleted += r.deleted; failed += r.deleteFailed
                } else {
                    for (p in selected) {
                        val title = titles[p.key]?.takeIf { it.isNotBlank() } ?: p.title
                        val author = authors[p.key] ?: p.author
                        val r = repo.importComic(root, title, categoryId, p.chapters, null, deleteOriginals, onFile, author)
                        comicsAdded += r.comics; added += r.added; skipped += r.skipped
                        deleted += r.deleted; failed += r.deleteFailed
                    }
                }
                // 壓縮檔：裡面的漫畫全部都有匯入時，才刪除壓縮檔本身
                if (deleteOriginals) {
                    val keys = selected.map { it.key }.toSet()
                    s.sources.filter { it.deleteSource != null }.forEach { src ->
                        val plansOfSrc = allPlans.filter { it.source === src }
                        if (plansOfSrc.isNotEmpty() && plansOfSrc.all { it.key in keys }) {
                            if (runCatching { src.deleteSource!!() }.getOrDefault(false)) deleted++ else failed++
                        }
                    }
                }
                buildString {
                    if (s.targetId != null) append("已新增 ${selected.sumOf { it.chapters.size }} 話，共 $added 頁")
                    else append("已匯入 $comicsAdded 部漫畫，共 $added 頁")
                    if (skipped > 0) append("\n略過 $skipped 個無法讀取的檔案")
                    if (deleteOriginals && deleted > 0) append("\n已刪除 $deleted 個原始檔")
                    if (deleteOriginals && failed > 0) append("\n$failed 個原始檔無法刪除，請手動刪除")
                }
            } finally {
                s.cleanup()
                if (s.fromHidden && deleteOriginals) {
                    withContext(Dispatchers.Main) { hiddenFolders = null }
                }
            }
        }
    }

    // ───────────────────────────── 漫畫操作

    fun rename(id: String, title: String) = work("儲存中…") { repo.rename(id, title); null }

    fun delete(ids: Collection<String>) = work("刪除中…") { ids.forEach { repo.delete(it) }; null }

    fun setLastPage(id: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) { repo.setLastPage(id, page) }
    }

    fun merge(ids: List<String>, title: String, categoryId: String?) = work("合併中…") { progress ->
        val c = repo.merge(ids, title, categoryId) { done, total -> progress(done.toFloat() / total) }
        "已合併成「${c.title}」，共 ${c.chapters.size} 話"
    }

    fun move(ids: Collection<String>, target: StorageLocation) = work("搬遷中…") { progress ->
        val list = ids.mapNotNull { repo.get(it) }
        val total = list.sumOf { it.pages.size }.coerceAtLeast(1)
        var done = 0
        var ok = 0
        var fail = 0
        for (c in list) {
            if (repo.moveComic(c, target.root) { done++; progress(done.toFloat() / total) }) ok++ else fail++
        }
        buildString {
            append("已搬遷 $ok 部到「${target.label}」")
            if (fail > 0) append("\n$fail 部搬遷失敗（空間不足、沒有權限或已存在）")
        }
    }

    fun moveAll(target: StorageLocation) = move(comics.map { it.id }, target)

    // ───────────────────────────── 作者 / 標籤 / 搜尋

    /** 首頁搜尋框的文字（點標籤時也會填入） */
    var searchQuery by mutableStateOf("")

    /** 所有作者（依名稱排序）與作品數 */
    val authors: List<Pair<String, Int>>
        get() = comics.flatMap { it.authors }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedWith { a, b -> NaturalOrder.compare(a.first, b.first) }

    fun worksOf(author: String): List<Comic> = comics.filter { author in it.authors }

    fun matches(c: Comic, q: String): Boolean =
        Search.textMatches(c.title, q) ||
            c.tags.any { Search.textMatches(it, q) } ||
            c.authors.any { Search.authorMatches(it, q) }

    fun setAuthor(ids: Collection<String>, author: String) = work("儲存中…") { repo.setAuthor(ids, author); null }
    fun renameAuthor(old: String, new: String) = work("儲存中…") { repo.renameAuthor(old, new); null }
    fun addTags(ids: Collection<String>, tags: List<String>) = work("儲存中…") { repo.addTags(ids, tags); null }
    fun removeTag(id: String, tag: String) = work("儲存中…") { repo.removeTag(id, tag); null }

    // ───────────────────────────── 章節

    fun moveChapter(id: String, from: Int, to: Int) = work("儲存中…") { repo.moveChapter(id, from, to); null }
    fun renameChapter(id: String, index: Int, name: String) = work("儲存中…") { repo.renameChapter(id, index, name); null }
    fun deleteChapter(id: String, index: Int) = work("刪除中…") { repo.deleteChapter(id, index); null }

    fun canAddChapters(id: String): Int = MAX_CHAPTERS - (comics.find { it.id == id }?.chapters?.size ?: 0)

    // ───────────────────────────── 類別

    fun addCategory(name: String, assignTo: Collection<String> = emptyList()) = work("儲存中…") {
        val c = repo.addCategory(name)
        if (assignTo.isNotEmpty()) repo.setCategory(assignTo, c.id)
        null
    }

    fun renameCategory(id: String, name: String) = work("儲存中…") { repo.renameCategory(id, name); null }
    fun deleteCategory(id: String) = work("刪除中…") { repo.deleteCategory(id); null }
    fun setCategory(ids: Collection<String>, categoryId: String?) = work("儲存中…") { repo.setCategory(ids, categoryId); null }

    // ───────────────────────────── 存放位置

    fun setDefaultRoot(loc: StorageLocation) = work("儲存中…") {
        if (!loc.available) error("無法使用「${loc.label}」，請確認 SD 卡已插入或已授權")
        repo.setDefaultRoot(loc.root)
        "新的漫畫會存到「${loc.label}」"
    }

    fun addCustomRoot(picked: File) = work("建立資料夾中…") {
        val loc = repo.addCustomRoot(picked) ?: error("無法在這個資料夾建立存放空間。請確認已開啟「所有檔案存取權」。")
        "已新增存放位置：${loc.label}"
    }

    fun removeCustomRoot(loc: StorageLocation) = work("儲存中…") {
        if (repo.removeCustomRoot(loc.path)) "已移除存放位置" else error("這個位置還有漫畫，請先搬遷到其他地方")
    }

    // ───────────────────────────── 隱藏相簿

    fun scanHidden() {
        if (scanStatus != null) return
        viewModelScope.launch {
            scanStatus = "掃描中…"
            val list = withContext(Dispatchers.IO) {
                runCatching { HiddenScanner.scan { scanStatus = "掃描中：$it" } }.getOrDefault(emptyList())
            }
            hiddenFolders = list
            scanStatus = null
        }
    }
}
