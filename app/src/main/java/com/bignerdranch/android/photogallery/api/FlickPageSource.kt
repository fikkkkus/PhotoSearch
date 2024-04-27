package com.bignerdranch.android.photogallery.api

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.bignerdranch.android.photogallery.GalleryItem

class FlickPageSource(
    private var searchTerm: String?,
    private val flickrFetchr: FlickrFetchr
) : PagingSource<Int, GalleryItem>() {

    override fun getRefreshKey(state: PagingState<Int, GalleryItem>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryItem> {
        val nextPageNumber = params.key ?: 1
        var response: List<GalleryItem>
        if ((searchTerm == null) or (searchTerm?.isBlank() == true)) {
            response = flickrFetchr.fetchPhotos(nextPageNumber)
        }
        else{
            response = flickrFetchr.searchPhotos(nextPageNumber, searchTerm!!)
        }
        return LoadResult.Page(
            data = response,
            prevKey = if (nextPageNumber == 1) null else nextPageNumber - 1,
            nextKey = if (response.isEmpty()) null else nextPageNumber + 1
        )
    }

}