package com.comicvault.reader.util

/** 搜尋與作者名稱的工具（純 Kotlin，可單獨測試） */
object Search {
    private val authorSeparators = Regex("[、,，/／&＆×;；]")

    private val notAuthorWords = listOf(
        "中文", "中國", "漢化", "汉化", "翻譯", "翻译", "dl版", "無修", "无修", "完結", "完结", "連載", "连载",
        "全彩", "彩色", "高清", "掃圖", "扫图", "雜誌", "杂志", "單行本", "单行本", "english", "chinese", "raw",
    )

    fun norm(s: String): String = s.lowercase().filterNot { it.isWhitespace() }

    /** 「A、B」「A/B」「A & B」→ [A, B] */
    fun splitAuthors(s: String): List<String> =
        s.split(authorSeparators).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** 作者比對：輸入的每一個字都出現在作者名字裡就算符合（打任一個字就找得到） */
    fun authorMatches(author: String, query: String): Boolean {
        val a = norm(author)
        val q = norm(query)
        if (q.isEmpty() || a.isEmpty()) return false
        return q.all { a.indexOf(it) >= 0 }
    }

    fun textMatches(text: String, query: String): Boolean {
        val q = norm(query)
        return q.isNotEmpty() && norm(text).contains(q)
    }

    /**
     * 從名稱猜作者：
     *   「[尾田榮一郎] 海賊王」 → 尾田榮一郎 / 海賊王
     *   「【鳥山明】七龍珠」 → 鳥山明 / 七龍珠
     *   「(C99) [社團 (作者)] 標題」 → 作者 / 標題
     * 回傳 (作者, 去掉作者後的標題)；猜不到時作者為空字串。
     */
    fun guessAuthor(name: String): Pair<String, String> {
        val m = Regex("^\\s*(?:[(（][^)）]*[)）]\\s*)?[\\[【]([^\\]】]+)[\\]】]\\s*(.*)$").find(name)
            ?: return "" to name
        val inside = m.groupValues[1].trim()
        val rest = m.groupValues[2].trim()
        // 「社團 (作者)」取括號內的作者
        val inner = Regex("[(（]([^)）]+)[)）]").find(inside)?.groupValues?.get(1)?.trim()
        val author = (inner ?: inside).trim()
        val low = author.lowercase()
        val bad = author.isEmpty() ||
            author.any { it.isDigit() } ||
            notAuthorWords.any { low.contains(it) } ||
            Regex("^第.+[話话章卷集回]$").matches(author)
        if (bad) return "" to name
        return author to rest.ifBlank { name }
    }
}
