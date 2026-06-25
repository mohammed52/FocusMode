package com.example.focusmode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BohraGoldLight,
    onPrimary = BohraOnGoldLight,
    primaryContainer = BohraGoldContainerDark,
    onPrimaryContainer = BohraOnGoldDark,
    secondary = BohraEmeraldLight,
    onSecondary = BohraIvoryDark,
    secondaryContainer = BohraEmeraldContainerDark,
    onSecondaryContainer = BohraOnEmeraldContainerDark,
    tertiary = BohraMaroonLight,
    onTertiary = BohraIvoryDark,
    tertiaryContainer = BohraMaroonContainerDark,
    onTertiaryContainer = BohraOnMaroonContainerDark,
    background = BohraIvoryDark,
    onBackground = BohraOnIvoryDark,
    surface = BohraIvoryDark,
    onSurface = BohraOnIvoryDark,
    surfaceVariant = BohraSurfaceVariantDark,
    onSurfaceVariant = BohraOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = BohraGold,
    onPrimary = BohraOnGoldLight,
    primaryContainer = BohraGoldContainerLight,
    onPrimaryContainer = BohraOnGoldLight,
    secondary = BohraEmerald,
    onSecondary = BohraIvory,
    secondaryContainer = BohraEmeraldContainerLight,
    onSecondaryContainer = BohraOnEmeraldContainerLight,
    tertiary = BohraMaroon,
    onTertiary = BohraIvory,
    tertiaryContainer = BohraMaroonContainerLight,
    onTertiaryContainer = BohraOnMaroonContainerLight,
    background = BohraIvory,
    onBackground = BohraOnIvory,
    surface = BohraIvory,
    onSurface = BohraOnIvory,
    surfaceVariant = BohraSurfaceVariantLight,
    onSurfaceVariant = BohraOnSurfaceVariantLight
)

@Composable
fun FocusModeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color would replace this deliberate Ivory & Gold palette with the wallpaper-derived
    // system one on Android 12+, defeating the point of having a brand palette — off by default
    // now that there is one. Left as a parameter rather than deleted in case a future caller
    // wants it (e.g. a settings toggle), but nothing in this app turns it on today.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
