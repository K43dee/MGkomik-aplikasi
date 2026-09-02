package com.mgkomik.reader.library

import java.io.File

data class Book(
    val id: String,
    val title: String,
    val chapter: String,
    val sourceUrl: String,
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long,
    val addedAt: Long
) {
    val displayTitle: String get() = title.ifBlank { file.nameWithoutExtension }

    val displayChapter: String get() = chapter.ifBlank { "-" }
}

/** A comic series = a folder in the library containing its downloaded chapters. */
data class MangaSeries(
    val title: String,
    val folder: File,
    val books: List<Book>
) {
    val pageCount: Int get() = books.sumOf { it.pageCount }
    val sizeBytes: Long get() = books.sumOf { it.sizeBytes }
    val updatedAt: Long get() = books.maxOfOrNull { it.addedAt } ?: folder.lastModified()
    val chapterCount: Int get() = books.size
}
