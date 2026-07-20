package com.toyrobotworkshop.auspex.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color as AndroidColor

/**
 * Dark colour scheme — seed: deep blue #1565C0.
 * Defines the full set of M3 roles so nothing falls back to mismatched defaults.
 */
private val DarkColorScheme = darkColorScheme(
    primary       = Color(0xFF90CAF9),
    onPrimary     = Color(0xFF003258),
    primaryContainer    = Color(0xFF064577),
    onPrimaryContainer  = Color(0xFFD1E4FF),

    secondary     = Color(0xFFB0BEC5),
    onSecondary   = Color(0xFF263035),
    secondaryContainer    = Color(0xFF3C474D),
    onSecondaryContainer  = Color(0xFFDCE4EA),

    tertiary      = Color(0xFF80CBC4),
    onTertiary    = Color(0xFF00372F),
    tertiaryContainer     = Color(0xFF004D43),
    onTertiaryContainer   = Color(0xFF9EF3E6),

    error         = Color(0xFFCF6679),
    onError       = Color(0xFF690005),
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),

    background    = Color(0xFF121212),
    onBackground  = Color(0xFFE6E1E5),

    surface       = Color(0xFF1E1E1E),
    onSurface     = Color(0xFFE6E1E5),
    surfaceVariant= Color(0xFF49454F),
    onSurfaceVariant= Color(0xFFCAC4D0),

    surfaceContainerHigh  = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),

    outline       = Color(0xFF938F99),
    outlineVariant= Color(0xFF49454F),
)

/**
 * Light colour scheme — seed: deep blue #1565C0.
 * Defines the full set of M3 roles so nothing falls back to mismatched defaults.
 */
private val LightColorScheme = lightColorScheme(
    primary       = Color(0xFF1976D2),
    onPrimary     = Color.White,
    primaryContainer    = Color(0xFFD1E4FF),
    onPrimaryContainer  = Color(0xFF001D36),

    secondary     = Color(0xFF5C6368),
    onSecondary   = Color.White,
    secondaryContainer    = Color(0xFFDDE3EA),
    onSecondaryContainer  = Color(0xFF191C20),

    tertiary      = Color(0xFF00897B),
    onTertiary    = Color.White,
    tertiaryContainer     = Color(0xFF6FF2DF),
    onTertiaryContainer   = Color(0xFF002A24),

    error         = Color(0xFFBA1A1A),
    onError       = Color.White,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),

    background    = Color(0xFFF5F5F5),
    onBackground  = Color(0xFF1C1B1F),

    surface       = Color.White,
    onSurface     = Color(0xFF1C1B1F),
    surfaceVariant= Color(0xFFE2E1EC),
    onSurfaceVariant= Color(0xFF49454F),

    surfaceContainerHigh  = Color(0xFFF3F0F4),
    surfaceContainerHighest = Color(0xFFEBE8ED),

    outline       = Color(0xFF79747E),
    outlineVariant= Color(0xFFCAC4D0),
)

@Composable
fun AuspexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Transparent bars — content draws behind them, system handles contrast scrim
            //window.statusBarColor     = AndroidColor.TRANSPARENT
            //window.navigationBarColor = AndroidColor.TRANSPARENT

            // Match icon/handle appearance to active theme
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars    = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
