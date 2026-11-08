package com.nichita.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nichita.gallery.R
import com.nichita.gallery.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoInformation(item: MediaItem, repository: MediaMetadata, onClose: () -> Unit) {
    val details by produceState<List<MetadataField>?>(null, item.id, item.cacheVersion) { value = repository.load(item) }
    val window = LocalWindowInfo.current
    val height by remember(window) { derivedStateOf { window.containerSize.height } }
    val maximumHeight = with(LocalDensity.current) { height.toDp() } * 0.8f
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maximumHeight).testTag("photo_information"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.information), style = MaterialTheme.typography.headlineSmall) }
            if (details == null) item { CircularProgressIndicator() }
            details?.forEachIndexed { index, field ->
                if (field.camera && details?.getOrNull(index - 1)?.camera == false) item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item(key = field.label) {
                    Column {
                        Text(stringResource(field.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(field.value, style = MaterialTheme.typography.bodyLarge,
                            fontFamily = if (field.label == R.string.folder) androidx.compose.ui.text.font.FontFamily.Monospace else null)
                    }
                }
            }
        }
    }
}
