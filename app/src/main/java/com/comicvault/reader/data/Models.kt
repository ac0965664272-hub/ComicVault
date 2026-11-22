package com.comicvault.reader.data

import java.io.File
import java.io.InputStream

/** 一部漫畫最多幾話 */
const val MAX_CHAPTERS = 16

fun chapterLabel(index: Int) = "第${index + 1}話"

data class Page(val file: String, val name: String, val w: Int, val h: Int) {
    val ratio: Float get() = if (w > 0 && h > 0) w.toFloat() / h else 0.7f
}

/** name：章節副標題（來源資料夾名稱或使用者自訂），顯示時前面一律加「第N話」 */
data class Chapter(val name: String, val pages: List<Page>)

data class Category(val id: String, val name: String)

data class Comic(
    val id: String,
    val title: String,
    val categoryId: String?,
    val created: Long,
    /** 全書攤平後的頁碼 */
    val lastPage: Int,
    val chapters: List<Chapter>,
    val dir: File,
    /** 作者，多位作者用「、」分隔 */
    val author: String = "",
    val tags: List<String> = emptyList(),
) {
    val authors: List<String> get() = com.comicvault.reader.util.Search.splitAuthors(author)

    val pages: List<Page> = chapters.flatMap { it.pages }

    /** 每一話第一頁在 pages 裡的位置 */
    val chapterStarts: List<Int> = run {
        var acc = 0
        chapters.map { ch -> acc.also { acc += ch.pages.size } }
    }

    fun chapterOf(pageIndex: Int): Int {
        var result = 0
        chapterStarts.forEachIndexed { i, start -> if (pageIndex >= start && chapters[i].pages.isNotEmpty()) result = i }
        return result
    }

    fun fileOf(p: Page) = File(dir, p.file)
    val cover: File? get() = pages.firstOrNull()?.let { fileOf(it) }
    val root: File get() = dir.parentFile ?: dir
}

enum class StorageKind { Internal, AppExternal, Custom }

data class StorageLocation(
    val root: File,
    val label: String,
    val detail: String,
    val kind: StorageKind,
    val removable: Boolean,
) {
    val path: String get() = root.absolutePath
    val available: Boolean get() = root.exists() || runCatching { root.mkdirs() }.getOrDefault(false)
    val freeBytes: Long get() = runCatching { (if (root.exists()) root else root.parentFile ?: root).usableSpace }.getOrDefault(0L)
}

/** 匯入來源中的一個檔案。path 為含資料夾的相對路徑，例如「海賊王/第1話/001.jpg」 */
class Candidate(
    val path: String,
    val size: Long,
    val mime: String?,
    /** 給 Coil 顯示縮圖用（Uri 或 File） */
    val preview: Any?,
    val open: () -> InputStream?,
    /** 刪除原始檔；null 代表這個來源無法逐一刪除 */
    val deleteOriginal: (() -> Boolean)?,
)

/** 一個匯入來源（一個資料夾、一個壓縮檔、或一批圖片） */
class Source(
    val label: String,
    val images: List<Candidate>,
    /** 整個來源一次刪除（例如壓縮檔本身） */
    val deleteSource: (() -> Boolean)? = null,
    val cleanup: () -> Unit = {},
)

class PlannedChapter(val name: String, val items: List<Candidate>)

class PlannedComic(
    val key: String,
    val title: String,
    val chapters: List<PlannedChapter>,
    val source: Source,
    /** 從名稱自動判斷的作者，例如「[作者] 標題」 */
    val author: String = "",
) {
    val imageCount: Int get() = chapters.sumOf { it.items.size }
    val preview: Any? get() = chapters.firstOrNull()?.items?.firstOrNull()?.preview
}

data class ImportResult(val comics: Int, val added: Int, val skipped: Int, val deleted: Int, val deleteFailed: Int)
