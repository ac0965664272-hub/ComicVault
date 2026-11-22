package com.comicvault.reader.util

/** 一部漫畫的「指紋」：各頁的檔案大小 */
class Fingerprint(val id: String, val title: String, sizes: List<Long>) {
    val sizes: List<Long> = sizes.filter { it > 0 }.sorted()
}

class DuplicateMatch(val id: String, val title: String, val exact: Boolean, val reason: String)

object Duplicates {
    /**
     * 匯入的漫畫是否已經存在：
     *  - 頁數相同、每一頁檔案大小都相同 → 完全相同（預設不匯入）
     *  - 80% 以上的頁面大小相同，或名稱相同 → 可能重複（提醒）
     */
    fun find(title: String, sizes: List<Long>, existing: List<Fingerprint>): DuplicateMatch? {
        val mine = sizes.filter { it > 0 }.sorted()
        var best: DuplicateMatch? = null
        var bestScore = 0.0
        for (fp in existing) {
            val score = overlap(mine, fp.sizes)
            if (mine.isNotEmpty() && mine.size == fp.sizes.size && score >= 1.0) {
                return DuplicateMatch(fp.id, fp.title, exact = true, reason = "內容完全相同")
            }
            if (score >= 0.8 && score > bestScore) {
                bestScore = score
                best = DuplicateMatch(fp.id, fp.title, exact = false, reason = "${(score * 100).toInt()}% 的頁面相同")
            }
        }
        if (best != null) return best
        val t = Search.norm(title)
        val sameName = existing.firstOrNull { t.isNotEmpty() && Search.norm(it.title) == t }
        return sameName?.let { DuplicateMatch(it.id, it.title, exact = false, reason = "名稱相同") }
    }

    /** 兩組（已排序的）大小有多少比例相同 */
    fun overlap(a: List<Long>, b: List<Long>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var i = 0
        var j = 0
        var same = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { same++; i++; j++ }
                a[i] < b[j] -> i++
                else -> j++
            }
        }
        return same.toDouble() / maxOf(a.size, b.size)
    }
}
