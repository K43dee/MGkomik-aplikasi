package com.mgkomik.reader.util

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sanitize a string into a safe file name. */
fun sanitizeFileName(name: String): String {
    val cleaned = name
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .replace(Regex("""[. ]+$"""), "")
        .take(120)
    return cleaned.ifBlank { "untitled" }
}

fun sha1(text: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    return md.digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun bytesToHuman(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

fun formatDate(millis: Long): String = dateFmt.format(Date(millis))

/** Resolve a possibly-relative URL against a base URL. */
fun resolveUrl(base: String, url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (url.startsWith("//")) return "https:$url"
    val b = base.substringBefore("?", "").substringBefore("#", "")
    val slash = b.indexOf('/', b.indexOf(':') + 3)
    val origin = if (slash >= 0) b.substring(0, slash) else b
    if (url.startsWith("/")) return origin + url
    val dir = b.substringBeforeLast('/', "").let { if (it.isEmpty()) origin else it }
    return "$dir/$url"
}

/** Return a File that does not collide with existing siblings (appends (1), (2)...). */
fun uniqueFile(dir: File, baseName: String, ext: String): File {
    var f = File(dir, "$baseName$ext")
    var i = 1
    while (f.exists()) {
        f = File(dir, "$baseName ($i)$ext")
        i++
    }
    return f
}
