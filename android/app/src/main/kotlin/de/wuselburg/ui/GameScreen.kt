// OWNER: UI
package de.wuselburg.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wuselburg.R
import de.wuselburg.core.LevelKind
import de.wuselburg.game.Card
import de.wuselburg.game.GameEvent
import de.wuselburg.game.GameViewModel
import de.wuselburg.game.formatTime
import de.wuselburg.render.FigureIcon
import de.wuselburg.render.IconKind
import de.wuselburg.ui.theme.WuselColors

private const val ZOOM_STEP = 1.5f

@Composable
fun GameScreen(vm: GameViewModel) {
    val camera = vm.camera
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(vm) {
        vm.events.collect {
            when (it) {
                GameEvent.FOUND -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                GameEvent.MISS -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val picture = vm.picture

    fun zoom(factor: Float) {
        camera.cancelFly()
        val newK = (camera.k * factor).coerceIn(camera.minK, camera.maxK)
        camera.zoomAt(viewport.width / 2f, viewport.height / 2f, newK)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(WuselColors.GameGround)
            .onSizeChanged {
                viewport = it
                camera.onViewportChanged(it.width.toFloat(), it.height.toFloat())
            },
    ) {
        if (picture != null) {
            WorldView(
                picture = picture,
                camera = camera,
                effects = vm.effects,
                onTapWorld = vm::tapWorld,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (vm.loading) {
            val description = stringResource(R.string.cd_loading)
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .semantics { contentDescription = description },
                color = WuselColors.Teal,
            )
        }

        Hud(vm, Modifier.align(Alignment.TopStart))

        if (vm.goal != null) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                    .padding(end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundButton("＋", stringResource(R.string.cd_zoom_in), onClick = { zoom(ZOOM_STEP) })
                RoundButton("－", stringResource(R.string.cd_zoom_out), onClick = { zoom(1 / ZOOM_STEP) })
            }
        }

        vm.card?.let { CardOverlay(vm, it) }
    }
}

@Composable
private fun Hud(vm: GameViewModel, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("☰", stringResource(R.string.cd_menu), onClick = vm::openPause)
        val goal = vm.goal
        if (goal != null) {
            Surface(
                Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = WuselColors.HudSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FigureIcon(goal.icon, Modifier.size(width = 40.dp, height = 50.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            goal.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            goal.subtitle, modifier = Modifier.alpha(0.75f), fontSize = 12.5.sp, lineHeight = 15.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = WuselColors.HudSurface, shadowElevation = 6.dp) {
                Text(
                    formatTime(vm.elapsedSeconds),
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                )
            }
            RoundButton(
                "💡", stringResource(R.string.cd_hint),
                color = WuselColors.HintYellow, onClick = vm::useHint,
            )
        } else {
            Box(Modifier.weight(1f))
        }
    }
}

/** 48dp round HUD / zoom button. */
@Composable
private fun RoundButton(
    label: String,
    description: String,
    color: Color = WuselColors.HudSurface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = color,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 22.sp) }
    }
}

// ---------------------------------------------------------------- Cards

private class CardButton(val label: String, val secondary: Boolean = false, val onClick: () -> Unit)

private class CardContent(
    val art: IconKind?,
    val title: String,
    val body: String,
    val stars: Int? = null,
    val buttons: List<CardButton>,
)

@Composable
private fun cardContent(vm: GameViewModel, card: Card): CardContent {
    val res = LocalContext.current.resources
    return when (card) {
        is Card.Intro -> CardContent(
            IconKind.FIPS, card.title, card.body,
            buttons = listOf(CardButton(res.getString(R.string.intro_start), onClick = vm::beginPlay)),
        )
        is Card.StepFound -> CardContent(
            IconKind.MAGNIFIER, res.getString(R.string.step_found_title), card.body,
            buttons = listOf(CardButton(res.getString(R.string.button_continue), onClick = vm::continueAfterStep)),
        )
        Card.Pause -> CardContent(
            null, res.getString(R.string.pause_title), res.getString(R.string.pause_body),
            buttons = listOf(
                CardButton(res.getString(R.string.button_resume), onClick = vm::resume),
                CardButton(res.getString(R.string.button_menu), secondary = true, onClick = vm::showMenu),
            ),
        )
        is Card.Win -> {
            val case = card.kind == LevelKind.CASE
            val stats = res.getString(
                R.string.stats_line,
                formatTime(card.seconds),
                res.getQuantityString(R.plurals.misses, card.misses, card.misses),
                res.getQuantityString(R.plurals.hints, card.hints, card.hints),
            )
            val buttons = buildList {
                add(CardButton(res.getString(R.string.button_retry), secondary = true, onClick = vm::restart))
                if (card.hasNext) add(CardButton(res.getString(R.string.button_next_level), onClick = vm::nextLevel))
                add(CardButton(res.getString(R.string.button_menu), secondary = true, onClick = vm::showMenu))
            }
            CardContent(
                if (case) IconKind.RACCOON_CAKE else IconKind.FIPS,
                res.getString(if (case) R.string.win_case else R.string.win_fips),
                (card.epilogue ?: res.getString(R.string.epilogue_default)) + "\n" + stats,
                stars = card.stars,
                buttons = buttons,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardOverlay(vm: GameViewModel, card: Card) {
    val content = cardContent(vm, card)
    // "pop" animation of the web card, replayed for every new card
    val pop = remember(card) { Animatable(0f) }
    LaunchedEffect(card) { pop.animateTo(1f, tween(250)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(WuselColors.Scrim)
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches meant for the world
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.85f + 0.15f * pop.value
                    scaleX = s; scaleY = s; alpha = pop.value
                },
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content.art?.let { FigureIcon(it, Modifier.size(width = 90.dp, height = 112.dp)) }
                Text(
                    content.title,
                    Modifier.padding(top = 6.dp, bottom = 8.dp),
                    color = WuselColors.TealDark, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (content.body.isNotEmpty()) {
                    Text(
                        content.body,
                        Modifier.padding(bottom = 12.dp),
                        fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center,
                    )
                }
                content.stars?.let { Stars(it, fontSize = 32.sp, spacing = 4.dp, modifier = Modifier.padding(bottom = 10.dp)) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content.buttons.forEach { b ->
                        Button(
                            onClick = b.onClick,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp),
                            colors = if (b.secondary) {
                                ButtonDefaults.buttonColors(
                                    containerColor = WuselColors.ButtonSecondary, contentColor = WuselColors.Ink,
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = WuselColors.Teal, contentColor = Color.White,
                                )
                            },
                        ) { Text(b.label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
