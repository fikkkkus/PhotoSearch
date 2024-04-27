package com.bignerdranch.android.photogallery

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bignerdranch.android.photogallery.api.FlickPageSource
import com.bignerdranch.android.photogallery.api.FlickrFetchr
import kotlinx.coroutines.flow.Flow

class PhotoGalleryViewModel : ViewModel() {
    var galleryItemFlow: Flow<PagingData<GalleryItem>>? = null
    private var flickPageSource: FlickPageSource? = null
    private val flickrFetchr: FlickrFetchr = FlickrFetchr()

    init {
        assignNewPageSource(null)
    }

    fun assignNewPageSource(newSearchTerm: String?) {
        flickPageSource = FlickPageSource(newSearchTerm, flickrFetchr)

        galleryItemFlow = Pager(
            config = PagingConfig(
                pageSize = 100,
                enablePlaceholders = true,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { flickPageSource!! }
        ).flow
    }

}