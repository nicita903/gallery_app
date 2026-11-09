package com.nichita.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    refresh: () -> Unit, favoritesOnly: Boolean, toggleFavorites: () -> Unit,
    openDrawer: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("[ >_ ]", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = openDrawer).padding(end = 8.dp),
                style = MaterialTheme.typography.labelMedium)
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
    val accent = Color(0xFFFF3030)
    val inactive = Color(0xFFAAAAAA)
    val surface = Color(0xE6101010)
    val border = Color(0xFF292929)

    val tabs = GalleryTab.entries
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

    val indicatorPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(
            durationMillis = 220,
            easing = GalleryMotion.Easing
        ),
        label = "Terminal navigation indicator"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
            color = surface,
            border = BorderStroke(1.dp, border),
            shadowElevation = 0.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val itemWidth = maxWidth / tabs.size

                // Moving red indicator
                Box(
                    modifier = Modifier
                        .offset(
                            x = itemWidth * indicatorPosition
                        )
                        .width(itemWidth)
                        .height(2.dp)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .height(2.dp)
                            .background(
                                accent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->

                        val isSelected = selected == tab

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                accent
                            } else {
                                inactive
                            },
                            animationSpec = tween(180),
                            label = "Navigation text color"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isSelected) select(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Text(
                                        text = ">",
                                        color = accent,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.width(3.dp))
                                }

                                Text(
                                    text = when (tab) {
                                        GalleryTab.Photos -> "Photos"
                                        GalleryTab.Albums -> "Albums"
                                        GalleryTab.Search -> "Search"
                                        GalleryTab.Settings -> "Settings"
                                    },
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (index < tabs.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(Color(0xFF292929))
                            )
                        }
                    }
                }
            }
        }
    }
}
