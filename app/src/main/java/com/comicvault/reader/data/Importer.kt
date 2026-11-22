package com.comicvault.reader.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.comicvault.reader.util.ImageFiles
import com.comicvault.reader.util.NaturalOrder
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** 把各種來源讀成 Source（只保留確定是圖片的檔案） */
object Importer {

    /** 系統檔案選擇器多選的圖片 → 一個來源 */
    fun fromDocuments(ctx: Context, uris: List<Uri>): Source {
        val cr = ctx.contentResolver
        val cands = uris.map { uri ->
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
            var size = -1L
            runCatching {
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            Candidate(
                path = name,
                size = size,
                mime = runCatching { cr.getType(uri) }.getOrNull(),
                preview = uri,
                open = { cr.openInputStream(uri) },
                deleteOriginal = { DocumentsContract.deleteDocument(cr, uri) },
            )
        }
        return Source(suggestTitle(cands.map { it.path }), filterImages(cands))
    }

    /** 整個資料夾（含子資料夾、含隱藏檔） */
    fun fromTree(ctx: Context, treeUri: Uri): Source {
        val cr = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = queryName(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId))
            ?: rootId.substringAfterLast('/').substringAfterLast(':').ifBlank { "資料夾" }
        val out = ArrayList<Candidate>()
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )

        fun walk(docId: String, prefix: String, depth: Int) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val dirs = ArrayList<Pair<String, String>>()
            cr.query(childrenUri, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    val size = if (c.isNull(3)) -1L else c.getLong(3)
                    if (mime == Document.MIME_TYPE_DIR) {
                        dirs += id to name
                    } else if (ImageFiles.isCandidate(name, mime, size)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        out += Candidate(
                            path = prefix + name,
                            size = size,
                            mime = mime,
                            preview = docUri,
                            open = { cr.openInputStream(docUri) },
                            deleteOriginal = { DocumentsContract.deleteDocument(cr, docUri) },
                        )
                    }
                }
            }
            if (depth < 8) for ((id, name) in dirs) walk(id, "$prefix$name/", depth + 1)
        }

        walk(rootId, "$rootName/", 0)
        return Source(cleanTitle(rootName), filterImages(out))
    }

    /** ZIP / CBZ 壓縮檔，自動嘗試 UTF-8、Big5、GBK 檔名編碼 */
    fun fromZip(ctx: Context, uri: Uri): Source {
        val cr = ctx.contentResolver
        val zipName = queryName(cr, uri) ?: "壓縮檔"
        val tmp = File(ctx.cacheDir, "zip_${System.nanoTime()}").apply { mkdirs() }
        val charsets = listOf("UTF-8", "Big5", "GBK", "ISO-8859-1")
            .mapNotNull { runCatching { Charset.forName(it) }.getOrNull() }

        var result: List<Candidate>? = null
        var lastError: Throwable? = null
        for (cs in charsets) {
            tmp.listFiles()?.forEach { it.delete() }
            val r = runCatching { extract(cr, uri, cs, tmp) }
            if (r.isSuccess) {
                result = r.getOrNull()
                break
            }
            lastError = r.exceptionOrNull()
            if (lastError !is IllegalArgumentException) break // 不是檔名編碼問題就不重試
        }
        if (result == null) {
            tmp.deleteRecursively()
            throw lastError ?: IOException("無法讀取壓縮檔")
        }
        return Source(
            label = cleanTitle(zipName.substringBeforeLast('.')),
            images = filterImages(result),
            deleteSource = { DocumentsContract.deleteDocument(cr, uri) },
            cleanup = { tmp.deleteRecursively() },
        )
    }

    private fun extract(cr: ContentResolver, uri: Uri, cs: Charset, tmp: File): List<Candidate> {
        val list = ArrayList<Candidate>()
        val input = cr.openInputStream(uri) ?: throw IOException("無法開啟檔案")
        ZipInputStream(input.buffered(), cs).use { zis ->
            var idx = 0
            while (true) {
                val e = zis.nextEntry ?: break
                if (!e.isDirectory && ImageFiles.isCandidate(e.name, null, e.size)) {
                    // 用流水號存檔，不使用壓縮檔內的路徑（避免路徑穿越）
                    val f = File(tmp, "e${idx++}")
                    f.outputStream().use { zis.copyTo(it, 64 * 1024) }
                    list += Candidate(e.name.trimStart('/'), f.length(), null, f, { f.inputStream() }, null)
                }
            }
        }
        return list
    }

    /** 直接用檔案路徑讀取（隱藏相簿掃描結果，需要所有檔案存取權） */
    fun fromDirectory(ctx: Context, dir: File): Source {
        val out = ArrayList<Candidate>()
        fun walk(d: File, prefix: String, depth: Int) {
            val kids = d.listFiles() ?: return
            for (f in kids) {
                if (f.isFile && ImageFiles.isCandidate(f.name, null, f.length())) {
                    out += Candidate(
                        path = prefix + f.name,
                        size = f.length(),
                        mime = null,
                        preview = f,
                        open = { f.inputStream() },
                        deleteOriginal = {
                            val ok = f.delete()
                            if (ok) MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
                            ok
                        },
                    )
                }
            }
            if (depth < 4) for (k in kids) if (k.isDirectory) walk(k, "$prefix${k.name}/", depth + 1)
        }
        walk(dir, "${dir.name}/", 0)
        return Source(cleanTitle(dir.name), filterImages(out))
    }

    /** 只留下真的是圖片的檔案；副檔名不明的會讀檔頭確認 */
    private fun filterImages(list: List<Candidate>): List<Candidate> = list.filter { c ->
        when {
            c.mime?.startsWith("image/") == true -> true
            ImageFiles.hasImageExt(c.path) -> true
            !ImageFiles.isCandidate(c.path, c.mime, c.size) -> false
            else -> runCatching {
                val b = ByteArray(16)
                val n = c.open()?.use { it.read(b) } ?: 0
                n >= 12 && ImageFiles.looksLikeImage(b)
            }.getOrDefault(false)
        }
    }

    /** SAF 選到的資料夾 → 實際路徑（自訂存放位置用） */
    fun treeUriToFile(treeUri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (docId.startsWith("raw:")) return File(docId.removePrefix("raw:"))
        val parts = docId.split(":", limit = 2)
        if (parts.size < 2 && !docId.endsWith(":")) return null
        val volume = parts[0]
        val rel = parts.getOrElse(1) { "" }
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else if (volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
            File("/storage/$volume")
        } else {
            return null
        }
        return if (rel.isEmpty()) base else File(base, rel)
    }

    private fun queryName(cr: ContentResolver, uri: Uri): String? = runCatching {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun cleanTitle(s: String): String = s.trimStart('.').trim().ifBlank { s.ifBlank { "新漫畫" } }

    /** 從檔名猜漫畫名稱：「海賊王_001.jpg」→「海賊王」 */
    fun suggestTitle(names: List<String>): String {
        val first = names.minWithOrNull(NaturalOrder.fileNameComparator) ?: return "新漫畫"
        val base = first.substringAfterLast('/').substringBeforeLast('.')
        val t = base.replace(Regex("[\\s_\\-.#()（）\\[\\]【】]*[0-9０-９]+[\\s_\\-.()（）\\[\\]】]*$"), "").trim()
        return cleanTitle(t.ifBlank { "新漫畫" })
    }
}
