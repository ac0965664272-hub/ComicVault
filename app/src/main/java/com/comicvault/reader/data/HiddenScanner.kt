package com.comicvault.reader.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.comicvault.reader.util.ImageFiles
import com.comicvault.reader.util.NaturalOrder
import java.io.File

data class HiddenFolder(
    val dir: File,
    val path: String,
    val imageCount: Int,
    val preview: File?,
    val reason: String,
    val isCache: Boolean,
)

/**
 * 找出相簿 App 看不到的圖片資料夾：
 *  1. 名稱以「.」開頭的隱藏資料夾（例如 .hidden、.private）
 *  2. 含有 .nomedia 的資料夾（系統相簿會略過）
 *  3. 位在上述資料夾底下的子資料夾
 * 副檔名被改掉或沒有副檔名的檔案，會讀檔頭判斷是不是圖片。
 */
object HiddenScanner {

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun accessIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun fallbackAccessIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    private val cacheNames = setOf(".thumbnails", ".thumbcache", ".cache", ".trash", ".trashed", ".recycle", ".recyclebin")

    fun scan(onProgress: (String) -> Unit): List<HiddenFolder> {
        val root = Environment.getExternalStorageDirectory()
        val rootPath = root.absolutePath
        val result = ArrayList<HiddenFolder>()
        var visited = 0

        fun countImages(files: List<File>): Pair<Int, File?> {
            var n = 0
            var first: File? = null
            var sniffed = 0
            for (f in files.sortedWith { a, b -> NaturalOrder.compareFileNames(a.name, b.name) }) {
                val ok = when {
                    !ImageFiles.isCandidate(f.name, null, f.length()) -> false
                    ImageFiles.hasImageExt(f.name) -> true
                    sniffed++ < 300 -> ImageFiles.looksLikeImage(f)
                    else -> false
                }
                if (ok) {
                    n++
                    if (first == null) first = f
                }
            }
            return n to first
        }

        fun walk(dir: File, depth: Int, parentHidden: Boolean, parentCache: Boolean) {
            val kids = dir.listFiles() ?: return
            if (++visited % 40 == 0) onProgress(dir.absolutePath.removePrefix(rootPath).ifEmpty { "/" })
            val dotName = dir != root && dir.name.startsWith(".")
            val noMedia = kids.any { it.name.equals(".nomedia", ignoreCase = true) }
            val isCache = parentCache || dir.name.lowercase() in cacheNames
            if (dotName || noMedia || parentHidden) {
                val (n, first) = countImages(kids.filter { it.isFile })
                if (n > 0) {
                    val reason = when {
                        isCache -> "系統縮圖／快取（通常不是你要找的）"
                        dotName -> "隱藏資料夾（名稱以 . 開頭）"
                        noMedia -> "含 .nomedia，相簿不會顯示"
                        else -> "位於隱藏資料夾之中"
                    }
                    result += HiddenFolder(
                        dir = dir,
                        path = dir.absolutePath.removePrefix(rootPath).ifEmpty { "/" },
                        imageCount = n,
                        preview = first,
                        reason = reason,
                        isCache = isCache,
                    )
                }
            }
            if (depth >= 10) return
            for (k in kids) {
                if (!k.isDirectory) continue
                if (dir == root && k.name == "Android") continue // Android/data 系統不允許讀取
                walk(k, depth + 1, parentHidden || dotName || noMedia, isCache)
            }
        }

        walk(root, 0, parentHidden = false, parentCache = false)
        return result.sortedWith(compareBy<HiddenFolder> { it.isCache }.thenByDescending { it.imageCount })
    }
}
