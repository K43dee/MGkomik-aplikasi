package com.mgkomik.reader.library

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Simple CBZ (ZIP) reader that exposes page images sorted naturally. */
class CbzArchive(private val file: File) {

    val entries: List<String> by lazy { readEntries() }

    fun pageCount(): Int = entries.size

    /** Reads a page's bytes; the ZipFile is kept open during the read. */
    fun readEntryBytes(entryName: String): ByteArray? {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readEntries(): List<String> {
        return try {
            ZipFile(file).use { zip ->
                zip.entries()
                    .asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter(::isImageName)
                    .sortedWith(::compareNames)
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isImageName(name: String): Boolean =
        name.substringAfterLast('.').lowercase() in IMAGE_EXTS

    private fun compareNames(a: String, b: String): Int {
        val ae = a.substringAfterLast('.').lowercase()
        val be = b.substringAfterLast('.').lowercase()
        if (ae != be) return a.compareTo(b)
        val an = a.substringBeforeLast('.').extractNumber()
        val bn = b.substringBeforeLast('.').extractNumber()
        if (an != null && bn != null && an != bn) return an.compareTo(bn)
        return a.compareTo(b)
    }

    private fun String.extractNumber(): Int? =
        Regex("""\d+""").find(this)?.value?.toIntOrNull()

    private companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}
