package com.bignerdranch.android.photogallery

import com.google.gson.annotations.SerializedName

data class GalleryItem(
    val title: String? = null,
    val id: String? = null,
    @SerializedName("url_s")
    val url: String? = null
)
