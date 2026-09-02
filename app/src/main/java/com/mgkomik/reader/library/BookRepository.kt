package com.mgkomik.reader.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mgkomik.reader.util.bytesToHuman
import com.mgkomik.reader.util.formatDate
import com.mgkomik.reader.util.sanitizeFileName
import com.mgkomik.reader.util.sha1
import com.mgkomik.reader.util.uniqueFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Repository for locally saved CBZ files, organised by series:
 *
 *   <externalFilesDir>/library/
 *       Punishing Gray Raven/
 *           Punishing Gray Raven - Chapter 05.cbz
 *           Punishing Gray Raven - Chapter 05.cbz.json
 *       Other Series/
 *           ...
 *
 * Metadata (title/chapter/source/date) is stored in a sidecar JSON per file.
 */
class BookRepository(private val context: Context) {

    private val libraryDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "library")

    fun ensureDir(): File = libraryDir.apply { if (!exists()) mkdirs() }

    /** Returns all series folders (sorted by most recently updated). */
    fun listSeries(): List<MangaSeries> {
        ensureDir()
        return libraryDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { folder ->
                val books = readBooksIn(folder)
                if (books.isEmpty()) {
                    // Skip empty leftover folders
                    null
                } else {
                    MangaSeries(
                        title = folder.name,
                        folder = folder,
                        books = books
                    )
                }
            }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }

    fun getSeries(title: String): MangaSeries? =
        listSeries().firstOrNull { it.title.equals(title, ignoreCase = true) }

    /** Chapters of a series, newest first. */
    fun listChapters(series: MangaSeries): List<Book> =
        series.books.sortedByDescending { it.addedAt }

    fun getBookById(id: String): Book? =
        listSeries().flatMap { it.books }.firstOrNull { it.id == id }

    /** Directory for a series title. */
    fun seriesDir(title: String): File {
        ensureDir()
        return File(libraryDir, sanitizeFileName(title)).apply { mkdirs() }
    }

    fun writeMeta(file: File, title: String, chapter: String, sourceUrl: String, pages: Int) {
        val metaFile = File(file.parentFile, "${file.name}.json")
        val obj = JSONObject().apply {
            put("title", title)
            put("chapter", chapter)
            put("sourceUrl", sourceUrl)
            put("pages", pages)
            put("addedAt", System.currentTimeMillis())
        }
        metaFile.writeText(obj.toString())
    }

    private fun readMeta(file: File): JSONObject? {
        val metaFile = File(file.parentFile, "${file.name}.json")
        return try {
            if (metaFile.exists()) JSONObject(metaFile.readText()) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readBooksIn(folder: File): List<Book> =
        folder.listFiles { f -> f.isFile && f.extension.equals("cbz", true) }
            ?.mapNotNull { file ->
                val meta = readMeta(file)
                Book(
                    id = file.nameWithoutExtension,
                    title = meta?.optString("title").orEmpty().ifBlank { folder.name },
                    chapter = meta?.optString("chapter").orEmpty(),
                    sourceUrl = meta?.optString("sourceUrl").orEmpty(),
                    file = file,
                    pageCount = meta?.optInt("pages", 0) ?: 0,
                    sizeBytes = file.length(),
                    addedAt = meta?.optLong("addedAt", file.lastModified()) ?: file.lastModified()
                )
            }
            .orEmpty()

    fun deleteBook(book: Book) {
        book.file.delete()
        File(book.file.parentFile, "${book.file.name}.json").delete()
        // Remove the series folder when its last chapter is gone.
        book.file.parentFile?.takeIf { it.isDirectory && it.listFiles()?.isEmpty() == true }?.delete()
    }

    fun deleteSeries(series: MangaSeries) {
        series.folder.deleteRecursively()
    }

    fun importCbz(uri: Uri, titleHint: String? = null): Result<Book> = runCatching {
        val name = queryName(uri) ?: "imported"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Can't read file")
        if (bytes.isEmpty()) error("Empty file")

        val cleanBase = sanitizeFileName(name.removeSuffix(".cbz").removeSuffix(".CBZ"))
        // Split "Title - Chapter 05" or use hint; default to the whole base name.
        val title = titleHint ?: cleanBase.substringBefore(" - ").ifBlank { cleanBase }
        val folder = seriesDir(title)
        val file = uniqueFile(folder, cleanBase, ".cbz")
        file.writeBytes(bytes)

        val chapter = cleanBase.substringAfter(" - ", "").ifBlank { "" }
        writeMeta(file, title, chapter, "", 0)
        Book(
            id = file.nameWithoutExtension,
            title = title,
            chapter = chapter,
            sourceUrl = "",
            file = file,
            pageCount = 0,
            sizeBytes = file.length(),
            addedAt = System.currentTimeMillis()
        )
    }

    private fun queryName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    fun bookLineage(book: Book): String {
        val parts = buildList {
            add(book.displayChapter)
            if (book.pageCount > 0) add("${book.pageCount} halaman")
            add(bytesToHuman(book.sizeBytes))
            add(formatDate(book.addedAt))
        }
        return parts.joinToString(" • ")
    }

    /** Persist series list as JSON (used for any future sync/backup). */
    fun exportIndex(): JSONArray {
        val arr = JSONArray()
        listSeries().forEach { s ->
            val series = JSONObject().apply {
                put("title", s.title)
                put("chapters", s.chapterCount)
                put("sizeBytes", s.sizeBytes)
                put("updatedAt", s.updatedAt)
            }
            arr.put(series)
        }
        return arr
    }

    /**
     * One-time migration: move any loose .cbz files directly inside the library
     * root into per-series folders (using each file's metadata title).
     */
    fun migrateLooseFiles() {
        ensureDir()
        val rootFiles = libraryDir.listFiles { f -> f.isFile && f.extension.equals("cbz", true) }
            .orEmpty()
        for (file in rootFiles) {
            val meta = readMeta(file)
            val title = meta?.optString("title").orEmpty().ifBlank { file.nameWithoutExtension }
            val folder = seriesDir(title)
            if (folder != file.parentFile) {
                val target = uniqueFile(folder, file.nameWithoutExtension, ".cbz")
                if (file.renameTo(target)) {
                    val oldMeta = File(file.parentFile, "${file.name}.json")
                    if (oldMeta.exists()) {
                        oldMeta.renameTo(File(folder, "${target.name}.json"))
                    }
                }
            }
        }
    }

    companion object {
        fun chapterId(sourceUrl: String): String = sha1(sourceUrl).take(16)
    }
}
