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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars

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
internal fun TerminalNavigation(
    selected: GalleryTab,
    select: (GalleryTab) -> Unit
) {
    val accentRed = Color(0xFFFF3030)
    val barBackground = Color(0xA3151515)
    val selectedBackground = Color(0xFF351818)
    val inactiveColor = Color(0xFFB0B0B0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = 8.dp,
                top = 4.dp
            )
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),

            shape = RoundedCornerShape(38.dp),

            color = barBackground,

            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF303030)
            ),

            shadowElevation = 0.dp
        ) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),

                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                GalleryTab.entries.forEach { tab ->

                    val active = selected == tab

                    val iconColor by animateColorAsState(
                        targetValue = if (active) accentRed else inactiveColor,
                        animationSpec = tween(200),
                        label = "Navigation icon color"
                    )

                    val indicatorColor by animateColorAsState(
                        targetValue = if (active)
                            selectedBackground
                        else
                            Color.Transparent,
                        animationSpec = tween(200),
                        label = "Navigation indicator"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .clickable {
                                if (!active) {
                                    select(tab)
                                }
                            },

                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {


                        Box(
                            modifier = Modifier
                                .size(
                                    width = 54.dp,
                                    height = 34.dp
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(indicatorColor),

                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = stringResource(tab.label),
                                tint = iconColor,
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = stringResource(tab.label),
                            color = if (active)
                                accentRed
                            else
                                Color(0xFFE0E0E0),

                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
