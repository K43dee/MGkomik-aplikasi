package com.mgkomik.reader.library

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a CBZ (zip of images) incrementally, page by page. */
class CbzWriter(
    private val output: File,
    private val onPage: (Int, Int) -> Unit,
    private val onError: (Int, String) -> Unit
) {
    private val zip = ZipOutputStream(output.outputStream())
    private var pagesWritten = 0

    /** Writes one page; returns true on success. */
    fun addPage(imageBytes: ByteArray): Boolean {
        pagesWritten++
        val name = "%04d.%s".format(pagesWritten, extOf(imageBytes))
        return try {
            zip.putNextEntry(ZipEntry(name))
            zip.write(imageBytes)
            zip.closeEntry()
            onPage(pagesWritten, imageBytes.size)
            true
        } catch (e: Exception) {
            onError(pagesWritten, e.message ?: "write error")
            false
        }
    }

    fun finish() {
        try {
            zip.close()
        } catch (e: Exception) {
            // best-effort
        }
    }

    private fun extOf(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
        bytes.size > 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() -> "gif"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size > 11 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "webp"
        else -> "jpg"
    }
}
