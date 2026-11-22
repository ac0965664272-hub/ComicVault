package com.comicvault.reader.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comicvault.reader.data.Comic
import com.comicvault.reader.data.ComicRepository
import com.comicvault.reader.data.HiddenFolder
import com.comicvault.reader.data.HiddenScanner
import com.comicvault.reader.data.Importer
import com.comicvault.reader.data.PreparedImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImportSource {
    data class Docs(val uris: List<Uri>) : ImportSource
    data class Tree(val uri: Uri) : ImportSource
    data class Zip(val uri: Uri) : ImportSource
    data class Dir(val dir: File) : ImportSource
}

class PendingImport(val prepared: PreparedImport, val targetId: String?, val fromHidden: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ComicRepository(app)

    var comics by mutableStateOf<List<Comic>>(emptyList())
        private set
    var busyText by mutableStateOf<String?>(null)
        private set
    var busyProgress by mutableStateOf<Float?>(null)
        private set
    var pending by mutableStateOf<PendingImport?>(null)
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
            comics = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    fun prepare(source: ImportSource, targetId: String?) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busyText = if (source is ImportSource.Zip) "解壓縮中…" else "讀取檔案清單…"
            busyProgress = null
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    when (source) {
                        is ImportSource.Docs -> Importer.fromDocuments(ctx, source.uris)
                        is ImportSource.Tree -> Importer.fromTree(ctx, source.uri)
                        is ImportSource.Zip -> Importer.fromZip(ctx, source.uri)
                        is ImportSource.Dir -> Importer.fromDirectory(ctx, source.dir)
                    }
                }
            }
            busyText = null
            r.onSuccess { p ->
                if (p.candidates.isEmpty()) {
                    p.cleanup()
                    message = "沒有找到可以讀取的圖片"
                } else {
                    pending = PendingImport(p, targetId, source is ImportSource.Dir)
                }
            }.onFailure {
                message = "讀取失敗：${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    fun cancelPending() {
        pending?.prepared?.cleanup()
        pending = null
    }

    fun confirmImport(title: String, deleteOriginals: Boolean) {
        val p = pending ?: return
        pending = null
        viewModelScope.launch {
            busyText = "匯入中…"
            busyProgress = 0f
            val r = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val existing = p.targetId?.let { repo.get(it) }
                        val comic = existing ?: repo.create(title)
                        val res = repo.addPages(comic, p.prepared, deleteOriginals) { done, total ->
                            busyProgress = if (total == 0) 1f else done.toFloat() / total
                        }
                        if (existing == null && res.added == 0) repo.delete(comic.id)
                        res
                    }
                } finally {
                    p.prepared.cleanup()
                }
            }
            busyText = null
            busyProgress = null
            r.onSuccess { res ->
                message = buildString {
                    append("已匯入 ${res.added} 頁")
                    if (res.skipped > 0) append("，略過 ${res.skipped} 個無法讀取的檔案")
                    if (deleteOriginals) {
                        if (res.deleted > 0) append("，已刪除 ${res.deleted} 個原始檔")
                        if (res.deleteFailed > 0) append("，${res.deleteFailed} 個原始檔無法刪除（請改用「選擇資料夾」匯入或手動刪除）")
                    }
                }
            }.onFailure { message = "匯入失敗：${it.message ?: it.javaClass.simpleName}" }
            refresh()
            if (p.fromHidden && deleteOriginals) scanHidden()
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.rename(id, title) }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            refresh()
        }
    }

    fun setLastPage(id: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) { repo.setLastPage(id, page) }
    }

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
