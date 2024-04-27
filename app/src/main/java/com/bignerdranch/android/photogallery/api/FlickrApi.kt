package com.bignerdranch.android.photogallery.api

import androidx.annotation.IntRange
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface FlickrApi {

    @GET("services/rest?method=flickr.interestingness.getList")
    fun fetchPhotos(
        @Query("page") @IntRange(from = 1) page: Int = 1
    ): Call<PhotoResponse>

    @GET("services/rest?method=flickr.photos.search")
    fun searchPhotos(
        @Query("page") @IntRange(from = 1) page: Int = 1,
        @Query("text") query: String
    ): Call<PhotoResponse>

    @GET
    fun fetchUrlBytes(@Url url: String): Call<ResponseBody>
}