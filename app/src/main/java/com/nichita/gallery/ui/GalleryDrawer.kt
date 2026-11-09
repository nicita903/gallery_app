package com.nichita.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Drawer sheet to place inside ModalNavigationDrawer(drawerContent = { ... }). */
@Composable
internal fun GalleryDrawer(
    width: Dp,
    selected: GalleryTab,
    onSelect: (GalleryTab) -> Unit
) {
    val context = LocalContext.current
    val version = androidx.compose.runtime.remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    val accent = MaterialTheme.colorScheme.primary
    ModalDrawerSheet(
        modifier = Modifier.width(width),
        drawerContainerColor = Color(0xFF101010)
    ) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("[ >_ ]", color = accent, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text("gallery@local:~$", color = Color(0xFFA0A0A0),
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            }
            Text("> MODULES", modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                color = Color(0xFFA0A0A0), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium)
            HorizontalDivider(color = Color(0xFF292929))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                GalleryTab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(tab.label)) },
                        selected = selected == tab,
                        icon = { Icon(painterResource(tab.icon), contentDescription = null,
                            modifier = Modifier.size(22.dp)) },
                        onClick = { onSelect(tab) },
                        modifier = Modifier.padding(vertical = 3.dp).heightIn(min = 52.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.12f),
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            unselectedIconColor = Color(0xFFAAAAAA),
                            unselectedTextColor = Color(0xFFEAEAEA)
                        )
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF292929))
            Text("LOCAL ONLY / v$version", modifier = Modifier.padding(top = 16.dp),
                color = Color(0xFF929292), fontFamily = FontFamily.Monospace,
                fontSize = 11.sp)
        }
    }
}
