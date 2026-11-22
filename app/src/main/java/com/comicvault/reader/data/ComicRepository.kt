package com.comicvault.reader.data

import android.content.Context
import android.os.Environment
import com.comicvault.reader.util.ImageFiles
import com.comicvault.reader.util.NaturalOrder
import com.comicvault.reader.util.Search
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 每部漫畫是一個資料夾：<存放位置>/<id>/，裡面有 meta.json 與圖片。
 * 每個存放位置的根目錄都會放 .nomedia，系統相簿不會掃描。
 */
class ComicRepository(private val context: Context) {
    private val internalRoot = File(context.filesDir, "comics")
    private val configFile = File(context.filesDir, "library.json")

    private var categories = mutableListOf<Category>()
    private var customRoots = mutableListOf<String>()
    private var defaultRootPath: String = internalRoot.absolutePath
    private val dirCache = HashMap<String, File>()

    init {
        ensureRoot(internalRoot)
        loadConfig()
    }

    // ───────────────────────────── 設定 / 類別 / 存放位置

    @Synchronized
    fun categories(): List<Category> = categories.toList()

    @Synchronized
    fun addCategory(name: String): Category {
        val existing = categories.find { it.name == name.trim() }
        if (existing != null) return existing
        val c = Category(UUID.randomUUID().toString(), name.trim().ifBlank { "新類別" })
        categories.add(c)
        saveConfig()
        return c
    }

