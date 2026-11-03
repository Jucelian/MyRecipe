package com.example.myrecipe.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ChefDarkPrimary,
    onPrimary = ChefDarkOnPrimary,
    primaryContainer = ChefDarkPrimaryContainer,
    onPrimaryContainer = ChefDarkOnPrimaryContainer,
    secondary = ChefDarkSecondary,
    onSecondary = ChefDarkOnSecondary,
    secondaryContainer = ChefDarkSecondaryContainer,
    onSecondaryContainer = ChefDarkOnSecondaryContainer,
    tertiary = ChefDarkTertiary,
    onTertiary = ChefDarkOnTertiary,
    tertiaryContainer = ChefDarkTertiaryContainer,
    onTertiaryContainer = ChefDarkOnTertiaryContainer,
    background = ChefDarkBackground,
    onBackground = ChefDarkOnBackground,
    surface = ChefDarkSurface,
    onSurface = ChefDarkOnSurface,
    surfaceVariant = ChefDarkSurfaceVariant,
    onSurfaceVariant = ChefDarkOnSurfaceVariant,
    outline = ChefOutline,
    outlineVariant = ChefOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = ChefPrimary,
    onPrimary = ChefOnPrimary,
    primaryContainer = ChefPrimaryContainer,
    onPrimaryContainer = ChefOnPrimaryContainer,
    secondary = ChefSecondary,
    onSecondary = ChefOnSecondary,
    secondaryContainer = ChefSecondaryContainer,
    onSecondaryContainer = ChefOnSecondaryContainer,
    tertiary = ChefTertiary,
    onTertiary = ChefOnTertiary,
    tertiaryContainer = ChefTertiaryContainer,
    onTertiaryContainer = ChefOnTertiaryContainer,
    background = ChefBackground,
    onBackground = ChefOnBackground,
    surface = ChefSurface,
    onSurface = ChefOnSurface,
    surfaceVariant = ChefSurfaceVariant,
    onSurfaceVariant = ChefOnSurfaceVariant,
    outline = ChefOutline,
    outlineVariant = ChefOutlineVariant
)

@Composable
fun MyRecipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
