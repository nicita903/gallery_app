package com.nichita.gallery.data

import android.content.ContentResolver
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.nichita.gallery.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal data class MetadataField(val label: Int, val value: String, val camera: Boolean = false)

internal class MediaMetadata(private val resolver: ContentResolver) {
    private var cachedKey: String? = null
    private var cached: List<MetadataField>? = null

    suspend fun load(item: MediaItem): List<MetadataField> = withContext(Dispatchers.IO) {
        val key = "${item.id}:${item.cacheVersion}:${Locale.getDefault()}:${ZoneId.systemDefault()}"
        if (key == cachedKey) cached?.let { return@withContext it }
        val fields = mutableListOf(MetadataField(R.string.filename, item.filename))
        if (item.width > 0 && item.height > 0) fields += MetadataField(R.string.resolution, "${item.width} x ${item.height}")
        if (item.creationDate > 0) fields += MetadataField(R.string.date_time,
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.creationDate)))
        try {
            val cursor = suspendCancellableCoroutine<android.database.Cursor?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    val value = resolver.query(item.contentUri, arrayOf(MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null, signal)
                    continuation.resume(value, onCancellation = { _, result, _ -> result?.close() })
                } catch (error: Exception) { continuation.resumeWith(Result.failure(error)) }
            }
            cursor?.use {
                if (it.moveToFirst()) {
                    if (!it.isNull(0) && it.getLong(0) > 0) fields += MetadataField(R.string.file_size,
                        formatFileSize(it.getLong(0)))
                    it.getString(1)?.takeIf(String::isNotBlank)?.let { value -> fields += MetadataField(R.string.format, value) }
                    it.getString(2)?.takeIf(String::isNotBlank)?.let { value -> fields += MetadataField(R.string.folder, value) }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Provider fields are optional; still try EXIF independently. */ }
        currentCoroutineContext().ensureActive()
        if (item.type == MediaType.Image) try {
            resolver.openFileDescriptor(item.contentUri, "r")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                for ((label, tag) in listOf(R.string.camera_model to ExifInterface.TAG_MODEL,
                    R.string.manufacturer to ExifInterface.TAG_MAKE, R.string.iso to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)) {
                    exif.getAttribute(tag)?.trim()?.takeIf(String::isNotEmpty)?.let { fields += MetadataField(label, it, true) }
                }
                fun number(label: Int, tag: String, format: (Double) -> String) {
                    val value = exif.getAttributeDouble(tag, Double.NaN)
                    if (value.isFinite() && value > 0) fields += MetadataField(label, format(value), true)
                }
                val cameraNumber = java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                    maximumFractionDigits = 2; isGroupingUsed = false
                }
                number(R.string.aperture, ExifInterface.TAG_F_NUMBER) { "f/${cameraNumber.format(it)}" }
                number(R.string.shutter_speed, ExifInterface.TAG_EXPOSURE_TIME, ::formatExposure)
                number(R.string.focal_length, ExifInterface.TAG_FOCAL_LENGTH) { "${cameraNumber.format(it)} mm" }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Unsupported and missing EXIF must not hide basic metadata. */ }
        currentCoroutineContext().ensureActive()
        cachedKey = key
        cached = fields.toList()
        cached!!
    }
}

internal fun formatExposure(seconds: Double): String {
    val denominator = if (seconds > 0) (1 / seconds).roundToLong() else 0L
    if (seconds < 1 && denominator > 0 && kotlin.math.abs(seconds * denominator - 1) < 0.001) return "1/$denominator s"
    return java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 4; isGroupingUsed = false
    }.format(seconds) + " s"
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1048576.0)
}
