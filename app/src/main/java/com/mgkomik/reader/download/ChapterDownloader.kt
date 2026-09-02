package com.mgkomik.reader.download

import android.content.Context
import com.mgkomik.reader.library.BookRepository
import com.mgkomik.reader.library.CbzWriter
import com.mgkomik.reader.util.resolveUrl
import com.mgkomik.reader.util.sanitizeFileName
import com.mgkomik.reader.util.uniqueFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class DownloadResult(
    val file: File,
    val pageCount: Int,
    val totalBytes: Long,
    val title: String,
    val chapter: String
)

class ChapterDownloader(
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val repo = BookRepository(context)

    /** Downloads image URLs into a CBZ inside the series folder and registers it. */
    suspend fun downloadChapter(
        imageUrls: List<String>,
        pageUrl: String,
        onProgress: (done: Int, total: Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(imageUrls.isNotEmpty()) { "No images" }

        val title = guessTitle(pageUrl)
        val chapter = guessChapter(pageUrl)
        val chapterFileBase = sanitizeFileName("$title - $chapter")
        // One sub-folder per series:  library/<Title>/<Title> - Chapter XX.cbz
        val outDir = repo.seriesDir(title)
        val outFile = uniqueFile(outDir, chapterFileBase, ".cbz")

        val writer = CbzWriter(
            output = outFile,
            onPage = { _, _ -> },
            onError = { _, _ -> }
        )

        var total = 0L
        var okCount = 0
        val totalCount = imageUrls.size

        try {
            imageUrls.forEachIndexed { i, raw ->
                val url = resolveUrl(pageUrl, raw)
                val bytes = fetch(url)
                if (bytes != null && bytes.isNotEmpty()) {
                    writer.addPage(bytes)
                    total += bytes.size
                    okCount++
                }
                onProgress(i + 1, totalCount)
            }
            if (okCount == 0) {
                writer.finish()
                outFile.delete()
                error("Gagal mengunduh semua gambar")
            }
            writer.finish()
            repo.writeMeta(outFile, title, chapter, pageUrl, okCount)
            DownloadResult(outFile, okCount, total, title, chapter)
        } catch (e: Exception) {
            writer.finish()
            outFile.delete()
            throw e
        }
    }

    private fun fetch(url: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://web1.mgkomik.cc/")
                .header("User-Agent", UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Extracts the manga/comic title from a URL like .../komik/{slug}/chapter-06/. */
    private fun guessTitle(pageUrl: String): String {
        val clean = pageUrl.trimEnd('/')
        // Strip any query/hash
        val path = clean.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotBlank() }

        // Find the segment right before the "chapter-..." segment
        val idx = segments.indexOfFirst { it.startsWith("chapter", ignoreCase = true) }
        val slug = when {
            idx > 0 -> segments[idx - 1]
            // fallback: segment right after "/komik/"
            else -> {
                val k = segments.indexOfFirst { it.equals("komik", ignoreCase = true) }
                segments.getOrNull(k + 1)
                    ?: segments.getOrNull(segments.size - 2)
                    ?: segments.lastOrNull().orEmpty()
            }
        }
        return slugToTitle(slug)
    }

    private fun guessChapter(pageUrl: String): String {
        val clean = pageUrl.trimEnd('/')
        val seg = clean.substringAfterLast('/')
        // Matches chapter-06, chapter_06, chapter 06, chapter-0-1 (=0.1), chapter-0-2
        val m = Regex("""chapter[-_ ]?([0-9]+(?:[-_.][0-9]+)*)""", RegexOption.IGNORE_CASE)
            .find(seg)
        return if (m != null) {
            val num = m.groupValues[1].replace('-', '.').replace('_', '.').trimEnd('.')
            "Chapter $num"
        } else {
            "Chapter"
        }
    }

    /** "punishing-gray-raven" -> "Punishing Gray Raven" */
    private fun slugToTitle(slug: String): String {
        val words = slug.split('-', '_').filter { it.isNotBlank() }
        val title = words.joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return title.ifBlank { "Manga" }
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
