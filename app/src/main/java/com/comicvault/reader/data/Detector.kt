package com.comicvault.reader.data

import com.comicvault.reader.util.NaturalOrder
import com.comicvault.reader.util.Search

/**
 * 依照資料夾結構自動判斷「有幾部漫畫、每部有哪幾話」：
 *  - 只有含圖片的資料夾才會出現；沒有圖片的資料夾自動略過
 *  - 同一層有 2 個以上含圖片的子資料夾，且名稱都像章節（含數字、第X話、Vol、Ch…）
 *    → 合成一部漫畫，子資料夾依名稱排序成第1話、第2話…
 *  - 其他含圖片的資料夾 → 各自一部漫畫
 *  - 超過 16 話時自動拆成「名稱」「名稱 (2)」…
 */
object Detector {
    private const val CN = "〇零一二兩两三四五六七八九十百千"
    private val chapterRegex = Regex(
        "[0-9０-９]|第\\s*[$CN]+|[$CN]+\\s*[話话章卷集回部]|(?i)\\b(ch|chap|chapter|vol|ep|episode|part)\\b"
    )

    fun looksLikeChapter(name: String) = chapterRegex.containsMatchIn(name)

    fun plan(sources: List<Source>, chapterMode: Boolean): List<PlannedComic> =
        sources.flatMap { plan(it, chapterMode) }

    fun plan(source: Source, chapterMode: Boolean): List<PlannedComic> {
        val byDir: Map<String, List<Candidate>> = source.images.groupBy { dirOf(it.path) }
        if (byDir.isEmpty()) return emptyList()
        val out = ArrayList<PlannedComic>()
        val consumed = HashSet<String>()
        fun titleOf(dir: String) = if (dir.isEmpty()) source.label else clean(lastSeg(dir))

        if (chapterMode) {
            val groups = byDir.keys.filter { it.isNotEmpty() }.groupBy { dirOf(it) }
            for (parent in groups.keys.sortedByDescending { depth(it) }) {
                val kids = groups.getValue(parent).filter { it !in consumed }
                if (kids.size < 2 || !kids.all { looksLikeChapter(lastSeg(it)) }) continue
                val chapters = ArrayList<PlannedChapter>()
                val own = byDir[parent]
                if (own != null && parent !in consumed) {
                    chapters += PlannedChapter(titleOf(parent), own)
                    consumed += parent
                }
                for (k in kids.sortedWith(NaturalOrder.fileNameComparator)) {
                    chapters += PlannedChapter(lastSeg(k), byDir.getValue(k))
                    consumed += k
                }
                addSplit(out, source, parent, titleOf(parent), chapters)
            }
        }
        for (dir in byDir.keys.sortedWith(NaturalOrder.fileNameComparator)) {
            if (dir in consumed) continue
            // 「海賊王/第1話」單獨成一部時，名稱用「海賊王 第1話」比較好認
            val parent = dirOf(dir)
            val title = if (parent.isNotEmpty() && looksLikeChapter(lastSeg(dir))) {
                "${clean(lastSeg(parent))} ${lastSeg(dir)}"
            } else {
                titleOf(dir)
            }
            addSplit(out, source, dir, title, listOf(PlannedChapter("", byDir.getValue(dir))))
        }
        return out.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }
    }

    private fun addSplit(
        out: MutableList<PlannedComic>,
        source: Source,
        dir: String,
        title: String,
        chapters: List<PlannedChapter>,
    ) {
        val (author, cleanTitle) = Search.guessAuthor(title)
        val parts = chapters.chunked(MAX_CHAPTERS)
        parts.forEachIndexed { i, part ->
            val t = if (i == 0) cleanTitle else "$cleanTitle (${i + 1})"
            out += PlannedComic("${System.identityHashCode(source)}|$dir|$i", t, part, source, author)
        }
    }

    private fun dirOf(path: String) = path.substringBeforeLast('/', "")
    private fun lastSeg(dir: String) = dir.substringAfterLast('/')
    private fun depth(dir: String) = if (dir.isEmpty()) 0 else dir.count { it == '/' } + 1
    private fun clean(s: String) = s.trimStart('.').trim().ifBlank { s.ifBlank { "新漫畫" } }
}
