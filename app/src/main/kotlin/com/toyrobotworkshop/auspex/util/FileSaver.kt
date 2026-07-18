package com.toyrobotworkshop.auspex.util

import android.content.ContentValues
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utility for saving captured photos and recorded videos to the device's media store.
 */
object FileSaver {

    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Generate a unique filename with a timestamp prefix.
     */
    fun generateFilename(extension: String): String {
        val timestamp = timestampFormat.format(System.currentTimeMillis())
        return "OTG_$timestamp.$extension"
    }

    /**
     * Get a content URI for saving a photo via MediaStore (Android Q+).
     */
    fun getPhotoUriResolver(
        contentValues: ContentValues,
    ): ContentValues {
        contentValues.apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, generateFilename("jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OTGCamera")
        }
        return contentValues
    }

    /**
     * Get a content URI for saving a video via MediaStore (Android Q+).
     */
    fun getVideoUriResolver(
        contentValues: ContentValues,
    ): ContentValues {
        contentValues.apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, generateFilename("mp4"))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OTGCamera")
        }
        return contentValues
    }

    /**
     * Get the output path for a photo file.
     */
    fun getPhotoPath(directory: File): String {
        val filename = generateFilename("jpg")
        return File(directory, filename).absolutePath
    }

    /**
     * Get the output path for a video file.
     */
    fun getVideoPath(directory: File): String {
        val filename = generateFilename("mp4")
        return File(directory, filename).absolutePath
    }
}
