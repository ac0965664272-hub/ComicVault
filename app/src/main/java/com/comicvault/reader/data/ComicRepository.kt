package com.comicvault.reader.data

import android.content.Context
import com.comicvault.reader.util.ImageFiles
import com.comicvault.reader.util.NaturalOrder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

data class Page(val file: String, val name: String, val w: Int, val h: Int) {
    val ratio: Float get() = if (w > 0 && h > 0) w.toFloat() / h else 0.7f
}

data class Comic(
    val id: String,
    val title: String,
    val created: Long,
    val lastPage: Int,
    val pages: List<Page>,
    val dir: File,
) {
    fun fileOf(p: Page) = File(dir, p.file)
    val cover: File? get() = pages.firstOrNull()?.let { fileOf(it) }
}

/** 一個待匯入的檔案 */
class Candidate(
    val name: String,
    val size: Long,
    val open: () -> InputStream?,
    /** 刪除原始檔；null 代表這個來源無法逐一刪除 */
    val deleteOriginal: (() -> Boolean)?,
)

class PreparedImport(
    val title: String,
    val candidates: List<Candidate>,
    /** 整個來源一次刪除（例如 ZIP 檔本身） */
    val deleteSource: (() -> Boolean)? = null,
    val cleanup: () -> Unit = {},
)

data class ImportResult(val added: Int, val skipped: Int, val deleted: Int, val deleteFailed: Int)

/**
 * 漫畫存放在 App 私有目錄 /data/data/<package>/files/comics/<id>/
 * 系統相簿和其他 App 都讀不到這裡（另外再放一個 .nomedia 雙重保險）。
 */
class ComicRepository(context: Context) {
    private val root = File(context.filesDir, "comics")

    init {
        root.mkdirs()
        runCatching { File(root, ".nomedia").createNewFile() }
    }

    fun list(): List<Comic> =
        (root.listFiles { f -> f.isDirectory } ?: emptyArray())
            .mapNotNull { load(it) }
            .sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }

    fun get(id: String): Comic? = load(File(root, id))

    @Synchronized
    fun create(title: String): Comic {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val c = Comic(id, title.ifBlank { "未命名漫畫" }, System.currentTimeMillis(), 0, emptyList(), dir)
        save(c)
        return c
    }

    @Synchronized
    fun rename(id: String, title: String) {
        get(id)?.let { save(it.copy(title = title.ifBlank { it.title })) }
    }

    @Synchronized
    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    @Synchronized
    fun setLastPage(id: String, page: Int) {
        get(id)?.let { if (it.lastPage != page) save(it.copy(lastPage = page)) }
    }

    /** 複製圖片進漫畫資料夾，並把全部頁面依檔名重新排序 */
    fun addPages(
        comic: Comic,
        prepared: PreparedImport,
        deleteOriginals: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportResult {
        val sorted = prepared.candidates.sortedWith { a, b -> NaturalOrder.compareFileNames(a.name, b.name) }
        val newPages = ArrayList<Page>()
        var skipped = 0
        var deleted = 0
        var failed = 0

        sorted.forEachIndexed { i, c ->
            onProgress(i, sorted.size)
            val ext = ImageFiles.ext(c.name).takeIf { it in ImageFiles.imageExt } ?: "img"
            val out = File(comic.dir, "p_${UUID.randomUUID()}.$ext")
            val copied = runCatching {
                val input = c.open() ?: return@runCatching false
                input.use { inp -> out.outputStream().use { inp.copyTo(it, 64 * 1024) } }
                true
            }.getOrDefault(false)
            val size = if (copied) ImageFiles.probeSize(out) else null
            if (size == null) {
                out.delete()
                skipped++
                return@forEachIndexed
            }
            newPages += Page(out.name, c.name, size.first, size.second)
            if (deleteOriginals) {
                val del = c.deleteOriginal
                if (del != null) {
                    if (runCatching { del() }.getOrDefault(false)) deleted++ else failed++
                }
            }
        }
        if (deleteOriginals && newPages.isNotEmpty()) {
            val del = prepared.deleteSource
            if (del != null) {
                if (runCatching { del() }.getOrDefault(false)) deleted++ else failed++
            }
        }
        onProgress(sorted.size, sorted.size)

        synchronized(this) {
            val latest = get(comic.id) ?: comic
            val merged = (latest.pages + newPages).sortedWith { a, b -> NaturalOrder.compareFileNames(a.name, b.name) }
            save(latest.copy(pages = merged))
        }
        return ImportResult(newPages.size, skipped, deleted, failed)
    }

    private fun load(dir: File): Comic? = runCatching {
        val json = JSONObject(File(dir, META).readText())
        val arr = json.getJSONArray("pages")
        val pages = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Page(o.getString("file"), o.getString("name"), o.optInt("w"), o.optInt("h"))
        }
        Comic(
            id = json.getString("id"),
            title = json.getString("title"),
            created = json.optLong("created"),
            lastPage = json.optInt("lastPage"),
            pages = pages,
            dir = dir,
        )
    }.getOrNull()

    private fun save(c: Comic) {
        val arr = JSONArray()
        c.pages.forEach {
            arr.put(JSONObject().put("file", it.file).put("name", it.name).put("w", it.w).put("h", it.h))
        }
        val json = JSONObject()
            .put("id", c.id)
            .put("title", c.title)
            .put("created", c.created)
            .put("lastPage", c.lastPage)
            .put("pages", arr)
            .toString()
        val target = File(c.dir, META)
        val tmp = File(c.dir, "$META.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.writeText(json)
            tmp.delete()
        }
    }

    companion object {
        private const val META = "meta.json"
    }
}
