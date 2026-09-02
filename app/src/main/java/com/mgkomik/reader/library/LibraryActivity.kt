package com.mgkomik.reader.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mgkomik.reader.R
import com.mgkomik.reader.databinding.ActivityLibraryBinding
import com.mgkomik.reader.databinding.ItemSeriesBinding
import com.mgkomik.reader.util.bytesToHuman
import com.mgkomik.reader.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Library home: shows one row per downloaded comic series (folder). Tapping a
 * series opens its chapter list.
 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var repo: BookRepository
    private lateinit var adapter: SeriesAdapter

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importCbz(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = BookRepository(this)
        adapter = SeriesAdapter(
            onOpen = { series -> SeriesChaptersActivity.open(this, series.title) },
            onDelete = { series -> confirmDelete(series) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_library)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/vnd.comicbook+zip", "application/zip", "application/octet-stream"))
                    true
                }
                else -> false
            }
        }

        // Move any pre-folder downloads into per-series folders once.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.migrateLooseFiles() }
            refresh()
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val series = withContext(Dispatchers.IO) { repo.listSeries() }
            adapter.submit(series)
            binding.emptyView.visibility = if (series.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete(series: MangaSeries) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.confirm_delete_series, series.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.deleteSeries(series) }
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importCbz(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repo.importCbz(uri) }
            result.onSuccess {
                refresh()
                Snackbar.make(binding.root, getString(R.string.export_done, it.file.name), Snackbar.LENGTH_LONG).show()
            }.onFailure {
                Snackbar.make(binding.root, getString(R.string.import_failed, it.message ?: "error"), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    class SeriesAdapter(
        private val onOpen: (MangaSeries) -> Unit,
        private val onDelete: (MangaSeries) -> Unit
    ) : RecyclerView.Adapter<SeriesAdapter.Holder>() {

        private val items = mutableListOf<MangaSeries>()

        fun submit(list: List<MangaSeries>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemSeriesBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(private val binding: ItemSeriesBinding) :
            RecyclerView.ViewHolder(binding.root) {

            init {
                binding.root.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onOpen(items[pos])
                }
                binding.root.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val series = items[pos]
                        MaterialAlertDialogBuilder(binding.root.context)
                            .setTitle(series.title)
                            .setItems(
                                arrayOf(
                                    binding.root.context.getString(R.string.action_open),
                                    binding.root.context.getString(R.string.action_delete)
                                )
                            ) { _, which ->
                                when (which) {
                                    0 -> onOpen(series)
                                    1 -> onDelete(series)
                                }
                            }
                            .show()
                    }
                    true
                }
            }

            fun bind(series: MangaSeries) {
                binding.title.text = series.title
                val info = buildList {
                    add(if (series.chapterCount == 1) "1 chapter" else "${series.chapterCount} chapter")
                    if (series.pageCount > 0) add("${series.pageCount} halaman")
                    add(bytesToHuman(series.sizeBytes))
                    add(formatDate(series.updatedAt))
                }.joinToString(" • ")
                binding.info.text = info
            }
        }
    }

    companion object {
        fun open(activity: Activity) {
            activity.startActivity(Intent(activity, LibraryActivity::class.java))
        }
    }
}
