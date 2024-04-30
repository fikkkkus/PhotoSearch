package com.bignerdranch.android.photogallery

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.bignerdranch.android.photogallery.notifications.PollWorker
import com.bignerdranch.android.photogallery.notifications.VisibleFragment
import com.bignerdranch.android.photogallery.webview.PhotoPageActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "PhotoGalleryFragment"
private const val POLL_WORK = "POLL_WORK"

class PhotoGalleryFragment : VisibleFragment() {

    private lateinit var photoRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var photoGalleryViewModel: PhotoGalleryViewModel
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoAdapter.PhotoHolder>
    private var WIDTH_COLUMN = 300

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setHasOptionsMenu(true)

        val responseHandler = Handler()
        thumbnailDownloader = ThumbnailDownloader(responseHandler, lifecycle, null) {
                photoHolder, bitmap ->
                val drawable = BitmapDrawable(resources, bitmap)
                photoHolder.bindDrawable(drawable)
            }

        lifecycle.addObserver(thumbnailDownloader.fragmentLifecycleObserver)

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_photo_gallery, menu)

        val searchItem: MenuItem = menu.findItem(R.id.menu_item_search)
        val searchView = searchItem.actionView as SearchView
        searchView.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(queryText: String): Boolean {
                    progressBar.visibility = View.VISIBLE
                    photoRecyclerView.visibility = View.GONE
                    searchView.onActionViewCollapsed()
                    photoGalleryViewModel.assignNewPageSource(queryText)
                    viewLifecycleOwner.lifecycleScope.launch {
                        (photoRecyclerView.adapter as? PhotoAdapter)?.refresh()
                    }
                    return true
                }
                override fun onQueryTextChange(queryText: String): Boolean {
                    Log.d(TAG, "QueryTextChange: $queryText")
                    return false
                }
            })
            setOnSearchClickListener {
                val searchTerm = photoGalleryViewModel.searchTerm
                searchView.setQuery(searchTerm, false)
            }
        }

        val toggleItem = menu.findItem(R.id.menu_item_toggle_polling)
        val isPolling = QueryPreferences.isPolling(requireContext())
        val toggleItemTitle = if (isPolling) {
            R.string.stop_polling
        } else {
            R.string.start_polling
        }
        toggleItem.setTitle(toggleItemTitle)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_clear -> {
                photoGalleryViewModel.assignNewPageSource("")
                viewLifecycleOwner.lifecycleScope.launch {
                    (photoRecyclerView.adapter as? PhotoAdapter)?.refresh()
                }
                true
            }
            R.id.menu_item_toggle_polling -> {
                val isPolling = QueryPreferences.isPolling(requireContext())
                if (isPolling) {
                    WorkManager.getInstance().cancelUniqueWork(POLL_WORK)
                    QueryPreferences.setPolling(requireContext(), false)
                } else {
                    val constraints = Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val periodicRequest =
                        PeriodicWorkRequest
                            .Builder(PollWorker::class.java, 15, TimeUnit.MINUTES)
                            .setConstraints(constraints)
                            .build()
                    WorkManager.getInstance().enqueueUniquePeriodicWork(
                        POLL_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        periodicRequest)
                    QueryPreferences.setPolling(requireContext(), true)
                }
                activity?.invalidateOptionsMenu()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewLifecycleOwnerLiveData.observe(viewLifecycleOwner) { owner ->
            owner.lifecycle.addObserver(thumbnailDownloader.viewLifecycleObserver)
        }

        thumbnailDownloader.viewLifecycleOwner = viewLifecycleOwner

        photoGalleryViewModel = ViewModelProviders.of(this)[PhotoGalleryViewModel::class.java]

        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)

        progressBar = view.findViewById(R.id.progressBar)

        photoRecyclerView = view.findViewById(R.id.photo_recycler_view)
        photoRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val columnWidth = WIDTH_COLUMN
                val recyclerViewWidth = photoRecyclerView.width
                val spanCount = recyclerViewWidth / columnWidth

                val layoutManager = GridLayoutManager(requireContext(), spanCount)
                photoRecyclerView.layoutManager = layoutManager

                photoRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PhotoAdapter(layoutInflater, thumbnailDownloader, requireContext())
        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.NotLoading) {
                photoRecyclerView.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            }
        }
        ViewTreeObserver.OnGlobalLayoutListener {
            photoRecyclerView
        }
        photoRecyclerView.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            photoGalleryViewModel.galleryItemFlow?.collect { pagingData ->
                adapter.submitData(pagingData)
            }
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewLifecycleOwnerLiveData.removeObservers(viewLifecycleOwner)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private class PhotoAdapter(
        private val layoutInflater: LayoutInflater,
        private val thumbnailDownloader: ThumbnailDownloader<PhotoHolder>,
        private val context: Context
    ) : PagingDataAdapter<GalleryItem, PhotoAdapter.PhotoHolder>(GalleryItemDiffCallback()) {

        private val defaultImage = ContextCompat.getDrawable(layoutInflater.context, R.drawable.placeholder) ?: ColorDrawable()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val view = layoutInflater.inflate(
                R.layout.list_item_gallery,
                parent,
                false
            ) as ImageView
            return PhotoHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val galleryItem = getItem(position)
            if (galleryItem != null) {
                holder.bindGalleryItem(galleryItem)
            }
            val placeholder: Drawable = defaultImage
            holder.bindDrawable(placeholder)
            if (galleryItem != null) {
                galleryItem.url?.let { thumbnailDownloader.queueThumbnail(holder, it) }
            }
        }

        inner class PhotoHolder(private val itemImageView: ImageView) : RecyclerView.ViewHolder(itemImageView),
            View.OnClickListener {

            private lateinit var galleryItem: GalleryItem
            val bindDrawable: (Drawable) -> Unit = itemImageView::setImageDrawable
            init {
                itemView.setOnClickListener(this)
            }

            fun bindGalleryItem(item: GalleryItem)
            {
                galleryItem = item
            }
            override fun onClick(view: View) {
                val isNotSkeleton = itemImageView.drawable.constantState != defaultImage.constantState
                if (isNotSkeleton) {
                    val intent = PhotoPageActivity.newIntent(context, galleryItem.photoPageUri)
                    context.startActivity(intent)
                }
            }

        }

        private class GalleryItemDiffCallback : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    companion object {
        fun newInstance() =
            PhotoGalleryFragment()
    }
}