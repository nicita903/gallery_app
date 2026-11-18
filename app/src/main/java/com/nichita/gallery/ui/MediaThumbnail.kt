package com.nichita.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import java.util.Locale

@Composable
internal fun MediaThumbnail(media: MediaItem, loader: ImageLoader, pixels: Int, columns: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val size = thumbnailPixels(pixels)
    val request = remember(media.contentUri, media.cacheVersion, size) {
        thumbnailImageRequest(context, media, size)
    }
    Box(modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = request, imageLoader = loader, contentDescription = media.filename,
            error = painterResource(R.drawable.ic_photos), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (media.type == MediaType.Video) {
            Row(Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.video), tint = Color.White, modifier = Modifier.size(12.dp))
                if (columns <= 5) {
                    val duration = remember(media.durationMillis) { formatDuration(media.durationMillis ?: 0L) }
                    Text(duration, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
