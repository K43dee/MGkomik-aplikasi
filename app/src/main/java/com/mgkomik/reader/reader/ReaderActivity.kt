package com.mgkomik.reader.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mgkomik.reader.R
import com.mgkomik.reader.databinding.ActivityReaderBinding
import com.mgkomik.reader.library.Book
import com.mgkomik.reader.library.BookRepository
import com.mgkomik.reader.library.CbzArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Webtoon-style offline reader: all chapter pages laid out vertically in a
 * RecyclerView so the reader scrolls continuously like a webtoon.
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var adapter: PageAdapter
    private var book: Book? = null
    private var uiVisible = false
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val b = withContext(Dispatchers.IO) {
                BookRepository(this@ReaderActivity).getBookById(bookId)
            }
            if (b == null) {
                finish()
                return@launch
            }
            book = b
            val archive = CbzArchive(b.file)
            adapter = PageAdapter(archive, lifecycleScope)
            adapter.onPageTap = { toggleUi() }

            val layoutManager = LinearLayoutManager(this@ReaderActivity)
            binding.recycler.layoutManager = layoutManager
            binding.recycler.adapter = adapter
            binding.recycler.itemAnimator = null

            setupUi(layoutManager)

            // Restore last page position if coming back from rotation.
            val startPage = savedInstanceState?.getInt(KEY_PAGE, 0) ?: 0
            if (startPage > 0) {
                binding.recycler.scrollToPosition(startPage)
            }
            updatePageLabel()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiVisible) hideUi() else finish()
            }
        })
    }

    private fun setupUi(layoutManager: LinearLayoutManager) {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareCurrent() }

        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                currentPage = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                updatePageLabel()
            }
        })
    }

    private fun toggleUi() {
        uiVisible = !uiVisible
        binding.topBar.visibility = if (uiVisible) View.VISIBLE else View.GONE
    }

    private fun hideUi() {
        uiVisible = false
        binding.topBar.visibility = View.GONE
    }

    private fun updatePageLabel() {
        val total = adapter.itemCount
        binding.pageLabel.text = "${currentPage + 1} / $total"
    }

    private fun shareCurrent() {
        val b = book ?: return
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", b.file)
        } catch (e: Exception) {
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.comicbook+zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, currentPage)
    }

    override fun onDestroy() {
        adapter.clearCache()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val KEY_PAGE = "reader_page"

        fun open(activity: android.app.Activity, book: Book) {
            val i = Intent(activity, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, book.id)
            activity.startActivity(i)
        }
    }
}
