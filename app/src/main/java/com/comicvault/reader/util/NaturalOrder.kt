package com.comicvault.reader.util

import java.math.BigInteger

/**
 * 「人類直覺」的檔名排序：
 *  - 數字依數值比較：2.jpg < 10.jpg < 100.jpg
 *  - 支援全形數字：１２.jpg
 *  - 支援中文數字：第二話 < 第十話 < 第十一話 < 第一百話
 *  - 不分大小寫
 *  - 依路徑逐層比較：第1話/003.jpg < 第2話/001.jpg
 *  - 先比主檔名再比副檔名：page.jpg < page (2).jpg
 */
object NaturalOrder : Comparator<String> {

    private val cnDigits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '壹' to 1, '二' to 2, '兩' to 2, '两' to 2, '貳' to 2,
        '三' to 3, '參' to 3, '叁' to 3, '四' to 4, '肆' to 4, '五' to 5, '伍' to 5,
        '六' to 6, '陸' to 6, '七' to 7, '柒' to 7, '八' to 8, '捌' to 8, '九' to 9, '玖' to 9,
    )
    private val cnUnits = mapOf('十' to 10, '拾' to 10, '百' to 100, '佰' to 100, '千' to 1000, '仟' to 1000)
    private val cnBig = mapOf('萬' to 10_000L, '万' to 10_000L, '億' to 100_000_000L, '亿' to 100_000_000L)

    private sealed interface Token
    private class Num(val value: BigInteger, val rawLength: Int) : Token
    private class Txt(val text: String) : Token

    val fileNameComparator: Comparator<String> = Comparator { a, b -> compareFileNames(a, b) }

    /** 比較兩個（可能含資料夾的）檔名路徑。 */
    fun compareFileNames(a: String, b: String): Int {
        val sa = a.replace('\\', '/').split('/')
        val sb = b.replace('\\', '/').split('/')
        val n = minOf(sa.size, sb.size)
        for (i in 0 until n) {
            val last = i == sa.size - 1 && i == sb.size - 1
            val r = if (last) compareLastSegment(sa[i], sb[i]) else compare(sa[i], sb[i])
            if (r != 0) return r
        }
        if (sa.size != sb.size) return sa.size - sb.size
        return a.compareTo(b)
    }

    private fun compareLastSegment(a: String, b: String): Int {
        val r = compare(stripExt(a), stripExt(b))
        return if (r != 0) r else compare(a, b)
    }

    private fun stripExt(s: String): String {
        val i = s.lastIndexOf('.')
        return if (i > 0) s.substring(0, i) else s
    }

    override fun compare(a: String, b: String): Int {
        val ta = tokenize(a)
        val tb = tokenize(b)
        val n = minOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta[i]
            val y = tb[i]
            val r = when {
                x is Num && y is Num -> x.value.compareTo(y.value)
                x is Txt && y is Txt -> x.text.compareTo(y.text)
                x is Num -> -1 // 數字排在文字前
                else -> 1
            }
            if (r != 0) return r
        }
        if (ta.size != tb.size) return ta.size - tb.size
        // 數值相同時，前導零較多的排後面（01 在 1 之後），最後才比原字串
        for (i in 0 until n) {
            val x = ta[i]
            val y = tb[i]
            if (x is Num && y is Num && x.rawLength != y.rawLength) return x.rawLength - y.rawLength
        }
        return a.compareTo(b)
    }

    private fun isCn(c: Char) = c in cnDigits || c in cnUnits || c in cnBig

    private fun tokenize(s: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val sb = StringBuilder()
        fun flushText() {
            if (sb.isNotEmpty()) {
                out += Txt(sb.toString().lowercase())
                sb.setLength(0)
            }
        }
        while (i < s.length) {
            val c = s[i]
            when {
                Character.isDigit(c) -> {
                    flushText()
                    val start = i
                    val digits = StringBuilder()
                    while (i < s.length && Character.isDigit(s[i])) {
                        digits.append(Character.digit(s[i], 10).coerceAtLeast(0))
                        i++
                    }
                    out += Num(BigInteger(digits.toString()), i - start)
                }
                isCn(c) -> {
                    val start = i
                    while (i < s.length && isCn(s[i])) i++
                    val run = s.substring(start, i)
                    val v = parseChinese(run)
                    if (v != null) {
                        flushText()
                        out += Num(BigInteger.valueOf(v), run.length)
                    } else {
                        sb.append(run)
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        flushText()
        return out
    }

    /** 解析中文數字，如「十一」「一百零五」「二〇二四」。失敗回傳 null。 */
    private fun parseChinese(run: String): Long? {
        if (run.isEmpty()) return null
        // 純數字字元（沒有十百千）：當作逐位數，例如 二〇二四 = 2024
        if (run.all { it in cnDigits }) {
            var v = 0L
            for (c in run) v = v * 10 + cnDigits.getValue(c)
            return v
        }
        var total = 0L
        var section = 0L
        var number = 0L
        for (c in run) {
            when (c) {
                in cnDigits -> number = cnDigits.getValue(c).toLong()
                in cnUnits -> {
                    val unit = cnUnits.getValue(c)
                    if (number == 0L) number = 1L // 「十」= 10
                    section += number * unit
                    number = 0L
                }
                in cnBig -> {
                    val big = cnBig.getValue(c)
                    section += number
                    if (section == 0L) section = 1L
                    total += section * big
                    section = 0L
                    number = 0L
                }
                else -> return null
            }
        }
        return total + section + number
    }
}
