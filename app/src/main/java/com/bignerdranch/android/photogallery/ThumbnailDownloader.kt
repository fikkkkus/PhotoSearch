package com.bignerdranch.android.photogallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.bignerdranch.android.photogallery.api.FlickrFetchr
import java.util.concurrent.ConcurrentHashMap


private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0

class ThumbnailDownloader<in T>(
    private val responseHandler: Handler,
    private val lifecycle: Lifecycle,
    var viewLifecycleOwner: LifecycleOwner?,
    private val onThumbnailDownloaded: (T, Bitmap) -> Unit,
) : HandlerThread(TAG), LifecycleObserver {

    private var hasQuit = false
    private lateinit var requestHandler: Handler
    private val requestMap = ConcurrentHashMap<T, String>()
    private val flickrFetchr = FlickrFetchr()

    private val cache = object : LruCache<String, Bitmap>(200) {
        fun getBitmapFromMemory(key: String?): Bitmap? {
            return this[key]
        }

        fun setBitmapToMemory(key: String, bitmap: Bitmap?) {
            if (getBitmapFromMemory(key) == null) {
                put(key, bitmap)
            }
        }
    }

    val fragmentLifecycleObserver: LifecycleObserver =
        object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            fun setup() {
                Log.i(TAG, "Starting background thread")
                start()
            }
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun tearDown() {
                Log.i(TAG, "Destroying background thread")
                quit()
                lifecycle.removeObserver(
                    this@ThumbnailDownloader
                )
            }
        }

    val viewLifecycleObserver: LifecycleObserver =
        object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun clearQueue() {
                Log.i(TAG, "Clearing all requests from queue")
                        requestHandler.removeMessages(MESSAGE_DOWNLOAD)
                        requestMap.clear()
                viewLifecycleOwner?.lifecycle?.removeObserver(
                    this@ThumbnailDownloader
                )
            }
        }

    override fun quit(): Boolean {
        hasQuit = true
        return super.quit()
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("HandlerLeak")
    override fun onLooperPrepared() {
        requestHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    val target = msg.obj as T
                    Log.i(TAG, "Got a request for URL: ${requestMap[target]}")
                    handleRequest(target)
                }
            }
        }
    }

    fun queueThumbnail(object_: T, url: String)
    {
        requestMap[object_] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, object_).sendToTarget()
        Log.i(TAG, "Got a URL: $url")
    }

    private fun handleRequest(object_: T) {

        val url = requestMap[object_] ?: return
        var bitmap: Bitmap? = cache.getBitmapFromMemory(url)
        if (bitmap == null) {
            bitmap = flickrFetchr.fetchPhoto(url) ?: return
            cache.setBitmapToMemory(url, bitmap)
        }


        responseHandler.post(Runnable {
            if (requestMap[object_] != url || hasQuit) {
                return@Runnable
            }
            requestMap.remove(object_)
            onThumbnailDownloaded(object_, bitmap)
        })
    }
}