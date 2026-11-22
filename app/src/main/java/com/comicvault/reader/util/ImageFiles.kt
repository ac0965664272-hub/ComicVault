package com.comicvault.reader.util

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageFiles {
    val imageExt = setOf("jpg", "jpeg", "jfif", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    /** 明確不是圖片的副檔名 */
    private val otherExt = setOf(
        "mp4", "mkv", "mov", "avi", "3gp", "webm", "ts", "mp3", "m4a", "aac", "wav", "ogg", "flac", "amr",
        "txt", "json", "xml", "db", "db-journal", "db-wal", "log", "apk", "pdf", "zip", "rar", "7z",
        "html", "js", "css", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "srt", "lrc"
    )

    /** 沒有副檔名或副檔名被改掉的檔案（常見於隱藏相簿），只嘗試 60MB 以下的 */
    private const val MAX_UNKNOWN = 60L * 1024 * 1024

    fun ext(name: String): String {
        val base = name.substringAfterLast('/')
        val i = base.lastIndexOf('.')
        return if (i <= 0) "" else base.substring(i + 1).lowercase()
    }

    fun hasImageExt(name: String) = ext(name) in imageExt

    /** 是否值得嘗試當作圖片匯入（實際能不能讀，匯入時會再用解碼器確認） */
    fun isCandidate(name: String, mime: String?, size: Long): Boolean {
        val base = name.substringAfterLast('/')
        if (base.equals(".nomedia", ignoreCase = true) || size == 0L) return false
        if (base.startsWith("__MACOSX") || name.contains("__MACOSX/")) return false
        if (mime != null && mime.startsWith("image/")) return true
        val e = ext(base)
        if (e in imageExt) return true
        if (e in otherExt) return false
        if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("text/"))) return false
        return size < 0 || size <= MAX_UNKNOWN
    }

    /** 讀檔頭判斷是否為圖片（給副檔名不明的檔案用） */
    fun looksLikeImage(file: File): Boolean = runCatching {
        val b = ByteArray(16)
        val n = file.inputStream().use { it.read(b) }
        n >= 12 && looksLikeImage(b)
    }.getOrDefault(false)

    fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        fun ascii(from: Int, len: Int) = String(b, from, len, Charsets.US_ASCII)
        if (u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF) return true                       // JPEG
        if (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return true      // PNG
        if (ascii(0, 3) == "GIF") return true                                              // GIF
        if (ascii(0, 2) == "BM") return true                                               // BMP
        if (ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP") return true                    // WEBP
        if (ascii(4, 4) == "ftyp") {                                                       // HEIC / AVIF
            return ascii(8, 4) in setOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif", "avis")
        }
        return false
    }

    /** 回傳顯示用的寬高（已考慮 EXIF 旋轉），不是圖片則回傳 null */
    fun probeSize(file: File): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.path, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val rotation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return if (rotation in 5..8) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }
}
