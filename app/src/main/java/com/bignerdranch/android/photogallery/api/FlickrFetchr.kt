package com.bignerdranch.android.photogallery.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import com.bignerdranch.android.photogallery.GalleryItem
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "FlickrFetchr"

class FlickrFetchr {
    private val flickrApi: FlickrApi

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(PhotoInterceptor())
            .build()

        val gson = GsonBuilder()
            .registerTypeAdapter(PhotoResponse::class.java, PhotoDeserializer())
            .create()

        val retrofit: Retrofit =
            Retrofit.Builder()
                .baseUrl("https://api.flickr.com/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build()

        flickrApi = retrofit.create(FlickrApi::class.java)
    }

    @WorkerThread
    fun fetchPhoto(url: String): Bitmap? {
        val response: Response<ResponseBody> = flickrApi.fetchUrlBytes(url).execute()
        val bitmap = response.body()?.byteStream()?.use(BitmapFactory::decodeStream)
        Log.i(TAG, "Decoded bitmap=$bitmap from Response=$response")
        return bitmap
    }

    fun fetchPhotosRequest(page: Int): Call<PhotoResponse> {
        return flickrApi.fetchPhotos(page)
    }

    fun searchPhotosRequest(page: Int, query: String): Call<PhotoResponse> {
        return flickrApi.searchPhotos(page, query)
    }

    suspend fun fetchPhotos(page: Int): List<GalleryItem> {
        return fetchPhotoMetadata(fetchPhotosRequest(page))
    }

    suspend fun searchPhotos(page: Int, query: String): List<GalleryItem> {
        return fetchPhotoMetadata(searchPhotosRequest(page, query))
    }

    private suspend fun fetchPhotoMetadata(flickrRequest: Call<PhotoResponse>): List<GalleryItem> {
        return suspendCoroutine { continuation ->
            flickrRequest.enqueue(object : Callback<PhotoResponse> {
                override fun onFailure(call: Call<PhotoResponse>, t: Throwable) {
                    Log.e(TAG, "Failed to fetch photos", t)
                    continuation.resume(emptyList())
                }

                override fun onResponse(call: Call<PhotoResponse>, response: Response<PhotoResponse>) {
                    val photoResponse: PhotoResponse? = response.body()
                    var galleryItems: List<GalleryItem> = photoResponse?.galleryItems ?: emptyList()
                    galleryItems = galleryItems.filterNot {
                        it.url?.let { url -> url.isBlank() } ?: false
                    }
                    continuation.resume(galleryItems)
                }
            })
        }
    }


}