    @Synchronized
    fun renameCategory(id: String, name: String) {
        val i = categories.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) {
            categories[i] = categories[i].copy(name = name.trim())
            saveConfig()
        }
    }

    fun deleteCategory(id: String) {
        synchronized(this) {
            categories.removeAll { it.id == id }
            saveConfig()
        }
        list().filter { it.categoryId == id }.forEach { save(it.copy(categoryId = null)) }
    }

    fun setCategory(ids: Collection<String>, categoryId: String?) {
        ids.forEach { id -> get(id)?.let { save(it.copy(categoryId = categoryId)) } }
    }

    fun locations(): List<StorageLocation> {
        val out = ArrayList<StorageLocation>()
        out += StorageLocation(
            internalRoot, "App 內部私有空間",
            "最隱密，其他 App 與電腦都看不到。解除安裝 App 會一併刪除。",
            StorageKind.Internal, removable = false,
        )
        context.getExternalFilesDirs(null).forEach { d ->
            if (d == null) return@forEach
            val removable = runCatching { Environment.isExternalStorageRemovable(d) }.getOrDefault(false)
            out += StorageLocation(
                File(d, "comics"),
                if (removable) "SD 卡（App 專屬資料夾）" else "手機儲存空間（App 專屬資料夾）",
                "不需要權限，相簿不會顯示。解除安裝 App 會一併刪除。",
                StorageKind.AppExternal, removable,
            )
        }
        synchronized(this) { customRoots.toList() }.forEach { p ->
            val f = File(p)
            val removable = runCatching { Environment.isExternalStorageRemovable(f) }.getOrDefault(false)
            out += StorageLocation(
                f, "自訂：" + prettyPath(f.parentFile ?: f),
                "解除安裝 App 後仍會保留（隱藏資料夾 + .nomedia，相簿不會顯示）。需要所有檔案存取權。",
                StorageKind.Custom, removable,
            )
        }
        return out.distinctBy { it.path }
    }

    @Synchronized
    fun defaultRoot(): File = File(defaultRootPath)

    @Synchronized
    fun setDefaultRoot(root: File) {
        defaultRootPath = root.absolutePath
        saveConfig()
    }

    /** 使用者選的資料夾 → 在裡面建立隱藏的 .ComicVault 資料夾當存放位置 */
    fun addCustomRoot(picked: File): StorageLocation? {
        val root = File(picked, ".ComicVault")
        if (!ensureRoot(root)) return null
        synchronized(this) {
            if (root.absolutePath !in customRoots) customRoots.add(root.absolutePath)
            saveConfig()
        }
        return locations().find { it.path == root.absolutePath }
    }

    @Synchronized
    fun removeCustomRoot(path: String): Boolean {
        val f = File(path)
        val hasComics = f.listFiles { x -> x.isDirectory }?.isNotEmpty() == true
        if (hasComics) return false
        customRoots.remove(path)
        if (defaultRootPath == path) defaultRootPath = internalRoot.absolutePath
        saveConfig()
        return true
    }

    // ───────────────────────────── 漫畫

    fun list(): List<Comic> {
        val found = ArrayList<Comic>()
        for (loc in locations()) {
            val dirs = runCatching { loc.root.listFiles { f -> f.isDirectory } }.getOrNull() ?: continue
            for (d in dirs) found += load(d) ?: continue
        }
        // 同一部出現在兩個位置（舊版搬遷沒刪乾淨）時，用最新的那份
        val all = found.groupBy { it.id }.values.map { copies ->
            copies.maxByOrNull { File(it.dir, META).lastModified() } ?: copies.first()
        }
        synchronized(dirCache) {
            dirCache.clear()
            all.forEach { dirCache[it.id] = it.dir }
        }
        return all.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }
    }

    fun get(id: String): Comic? {
        synchronized(dirCache) { dirCache[id] }?.let { d -> load(d)?.let { return it } }
        for (loc in locations()) {
            val d = File(loc.root, id)
            if (d.isDirectory) load(d)?.let { c ->
                synchronized(dirCache) { dirCache[id] = d }
                return c
            }
        }
        return null
    }

    fun create(root: File, title: String, categoryId: String?): Comic {
        ensureRoot(root)
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("無法在 ${prettyPath(root)} 建立資料夾，請確認權限或空間")
        val c = Comic(id, title.ifBlank { "未命名漫畫" }, categoryId, System.currentTimeMillis(), 0, emptyList(), dir)
        save(c)
        synchronized(dirCache) { dirCache[id] = dir }
        return c
    }

    fun rename(id: String, title: String) {
        get(id)?.let { save(it.copy(title = title.ifBlank { it.title })) }
    }

    fun delete(id: String) {
        get(id)?.dir?.deleteRecursively()
        synchronized(dirCache) { dirCache.remove(id) }
    }

    fun setLastPage(id: String, page: Int) {
        get(id)?.let { if (it.lastPage != page) save(it.copy(lastPage = page)) }
    }

    // ───────────────────────────── 章節

    fun moveChapter(id: String, from: Int, to: Int) {
        val c = get(id) ?: return
        if (from !in c.chapters.indices || to !in c.chapters.indices) return
        val list = c.chapters.toMutableList()
        val ch = list.removeAt(from)
        list.add(to, ch)
        save(c.copy(chapters = list, lastPage = 0))
    }

    fun renameChapter(id: String, index: Int, name: String) {
        val c = get(id) ?: return
        if (index !in c.chapters.indices) return
        val list = c.chapters.toMutableList()
        list[index] = list[index].copy(name = name.trim())
        save(c.copy(chapters = list))
    }

    fun deleteChapter(id: String, index: Int) {
        val c = get(id) ?: return
        if (index !in c.chapters.indices) return
        c.chapters[index].pages.forEach { c.fileOf(it).delete() }
        val list = c.chapters.toMutableList().apply { removeAt(index) }
        save(c.copy(chapters = list, lastPage = 0))
    }

    // ───────────────────────────── 作者 / 標籤

    /** 設定作者：舊作者的標籤換成新作者的標籤 */
    fun setAuthor(ids: Collection<String>, author: String) {
        val newAuthors = Search.splitAuthors(author)
        ids.forEach { id ->
            get(id)?.let { c ->
                val tags = (c.tags - c.authors.toSet() + newAuthors).distinct()
                save(c.copy(author = newAuthors.joinToString("、"), tags = tags))
            }
        }
    }

    /** 把某位作者在所有作品中改名 */
    fun renameAuthor(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty()) return
        list().filter { old in it.authors }.forEach { c ->
            val authors = c.authors.map { if (it == old) n else it }.distinct()
            val tags = c.tags.map { if (it == old) n else it }.distinct()
            save(c.copy(author = authors.joinToString("、"), tags = tags))
        }
    }

    fun addTags(ids: Collection<String>, tags: List<String>) {
        val clean = tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        ids.forEach { id -> get(id)?.let { save(it.copy(tags = (it.tags + clean).distinct())) } }
    }

    fun removeTag(id: String, tag: String) {
        get(id)?.let { save(it.copy(tags = it.tags - tag)) }
    }

    // ───────────────────────────── 合併

    /** 依 ids 的順序合併成一部新漫畫，原本的漫畫會被移除。 */
    fun merge(ids: List<String>, title: String, categoryId: String?, onProgress: (Int, Int) -> Unit): Comic {
        val comics = ids.mapNotNull { get(it) }
        require(comics.size >= 2) { "至少要選兩部漫畫" }
        val total = comics.sumOf { it.chapters.size }
        require(total <= MAX_CHAPTERS) { "合併後共 $total 話，超過上限 $MAX_CHAPTERS 話" }

        val target = create(comics.first().root, title, categoryId)
        val totalFiles = comics.sumOf { it.pages.size }
        var done = 0
        val chapters = ArrayList<Chapter>()
        for (c in comics) {
            for (ch in c.chapters) {
                val moved = ch.pages.map { p ->
                    if (!moveFile(c.fileOf(p), File(target.dir, p.file))) {
                        throw IllegalStateException("搬移圖片失敗：${p.name}")
                    }
                    onProgress(++done, totalFiles)
                    p
                }
                // 只有一話的漫畫，用漫畫名稱當這一話的副標題
                val name = if (c.chapters.size == 1) c.title else ch.name.ifBlank { c.title }
                chapters += Chapter(name, moved)
            }
        }
        val merged = target.copy(
            chapters = chapters,
            author = comics.map { it.author }.firstOrNull { it.isNotBlank() } ?: "",
            tags = comics.flatMap { it.tags }.distinct(),
        )
        save(merged)
        comics.forEach { it.dir.deleteRecursively() }
        return merged
    }

    // ───────────────────────────── 搬遷

    enum class MoveResult { Moved, AlreadyThere, MovedSourceLeft, Failed }

    /**
     * 把漫畫整個資料夾搬到另一個存放位置，搬完會刪除原本的檔案。
     * 同一個磁碟直接改名；跨磁碟（例如到 SD 卡）先複製、逐一核對大小，再刪除原檔。
     */
    fun moveComic(c: Comic, targetRoot: File, onFile: () -> Unit): MoveResult {
        val dest = File(targetRoot, c.id)
        if (dest.absolutePath == c.dir.absolutePath) return MoveResult.AlreadyThere
        if (!ensureRoot(targetRoot)) return MoveResult.Failed

        // 目的地已經有一份（例如上次搬到一半）：完整就直接用，不完整就重來
        if (dest.exists()) {
            if (isCompleteCopy(c, dest)) {
                repeat(c.pages.size) { onFile() }
                return finishMove(c, dest)
            }
            dest.deleteRecursively()
        }

        if (c.dir.renameTo(dest)) {
            repeat(c.pages.size) { onFile() }
            synchronized(dirCache) { dirCache[c.id] = dest }
            return MoveResult.Moved
        }

        if (!dest.mkdirs() && !dest.isDirectory) return MoveResult.Failed
        val copied = runCatching {
            val files = c.dir.listFiles() ?: emptyArray()
            for (f in files) {
                if (!f.isFile || f.name.endsWith(".tmp")) continue
                val out = File(dest, f.name)
                f.copyTo(out, overwrite = true, bufferSize = 64 * 1024)
                if (out.length() != f.length()) error("複製不完整：${f.name}")
                if (f.name != META) onFile()
            }
            true
        }.getOrDefault(false)
        if (!copied || !isCompleteCopy(c, dest)) {
            dest.deleteRecursively()
            return MoveResult.Failed
        }
        return finishMove(c, dest)
    }

    /** 目的地檔案都齊全後，刪掉原本的資料夾 */
    private fun finishMove(c: Comic, dest: File): MoveResult {
        synchronized(dirCache) { dirCache[c.id] = dest }
        // 先刪 meta.json，就算後面有檔案刪不掉，舊位置也不會再被當成一部漫畫
        File(c.dir, META).delete()
        c.dir.deleteRecursively()
        if (c.dir.exists()) {
            c.dir.listFiles()?.forEach { it.delete() }
            c.dir.delete()
        }
        return if (c.dir.exists()) MoveResult.MovedSourceLeft else MoveResult.Moved
    }

    private fun isCompleteCopy(c: Comic, dest: File): Boolean {
        if (load(dest)?.id != c.id) return false
        return c.pages.all { p ->
            val src = c.fileOf(p)
            val dst = File(dest, p.file)
            dst.isFile && (!src.exists() || dst.length() == src.length())
        }
    }

    /** 每部漫畫的頁數與各頁檔案大小（偵測重複匯入用） */
    fun fingerprints(): List<com.comicvault.reader.util.Fingerprint> = list().map { c ->
        com.comicvault.reader.util.Fingerprint(c.id, c.title, c.pages.map { c.fileOf(it).length() })
    }

    // ───────────────────────────── 匯入

    /**
     * 把規劃好的章節複製進漫畫。targetId 不為 null 時加到既有漫畫的最後面。
     * onFile 每處理一個檔案呼叫一次。
     */
    fun importComic(
        root: File,
        title: String,
        categoryId: String?,
        planned: List<PlannedChapter>,
        targetId: String?,
        deleteOriginals: Boolean,
        onFile: () -> Unit,
        author: String = "",
    ): ImportResult {
        val existing = targetId?.let { get(it) }
        if (existing != null && existing.chapters.size + planned.size > MAX_CHAPTERS) {
            throw IllegalStateException("「${existing.title}」已有 ${existing.chapters.size} 話，最多只能再加 ${MAX_CHAPTERS - existing.chapters.size} 話")
        }
        val comic = existing ?: create(root, title, categoryId).let { c ->
            // 匯入時自動把作者名字加成標籤
            val withAuthor = c.copy(author = author.trim(), tags = Search.splitAuthors(author))
            save(withAuthor)
            withAuthor
        }
        var added = 0
        var skipped = 0
        var deleted = 0
        var failed = 0
        val newChapters = ArrayList<Chapter>()

        for (pc in planned) {
            val pages = ArrayList<Page>()
            val sorted = pc.items.sortedWith { a, b -> NaturalOrder.compareFileNames(a.path, b.path) }
            for (c in sorted) {
                onFile()
                val ext = ImageFiles.ext(c.path).takeIf { it in ImageFiles.imageExt } ?: "img"
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
                    continue
                }
                pages += Page(out.name, c.path, size.first, size.second)
                added++
                if (deleteOriginals) {
                    val del = c.deleteOriginal
                    if (del != null) {
                        if (runCatching { del() }.getOrDefault(false)) deleted++ else failed++
                    }
                }
            }
            if (pages.isNotEmpty()) newChapters += Chapter(pc.name, pages)
        }

        val latest = get(comic.id) ?: comic
        if (latest.chapters.isEmpty() && newChapters.isEmpty()) {
            latest.dir.deleteRecursively()
            return ImportResult(0, 0, skipped, deleted, failed)
        }
        save(latest.copy(chapters = (latest.chapters + newChapters).take(MAX_CHAPTERS)))
        return ImportResult(if (existing == null) 1 else 0, added, skipped, deleted, failed)
    }

    // ───────────────────────────── 檔案 / JSON

    private fun ensureRoot(root: File): Boolean {
        if (!root.isDirectory && !runCatching { root.mkdirs() }.getOrDefault(false)) return false
        runCatching { File(root, ".nomedia").createNewFile() }
        return root.isDirectory && root.canWrite()
    }

    private fun moveFile(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        return runCatching {
            src.copyTo(dst, overwrite = true, bufferSize = 64 * 1024)
            src.delete()
            true
        }.getOrDefault(false)
    }

    private fun load(dir: File): Comic? = runCatching {
        val json = JSONObject(File(dir, META).readText())
        fun pagesOf(arr: JSONArray) = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Page(o.getString("file"), o.getString("name"), o.optInt("w"), o.optInt("h"))
        }
        val chapters = if (json.has("chapters")) {
            val arr = json.getJSONArray("chapters")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Chapter(o.optString("name"), pagesOf(o.getJSONArray("pages")))
            }
        } else {
            // 舊版格式：只有 pages
            listOf(Chapter("", pagesOf(json.getJSONArray("pages"))))
        }
        Comic(
            id = json.getString("id"),
            title = json.getString("title"),
            categoryId = json.optString("category").ifBlank { null },
            created = json.optLong("created"),
            lastPage = json.optInt("lastPage"),
            chapters = chapters.filter { it.pages.isNotEmpty() },
            dir = dir,
            author = json.optString("author"),
            tags = json.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }.getOrNull()

    fun save(c: Comic) {
        val chArr = JSONArray()
        c.chapters.forEach { ch ->
            val arr = JSONArray()
            ch.pages.forEach {
                arr.put(JSONObject().put("file", it.file).put("name", it.name).put("w", it.w).put("h", it.h))
            }
            chArr.put(JSONObject().put("name", ch.name).put("pages", arr))
        }
        val json = JSONObject()
            .put("version", 2)
            .put("id", c.id)
            .put("title", c.title)
            .put("category", c.categoryId ?: "")
            .put("created", c.created)
            .put("lastPage", c.lastPage.coerceIn(0, (c.pages.size - 1).coerceAtLeast(0)))
            .put("chapters", chArr)
            .put("author", c.author)
            .put("tags", JSONArray().apply { c.tags.forEach { put(it) } })
            .toString()
        writeAtomic(File(c.dir, META), json)
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    private fun loadConfig() = synchronized(this) {
        runCatching {
            val j = JSONObject(configFile.readText())
            val cats = j.optJSONArray("categories") ?: JSONArray()
            categories = (0 until cats.length()).map {
                val o = cats.getJSONObject(it)
                Category(o.getString("id"), o.getString("name"))
            }.toMutableList()
            val roots = j.optJSONArray("customRoots") ?: JSONArray()
            customRoots = (0 until roots.length()).map { roots.getString(it) }.toMutableList()
            defaultRootPath = j.optString("defaultRoot").ifBlank { internalRoot.absolutePath }
        }
    }

    private fun saveConfig() {
        val cats = JSONArray()
        categories.forEach { cats.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val roots = JSONArray()
        customRoots.forEach { roots.put(it) }
        val j = JSONObject().put("categories", cats).put("customRoots", roots).put("defaultRoot", defaultRootPath)
        writeAtomic(configFile, j.toString())
    }

    companion object {
        private const val META = "meta.json"

        fun prettyPath(f: File): String {
            val p = f.absolutePath
            val primary = Environment.getExternalStorageDirectory().absolutePath
            return when {
                p.startsWith(primary) -> "手機" + p.removePrefix(primary).ifEmpty { "/" }
                p.startsWith("/storage/") -> "SD卡/" + p.removePrefix("/storage/").substringAfter('/', "")
                else -> p
            }
        }
    }
}
