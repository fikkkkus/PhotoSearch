package com.bignerdranch.android.photogallery.api

import com.bignerdranch.android.photogallery.GalleryItem
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class PhotoDeserializer : JsonDeserializer<PhotoResponse> {
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): PhotoResponse {
        if (json.isJsonObject) {
            val jsonObject = json.asJsonObject
            val photos = jsonObject.getAsJsonObject("photos")
            val photoArray = photos.getAsJsonArray("photo")

            val galleryItems = mutableListOf<GalleryItem>()

            photoArray.forEach { jsonElement ->
                val galleryItem = context?.deserialize<GalleryItem>(jsonElement, GalleryItem::class.java)
                galleryItem?.let { galleryItems.add(it) }
            }

            val photoResponse = PhotoResponse()
            photoResponse.galleryItems = galleryItems
            return photoResponse
        } else {
            throw JsonParseException("Invalid JSON format")
        }
    }
}