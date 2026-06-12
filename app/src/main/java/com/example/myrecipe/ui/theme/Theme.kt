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
    primary = ChefPrimary,
    secondary = ChefSecondary,
    tertiary = ChefTertiary,
    background = ChefOnBackground,
    surface = ChefSecondary,
    onPrimary = ChefOnPrimary,
    onSecondary = ChefOnPrimary,
    onTertiary = ChefOnPrimary,
    onBackground = ChefBackground,
    onSurface = ChefBackground
)

private val LightColorScheme = lightColorScheme(
    primary = ChefPrimary,
    secondary = ChefSecondary,
    tertiary = ChefTertiary,
    background = ChefBackground,
    surface = ChefSurface,
    onPrimary = ChefOnPrimary,
    onSecondary = ChefOnPrimary,
    onTertiary = ChefOnPrimary,
    onBackground = ChefOnBackground,
    onSurface = ChefOnBackground
)

@Composable
fun MyRecipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to maintain the specific brand palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
