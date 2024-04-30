package com.bignerdranch.android.photogallery

import android.net.Uri
import com.google.gson.annotations.SerializedName

data class GalleryItem(
    val title: String? = null,
    val id: String? = null,
    @SerializedName("url_s")
    val url: String? = null,
    @SerializedName("owner")
    var owner: String? = null,
) {
    val photoPageUri: Uri
    get() {
        return Uri.parse("https://www.flickr.com/photos/")
            .buildUpon()
            .appendPath(owner)
            .appendPath(id)
            .build()
    }
}

