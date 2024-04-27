package com.bignerdranch.android.photogallery

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
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

private const val TAG = "PhotoGalleryFragment"

class PhotoGalleryFragment : Fragment() {

    private lateinit var photoRecyclerView: RecyclerView
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
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewLifecycleOwnerLiveData.observe(viewLifecycleOwner) { owner ->
            owner.lifecycle.addObserver(thumbnailDownloader.viewLifecycleObserver)
        }

        thumbnailDownloader.viewLifecycleOwner = viewLifecycleOwner

        photoGalleryViewModel = ViewModelProviders.of(this)[PhotoGalleryViewModel::class.java]

        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)
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
        val adapter = PhotoAdapter(layoutInflater, thumbnailDownloader)

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
    ) : PagingDataAdapter<GalleryItem, PhotoAdapter.PhotoHolder>(GalleryItemDiffCallback()) {

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
            val placeholder: Drawable = ContextCompat.getDrawable(layoutInflater.context, R.drawable.placeholder) ?: ColorDrawable()
            holder.bindDrawable(placeholder)
            if (galleryItem != null) {
                galleryItem.url?.let { thumbnailDownloader.queueThumbnail(holder, it) }
            }
        }

        class PhotoHolder(private val itemImageView: ImageView) : RecyclerView.ViewHolder(itemImageView) {
            val bindDrawable: (Drawable) -> Unit = itemImageView::setImageDrawable
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