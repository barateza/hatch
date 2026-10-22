package pw.barateza.hatch.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme =
  darkColorScheme(
    primary = HatchPalette.Mint,
    onPrimary = Color(0xFF04130E),
    secondary = HatchPalette.Violet,
    tertiary = HatchPalette.Coral,
    background = HatchPalette.Ink,
    onBackground = Color(0xFFE8EDF4),
    surface = HatchPalette.InkSoft,
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF141B25),
    onSurfaceVariant = HatchPalette.Fog,
    outline = HatchPalette.Line,
    error = HatchPalette.Coral,
  )

private val LightScheme =
  lightColorScheme(
    primary = Color(0xFF0E6B52),
    onPrimary = Color.White,
    secondary = Color(0xFF5B4BD1),
    tertiary = HatchPalette.Coral,
    background = HatchPalette.Paper,
    onBackground = HatchPalette.GridInk,
    surface = Color(0xFFFBF9F3),
    onSurface = HatchPalette.GridInk,
    surfaceVariant = Color(0xFFE8E4D9),
    onSurfaceVariant = Color(0xFF5A5F52),
    outline = Color(0xFFC9C5B7),
  )

/**
 * Shell theme. The three variant screens are self-contained and may override
 * MaterialTheme locally; this is used by settings, the auth WebView chrome and
 * the prototype dock.
 */
@Composable
fun HatchTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkScheme else LightScheme,
    typography = HatchTypography,
    content = content,
  )
}
