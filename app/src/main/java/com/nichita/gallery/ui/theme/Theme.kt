package com.nichita.gallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF3030),
    onPrimary = Color.White,

    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = Color(0xFFF5F5F5),

    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),

    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F5),

    surfaceContainer = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF181818),

    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = Color(0xFFA0A0A0),

    outlineVariant = Color(0xFF202020),
    outline = Color(0xFF555555),

    error = Color(0xFFFF3030)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFFD92525), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8E8), onPrimaryContainer = Color(0xFF7A1717),
    background = Color.White, onBackground = Color(0xFF171B19),
    surface = Color.White, onSurface = Color(0xFF171B19),
    surfaceVariant = Color(0xFFF0F3F1), onSurfaceVariant = Color(0xFF58625D)
)

@Composable
fun GalleryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
