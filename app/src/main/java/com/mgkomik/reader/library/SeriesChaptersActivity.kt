package com.mgkomik.reader.library

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mgkomik.reader.R
import com.mgkomik.reader.databinding.ActivitySeriesBinding
import com.mgkomik.reader.databinding.ItemBookBinding
import com.mgkomik.reader.reader.ReaderActivity
import com.mgkomik.reader.util.bytesToHuman
import com.mgkomik.reader.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chapter list of a single downloaded comic series. */
class SeriesChaptersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeriesBinding
    private lateinit var repo: BookRepository
    private lateinit var adapter: ChapterAdapter
    private var seriesTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seriesTitle = intent.getStringExtra(EXTRA_SERIES) ?: run {
            finish()
            return
        }
        repo = BookRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = seriesTitle

        adapter = ChapterAdapter(
            onOpen = { book -> ReaderActivity.open(this, book) },
            onShare = { book -> shareBook(book) },
            onDelete = { book -> confirmDelete(book) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val series = withContext(Dispatchers.IO) { repo.getSeries(seriesTitle) }
            if (series == null) {
                finish()
                return@launch
            }
            val chapters = withContext(Dispatchers.IO) { repo.listChapters(series) }
            adapter.submit(chapters)
            binding.emptyView.visibility = if (chapters.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun confirmDelete(book: Book) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.confirm_delete, book.displayChapter))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.deleteBook(book) }
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareBook(book: Book) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", book.file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.comicbook+zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    class ChapterAdapter(
        private val onOpen: (Book) -> Unit,
        private val onShare: (Book) -> Unit,
        private val onDelete: (Book) -> Unit
    ) : RecyclerView.Adapter<ChapterAdapter.Holder>() {

        private val items = mutableListOf<Book>()

        fun submit(list: List<Book>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemBookBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(private val binding: ItemBookBinding) :
            RecyclerView.ViewHolder(binding.root) {

            init {
                binding.root.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onOpen(items[pos])
                }
                binding.root.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val book = items[pos]
                        MaterialAlertDialogBuilder(binding.root.context)
                            .setTitle(book.displayChapter)
                            .setItems(
                                arrayOf(
                                    binding.root.context.getString(R.string.action_open),
                                    binding.root.context.getString(R.string.action_share),
                                    binding.root.context.getString(R.string.action_delete)
                                )
                            ) { _, which ->
                                when (which) {
                                    0 -> onOpen(book)
                                    1 -> onShare(book)
                                    2 -> onDelete(book)
                                }
                            }
                            .show()
                    }
                    true
                }
            }

            fun bind(book: Book) {
                binding.title.text = book.displayChapter
                binding.subtitle.text = book.displayTitle
                val info = buildList {
                    if (book.pageCount > 0) add("${book.pageCount} halaman")
                    add(bytesToHuman(book.sizeBytes))
                    add(formatDate(book.addedAt))
                }.joinToString(" • ")
                binding.info.text = info
            }
        }
    }

    companion object {
        const val EXTRA_SERIES = "series_title"

        fun open(activity: Activity, seriesTitle: String) {
            val i = Intent(activity, SeriesChaptersActivity::class.java)
                .putExtra(EXTRA_SERIES, seriesTitle)
            activity.startActivity(i)
        }
    }
}
