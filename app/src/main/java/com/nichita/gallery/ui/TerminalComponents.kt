package com.nichita.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nichita.gallery.R

internal val GalleryTab.label: Int get() = when (this) {
    GalleryTab.Photos -> R.string.photos
    GalleryTab.Albums -> R.string.albums
    GalleryTab.Search -> R.string.search
    GalleryTab.Settings -> R.string.settings
}
internal val GalleryTab.icon: Int get() = when (this) {
    GalleryTab.Photos -> R.drawable.ic_photos
    GalleryTab.Albums -> R.drawable.ic_albums
    GalleryTab.Search -> R.drawable.ic_search
    GalleryTab.Settings -> R.drawable.ic_settings
}

@Composable
internal fun TerminalHeader(title: String, search: () -> Unit, settings: () -> Unit,
    refresh: () -> Unit, favoritesOnly: Boolean, toggleFavorites: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("[ >_ ]", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium)
            Text("gallery@local:~$", fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = search) { Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (favoritesOnly) R.string.all_media else R.string.favorites)) },
                        onClick = { menu = false; toggleFavorites() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, onClick = { menu = false; refresh() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; settings() })
                }
            }
        }
        Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("> ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f, fill = false))
            Text("_", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
internal fun TerminalNavigation(selected: GalleryTab, select: (GalleryTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        GalleryTab.entries.forEach { tab ->
            val active = selected == tab
            NavigationBarItem(selected = active, onClick = { select(tab) }, icon = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(tab.icon), null, Modifier.size(24.dp))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(20.dp, 2.dp).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }, label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent))
        }
    }
}
