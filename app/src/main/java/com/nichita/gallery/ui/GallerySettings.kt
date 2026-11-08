package com.nichita.gallery.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nichita.gallery.GalleryApplication
import com.nichita.gallery.R

@Composable
internal fun GallerySettings(state: GalleryUiState, setTheme: (ThemeMode) -> Unit,
    setOldest: (Boolean) -> Unit, setAlbumNames: (Boolean) -> Unit, setColumns: (Int) -> Unit,
    requestAccess: () -> Unit, openSettings: () -> Unit,
    scroll: androidx.compose.foundation.lazy.LazyListState, header: @Composable () -> Unit) {
    var choice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        item(key = "header") { header() }
        item { SettingsSection(R.string.appearance) }
        item { SettingsRow(R.string.theme, stringResource(when (state.theme) {
            ThemeMode.Dark -> R.string.theme_dark
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.System -> R.string.theme_system
        })) { choice = "theme" } }
        item { SettingsSection(R.string.gallery_section) }
        item { SettingsRow(R.string.sort_order, stringResource(if (state.oldestFirst) R.string.oldest else R.string.newest)) { choice = "sort" } }
        item { SettingsRow(R.string.grid_layout, stringResource(R.string.grid_columns, state.columns)) { choice = "grid" } }
        item { SettingsRow(R.string.album_order, stringResource(if (state.albumNames) R.string.album_name else R.string.album_recent)) { choice = "albums" } }
        item { SettingsSection(R.string.privacy) }
        item { SettingsRow(R.string.offline, stringResource(R.string.offline_detail)) }
        item { SettingsRow(R.string.media_access, stringResource(when {
            state.access.isFull -> R.string.access_full
            state.access.canRead -> R.string.access_partial
            else -> R.string.access_none
        })) { requestAccess() } }
        item { SettingsRow(R.string.app_settings, "com.nichita.gallery", openSettings) }
        item { SettingsSection(R.string.performance) }
        item { SettingsRow(R.string.clear_cache, stringResource(R.string.cache_detail)) {
            (context.applicationContext as? GalleryApplication)?.clearImageMemory()
            Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        } }
        item { SettingsSection(R.string.about) }
        item { SettingsRow(R.string.version, version) }
    }
    val options: List<Pair<String, () -> Unit>> = when (choice) {
        "theme" -> ThemeMode.entries.map { mode -> stringResource(when (mode) {
            ThemeMode.Dark -> R.string.theme_dark
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.System -> R.string.theme_system
        }) to { setTheme(mode) } }
        "sort" -> listOf(stringResource(R.string.newest) to { setOldest(false) }, stringResource(R.string.oldest) to { setOldest(true) })
        "albums" -> listOf(stringResource(R.string.album_recent) to { setAlbumNames(false) }, stringResource(R.string.album_name) to { setAlbumNames(true) })
        "grid" -> (2..8).map { count -> stringResource(R.string.grid_columns, count) to { setColumns(count) } }
        else -> emptyList()
    }
    if (choice != null) AlertDialog(onDismissRequest = { choice = null },
        title = { Text(stringResource(when (choice) {
            "theme" -> R.string.theme
            "sort" -> R.string.sort_order
            "albums" -> R.string.album_order
            else -> R.string.grid_layout
        })) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { options.forEach { (label, action) ->
                TextButton(onClick = { action(); choice = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
            } }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { choice = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SettingsSection(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SettingsRow(title: Int, detail: String, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (action == null) Modifier else Modifier.clickable(onClick = action))
        .padding(vertical = 16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
