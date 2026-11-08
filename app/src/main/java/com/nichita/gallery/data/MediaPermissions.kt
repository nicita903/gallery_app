package com.nichita.gallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class MediaAccess(val images: Boolean = false, val videos: Boolean = false, val selected: Boolean = false) {
    val canRead: Boolean get() = images || videos || selected
    val isFull: Boolean get() = images && videos
}

object MediaPermissions {
    fun requested(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun read(context: Context): MediaAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= 33) MediaAccess(
            images = granted(Manifest.permission.READ_MEDIA_IMAGES),
            videos = granted(Manifest.permission.READ_MEDIA_VIDEO),
            selected = Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) else {
            val storage = granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            MediaAccess(images = storage, videos = storage)
        }
    }
}
