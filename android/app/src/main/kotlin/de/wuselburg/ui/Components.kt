package de.wuselburg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import de.wuselburg.R
import de.wuselburg.ui.theme.WuselColors

/** Three stars, [count] of them filled (web `starStr`). */
@Composable
fun Stars(count: Int, fontSize: TextUnit, spacing: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.stars_description, count)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(3) { i ->
            Text("★", fontSize = fontSize, color = if (i < count) WuselColors.Star else WuselColors.StarOff)
        }
    }
}
