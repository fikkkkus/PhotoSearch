package com.bignerdranch.android.photogallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bignerdranch.android.photogallery.api.FlickPageSource
import com.bignerdranch.android.photogallery.api.FlickrFetchr
import kotlinx.coroutines.flow.Flow

class PhotoGalleryViewModel(private val app: Application) : AndroidViewModel(app) {
    var galleryItemFlow: Flow<PagingData<GalleryItem>>? = null
    private var flickPageSource: FlickPageSource? = null
    private val flickrFetchr: FlickrFetchr = FlickrFetchr()

    var searchTerm: String = ""

    init {
        assignNewPageSource(null)
    }

    fun assignNewPageSource(newSearchTerm: String?) {
        flickPageSource = if(newSearchTerm == null) {
            val stringCache = QueryPreferences.getStoredQuery(app)
            searchTerm = stringCache
            FlickPageSource(
                stringCache,
                flickrFetchr
            )
        } else{
            searchTerm = newSearchTerm
            FlickPageSource(newSearchTerm, flickrFetchr)
        }

        galleryItemFlow = Pager(
            config = PagingConfig(
                pageSize = 100,
                enablePlaceholders = true,
                initialLoadSize = 10
            ),
            pagingSourceFactory = { flickPageSource!! }
        ).flow

        QueryPreferences.setStoredQuery(app, newSearchTerm ?: "")
    }

}