package com.nichita.gallery.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface DeletePreparation {
    data object Deleted : DeletePreparation
    data class Confirmation(val intent: PendingIntent, val systemDeletes: Boolean) : DeletePreparation
}

internal class MediaDeletion(private val resolver: ContentResolver) {
    // Only invoke after the user has confirmed the app's deletion dialog.
    suspend fun prepare(uri: Uri): DeletePreparation = withContext(Dispatchers.IO) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY)
        if (Build.VERSION.SDK_INT >= 30) {
            DeletePreparation.Confirmation(MediaStore.createDeleteRequest(resolver, listOf(uri)), true)
        } else {
            if (!exists(uri)) return@withContext DeletePreparation.Deleted
            try {
                deleteAuthorized(uri)
                DeletePreparation.Deleted
            } catch (recoverable: RecoverableSecurityException) {
                DeletePreparation.Confirmation(recoverable.userAction.actionIntent, false)
            }
        }
    }

    suspend fun retryAuthorized(uri: Uri) = withContext(Dispatchers.IO) { deleteAuthorized(uri) }

    private fun deleteAuthorized(uri: Uri) {
        if (resolver.delete(uri, null, null) == 0 && exists(uri)) error("Media deletion did not succeed")
    }

    private fun exists(uri: Uri): Boolean = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
        ?.use { it.moveToFirst() } ?: error("Media provider unavailable")
}
