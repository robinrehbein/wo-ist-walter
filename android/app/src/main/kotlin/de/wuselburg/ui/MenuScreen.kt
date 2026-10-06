// OWNER: UI
package de.wuselburg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wuselburg.R
import de.wuselburg.core.LevelDef
import de.wuselburg.core.LevelKind
import de.wuselburg.game.GameViewModel
import de.wuselburg.render.FigureIcon
import de.wuselburg.render.IconKind
import de.wuselburg.ui.theme.WuselColors

@Composable
fun MenuScreen(vm: GameViewModel) {
    val sky = Brush.verticalGradient(0f to WuselColors.MenuSky, 0.45f to WuselColors.Cream, 1f to WuselColors.Cream)
    Column(
        Modifier
            .fillMaxSize()
            .background(sky)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FigureIcon(IconKind.FIPS, Modifier.size(width = 110.dp, height = 130.dp))
            Text(
                stringResource(R.string.menu_title),
                modifier = Modifier.padding(vertical = 4.dp),
                color = WuselColors.TealDark,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                stringResource(R.string.menu_tagline),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.levels.forEach { level ->
                    LevelCard(level, vm.bestStars[level.id] ?: 0) { vm.startLevel(level.id) }
                }
            }
            Text(
                stringResource(R.string.menu_hint),
                modifier = Modifier
                    .padding(top = 22.dp)
                    .alpha(0.65f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LevelCard(level: LevelDef, stars: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FigureIcon(
                if (level.kind == LevelKind.CASE) IconKind.CAKE else IconKind.FIPS,
                Modifier.size(width = 44.dp, height = 54.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(level.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(level.subtitle, modifier = Modifier.alpha(0.75f), fontSize = 14.sp)
            }
            Stars(stars, fontSize = 18.sp, spacing = 2.dp)
        }
    }
}
