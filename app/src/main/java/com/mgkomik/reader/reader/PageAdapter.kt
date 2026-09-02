package com.mgkomik.reader.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mgkomik.reader.databinding.ItemPageBinding
import com.mgkomik.reader.library.CbzArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Webtoon-style adapter: renders every CBZ page vertically, one after another,
 * decoding lazily on a background dispatcher. Uses an LRU bitmap cache so
 * scrolling back up is instant without re-decoding everything.
 */
class PageAdapter(
    private val archive: CbzArchive,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PageAdapter.PageHolder>() {

    /** Called when a page is tapped (used to toggle reader UI). */
    var onPageTap: (() -> Unit)? = null

    private val entries = archive.entries
    private val jobs = mutableMapOf<Int, Job>()

    // Small LRU cache of decoded pages (keyed by entry name).
    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_CACHED_PAGES
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = entries.size

    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val binding = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageHolder(binding)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PageHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    /** Discard cached bitmaps (e.g. when leaving reader) to free memory. */
    fun clearCache() {
        cache.clear()
    }

    private fun cached(name: String): Bitmap? = cache[name]

    private fun remember(name: String, bmp: Bitmap) {
        synchronized(cache) {
            if (cache.size >= MAX_CACHED_PAGES) cache.clear() // cheap LRU-ish
            cache[name] = bmp
        }
    }

    inner class PageHolder(private val binding: ItemPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentJob: Job? = null

        val imageView: ImageViewCompat get() = binding.pageImage

        fun bind(position: Int) {
            clear()
            val name = entries[position]
            binding.pageImage.setImageDrawable(null)
            binding.pageImage.setOnClickListener { onPageTap?.invoke() }

            val hit = cached(name)
            if (hit != null) {
                binding.pageImage.setImageBitmap(hit)
                return
            }

            currentJob = scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        val bytes = archive.readEntryBytes(name) ?: return@withContext null
                        // Downsample very large pages to avoid OOM.
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        var sample = 1
                        val maxDim = 2600
                        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                            sample *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bmp != null && bindingAdapterPosition == position) {
                    remember(name, bmp)
                    binding.pageImage.setImageBitmap(bmp)
                }
            }
        }

        fun clear() {
            currentJob?.cancel()
            currentJob = null
        }
    }

    companion object {
        private const val MAX_CACHED_PAGES = 12
    }
}

/** Alias to keep layout/type references simple. */
typealias ImageViewCompat = android.widget.ImageView
