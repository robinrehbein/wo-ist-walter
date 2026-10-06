// OWNER: UI
package de.wuselburg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette of the web version (style.css :root). */
object WuselColors {
    val Teal = Color(0xFF1F8F8A)
    val TealDark = Color(0xFF176C68)
    val Orange = Color(0xFFE8802A)
    val Cream = Color(0xFFFDF4E3)
    val Ink = Color(0xFF2B2A33)
    val MenuSky = Color(0xFFBFE6E3)
    val GameGround = Color(0xFFBFE3A4)
    val HintYellow = Color(0xFFFFE58A)
    val Star = Color(0xFFF2B01E)
    val StarOff = Color(0xFFD8D3C6)
    val ButtonSecondary = Color(0xFFE7E1D3)
    val HudSurface = Color(0xF0FFFFFF)
    val Scrim = Color(0x8C1E282D)
}

private val Colors = lightColorScheme(
    primary = WuselColors.Teal,
    onPrimary = Color.White,
    secondary = WuselColors.Orange,
    onSecondary = Color.White,
    background = WuselColors.Cream,
    onBackground = WuselColors.Ink,
    surface = Color.White,
    onSurface = WuselColors.Ink,
)

@Composable
fun WuselburgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
