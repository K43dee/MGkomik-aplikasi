package com.mgkomik.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mgkomik.reader.adblock.AdBlocker
import com.mgkomik.reader.databinding.ActivityMainBinding
import com.mgkomik.reader.download.ChapterDownloader
import com.mgkomik.reader.download.ChapterImageExtractor
import com.mgkomik.reader.library.LibraryActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var blockedCount = 0
    private var downloadDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        setupWebView()

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_library -> {
                    LibraryActivity.open(this)
                    true
                }
                R.id.action_download -> {
                    startDownloadFlow()
                    true
                }
                R.id.action_refresh -> {
                    webView.reload()
                    true
                }
                else -> false
            }
        }

        binding.swipeRefresh.setOnRefreshListener { webView.reload() }

        binding.btnRetry.setOnClickListener {
            binding.errorPanel.visibility = View.GONE
            webView.loadUrl(START_URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = buildUserAgent(settings.userAgentString)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                // Never block the main site itself or its webview assets.
                val host = AdBlocker.hostOf(url)
                if (host != null &&
                    (host == "web1.mgkomik.cc" || host.endsWith(".mgkomik.cc") || url.startsWith("https://appassets"))
                ) {
                    // Allow the site itself, but block its in-house ad banners
                    // (GIF strips hosted under /banner/ or with gambling names).
                    if (AdBlocker.isAdResource(url)) {
                        blockedCount++
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }
                    return super.shouldInterceptRequest(view, request)
                }
                if (AdBlocker.isBlocked(url)) {
                    blockedCount++
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (isAdUrl(url) || AdBlocker.isAdResource(url)) {
                        blockedCount++
                        return true
                    }
                    return false
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.swipeRefresh.isRefreshing = true
                binding.errorPanel.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                webView.evaluateJavascript(AdBlocker.cssHideScript(), null)
                webView.evaluateJavascript(AdBlocker.removeAdsScript(), null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError
            ) {
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (failingUrl == START_URL) {
                    binding.errorPanel.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun buildUserAgent(base: String): String =
        "$base MGKomikReader/1.0"

    private fun isAdUrl(url: String): Boolean = AdBlocker.isBlocked(url)

    private fun startDownloadFlow() {
        val currentUrl = webView.url ?: return
        if (!currentUrl.startsWith("http")) {
            toast(R.string.network_error)
            return
        }
        lifecycleScope.launch {
            val images = ChapterImageExtractor.extract(webView, currentUrl)
            if (images.isEmpty()) {
                Snackbar.make(binding.root, R.string.no_images_found, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            // confirmation + progress dialog
            val builder = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.download_chapter)
                .setMessage("${images.size} halaman akan diunduh")
                .setPositiveButton("Download") { _, _ -> runDownload(images, currentUrl) }
                .setNegativeButton(android.R.string.cancel, null)
            builder.show()
        }
    }

    private fun runDownload(images: List<String>, pageUrl: String) {
        val progress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            max = images.size.coerceAtLeast(1)
            setProgressCompat(0, true)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.downloading, 0, images.size))
            .setView(progress)
            .setCancelable(false)
        val dialog = builder.show()
        downloadDialog = dialog

        lifecycleScope.launch {
            try {
                val downloader = ChapterDownloader(this@MainActivity)
                downloader.downloadChapter(images, pageUrl) { done, total ->
                    runOnUiThread {
                        progress.max = total.coerceAtLeast(1)
                        progress.setProgressCompat(done, true)
                        dialog.setTitle(getString(R.string.downloading, done, total))
                    }
                }
                dialog.dismiss()
                Snackbar.make(
                    binding.root,
                    getString(R.string.chapter_saved),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.action_open) {
                    LibraryActivity.open(this@MainActivity)
                }.show()
            } catch (e: Exception) {
                dialog.dismiss()
                Snackbar.make(
                    binding.root,
                    getString(R.string.download_failed, e.message ?: "error"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        downloadDialog?.dismiss()
        super.onDestroy()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        const val START_URL = "https://web1.mgkomik.cc"
    }
}
