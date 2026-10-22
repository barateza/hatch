package pw.barateza.hatch.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pw.barateza.hatch.R

private fun grotesk(weight: Int) =
  Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
  )

private fun inter(weight: Int) =
  Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
  )

private fun mono(weight: Int) =
  Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
  )

/** Editorial display face used by Aurora / Grid headlines. */
val SpaceGrotesk =
  FontFamily(
    grotesk(400),
    grotesk(500),
    grotesk(600),
    grotesk(700),
  )

/** Neutral, very legible body face. */
val Inter =
  FontFamily(
    inter(400),
    inter(500),
    inter(600),
  )

/** Telemetry / endpoints / logs. */
val JetBrainsMono =
  FontFamily(
    mono(400),
    mono(500),
    mono(700),
  )

/** Warm editorial face for the Tactile direction. */
val InstrumentSerif =
  FontFamily(
    Font(R.font.instrument_serif, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(R.font.instrument_serif_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
  )

val HatchTypography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 52.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.8).sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
      ),
  )
