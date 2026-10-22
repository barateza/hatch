package pw.barateza.hatch.theme

import androidx.compose.ui.graphics.Color

/**
 * Hatch prototype palette. Variants are deliberately allowed to break away from
 * these colours — they live here so the shell (settings, auth, dock) can stay
 * coherent across all three directions.
 */
object HatchPalette {
  // Aurora / shell
  val Ink = Color(0xFF05070C)
  val InkSoft = Color(0xFF0B1018)
  val Paper = Color(0xFFF4F1E9)
  val Mint = Color(0xFF7DF5C9)
  val MintDeep = Color(0xFF0F5C48)
  val Sky = Color(0xFF6CC9FF)
  val Violet = Color(0xFF9B8CFF)
  val Coral = Color(0xFFFF7A5C)
  val Amber = Color(0xFFFFC46B)
  val Fog = Color(0xFF8A93A6)
  val Line = Color(0xFF1D2531)

  // Grid direction
  val GridPaper = Color(0xFFF2F3EC)
  val GridInk = Color(0xFF10120E)
  val GridLime = Color(0xFFD8FF3E)
  val GridRule = Color(0xFFD6D9C9)

  // Tactile direction
  val TactileCream = Color(0xFFF3E9D8)
  val TactileClay = Color(0xFFC56B4A)
  val TactileOlive = Color(0xFF5A6248)
  val TactileInk = Color(0xFF2B261E)
  val TactilePaper = Color(0xFFEDE0CA)
}
