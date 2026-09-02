package com.mgkomik.reader.download

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Extracts chapter page image URLs by running JS inside the already-loaded
 * WebView (same cookies/CF clearance), trying progressively broader selectors.
 *
 * Uses evaluateJavascript's built-in callback (no JavascriptInterface needed),
 * so it is reliable and cannot leak interfaces.
 */
object ChapterImageExtractor {

    private const val TIMEOUT_MS = 20_000L

    suspend fun extract(webView: WebView, pageUrl: String): List<String> =
        suspendCancellableCoroutine { cont ->
            val startedAt = System.currentTimeMillis()

            fun finish(result: List<String>) {
                if (!cont.isActive) return
                cont.resume(result)
            }

            fun evaluate() {
                if (cont.isCancelled) return
                webView.evaluateJavascript(buildExtractJs()) { raw ->
                    if (cont.isCancelled) return@evaluateJavascript
                    // evaluateJavascript returns a JSON-encoded string literal.
                    val json = raw?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                    val list = try {
                        val arr = JSONArray(json)
                        (0 until arr.length()).map { arr.optString(it) }
                            .filter { it.isNotBlank() }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    finish(list)
                }
            }

            // Guard: if the callback never fires (rare), time out.
            val timeoutRunnable = Runnable {
                finish(emptyList())
            }
            webView.postDelayed(timeoutRunnable, TIMEOUT_MS)
            cont.invokeOnCancellation {
                webView.removeCallbacks(timeoutRunnable)
            }

            evaluate()
        }

    private fun buildExtractJs(): String = """
        (function(){
          var READER_SELECTORS = [
            '#reader-area img', '.reader-area img',
            '#reader img', '.reader img',
            '#chapters img', '.chapters img',
            '#chapter-images img', '.chapter-images img',
            '#chapter-content img', '.chapter-content img',
            '.chapter-page img', '#chapter-page img',
            '#images img', '.images img',
            '#all img', '.all img',
            '#chapter-container img', '.chapter-container img'
          ];
          var IMG_HINT = /chapter|reader|uploads|komik|manga|page|chapter-image/i;
          function pick(el) {
            if (!el) return null;
            var u = el.currentSrc || el.src ||
                    el.getAttribute('data-src') ||
                    el.getAttribute('data-original') ||
                    el.getAttribute('data-lazy-src') ||
                    el.getAttribute('data-url') ||
                    el.getAttribute('data-image') ||
                    el.getAttribute('data-echo') ||
                    el.getAttribute('data-srcset');
            if (u && u.indexOf('data:') !== 0 && u.indexOf('blob:') !== 0) {
              // normalize srcset: first candidate only
              return u.split(',')[0].trim().split(/\s+/)[0];
            }
            return null;
          }
          function collectFrom(sel) {
            var out = [];
            document.querySelectorAll(sel).forEach(function(el){
              var u = pick(el);
              if (u) out.push(u);
            });
            return out;
          }
          function unique(arr) {
            return arr.filter(function(v,i){ return arr.indexOf(v) === i; });
          }

          var result = [];
          // Strategy 1: known reader containers
          for (var i = 0; i < READER_SELECTORS.length; i++) {
            result = result.concat(collectFrom(READER_SELECTORS[i]));
          }
          // Strategy 2: any img whose URL looks like a chapter image
          if (result.length === 0) {
            document.querySelectorAll('img').forEach(function(el){
              var u = pick(el);
              if (u && IMG_HINT.test(u)) result.push(u);
            });
          }
          result = unique(result);
          // Drop in-house ad banners (gif strips under /banner/ or gambling names)
          var AD_RE = /\/banner[\/_-]|koko88|rusia777|kaiko|arab777|gaza88|judi89|ratu89|indo666|klikhoki|penta|cina777|slot|togel|judi|casino|gamble|jackpot|scatter/i;
          result = result.filter(function(u){ return !AD_RE.test(u); });
          return JSON.stringify(result);
        })();
    """.trimIndent()
}
