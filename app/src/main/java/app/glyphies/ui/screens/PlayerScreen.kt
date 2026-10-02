package app.glyphies.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.data.GameTemplate
import app.glyphies.data.description
import app.glyphies.engine.Need
import app.glyphies.glyph.GlyphOutput
import app.glyphies.glyph.GlyphSupport
import app.glyphies.play.BuiltIn
import app.glyphies.play.PlayState
import app.glyphies.tr
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.IconBtn
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.components.StatusChip
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/**
 * What's playing, full screen: the matrix as large as it fits, the score, and the controls.
 * The whole screen is a button (tap = action) and a slider (finger position) — fingers behind
 * the phone reach it while you look at the back.
 */
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val state by Graph.player.state.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<PlayState?>(null) }
    state?.let { last = it }
    val s = last ?: return
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val sending by GlyphOutput.sending.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current

    // Keep the screen on while playing.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Voice-driven things need the microphone: ask once when they start.
    val needsMic = Need.MIC in s.needs
    var micAllowed by remember { mutableStateOf(Graph.sensors.mic.permitted) }
    val micAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAllowed = granted
        if (granted) Graph.player.refreshSensors()
    }
    LaunchedEffect(s.title, needsMic) {
        if (needsMic && !Graph.sensors.mic.permitted) micAsk.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(P.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    Graph.sensors.tap()
                    Graph.sensors.touch(true, down.position.x / w)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        Graph.sensors.touchMove(change.position.x / w)
                    }
                    Graph.sensors.touch(false, null)
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Close, onClose, contentDescription = tr("Close", "Fermer"))
                Spacer(Modifier.width(6.dp))
                Text(s.title, style = Type.titleLarge, color = P.text, maxLines = 1, modifier = Modifier.weight(1f))
                StatusChip(
                    when {
                        sending -> "GLYPH MATRIX"
                        GlyphSupport.isSupported && !settings.glyphOutput -> tr("SCREEN ONLY", "ÉCRAN SEUL")
                        GlyphSupport.isSupported -> tr("CONNECTING", "CONNEXION")
                        else -> tr("PREVIEW", "APERÇU")
                    },
                    on = sending,
                )
            }

            Spacer(Modifier.height(18.dp))
            Hud(s)
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(CircleShape)
                    .background(if (P.isDark) P.surface else P.surfaceHigh)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                MatrixView(s.frame, s.shape, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(18.dp))
            if (s.newBest) {
                Text(tr("NEW BEST!", "NOUVEAU RECORD !"), style = Type.displaySmall, color = P.accent)
                Spacer(Modifier.height(8.dp))
            }
            if (s.paused) {
                Text(tr("PAUSED", "EN PAUSE"), style = Type.displaySmall, color = P.textDim)
                Spacer(Modifier.height(8.dp))
            }
            Text(hint(s), style = Type.label, color = P.textDim, textAlign = TextAlign.Center)
            if (needsMic && !micAllowed) {
                Spacer(Modifier.height(10.dp))
                PillButton(tr("Allow the microphone", "Autoriser le micro"), { micAsk.launch(Manifest.permission.RECORD_AUDIO) }, icon = Ic.Mic, style = PillStyle.Accent)
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.builtIn == BuiltIn.SAND) {
                    RoundButton("−") { Graph.sensors.volumeKey(plus = false, pressed = true); Graph.sensors.volumeKey(plus = false, pressed = false) }
                    RoundButton("+") { Graph.sensors.volumeKey(plus = true, pressed = true); Graph.sensors.volumeKey(plus = true, pressed = false) }
                }
                if (s.builtIn == BuiltIn.HOURGLASS) {
                    PillButton(duration(settings.hourglassSeconds), {
                        sheets(
                            SheetSpec(
                                title = tr("Hourglass", "Sablier"),
                                actions = listOf(30, 60, 180, 300, 600).map { sec ->
                                    SheetAction(duration(sec), if (sec == settings.hourglassSeconds) Ic.Check else null) {
                                        Graph.settings.update { it.copy(hourglassSeconds = sec) }
                                        Graph.player.play(BuiltIn.HOURGLASS)
                                    }
                                },
                            )
                        )
                    })
                }
                PillButton(
                    if (s.paused) tr("Resume", "Reprendre") else tr("Pause", "Pause"),
                    { Graph.player.togglePause() },
                    icon = if (s.paused) Ic.Play else Ic.Pause,
                )
                PillButton(tr("Restart", "Recommencer"), { Graph.player.restart() }, icon = Ic.Refresh)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                settings.facing.description,
                style = Type.label,
                color = P.textFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun Hud(s: PlayState) {
    val h = s.hud
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        if (h.score != null) Stat(tr("SCORE", "SCORE"), h.score.toString())
        if (h.lives != null) Stat(tr("LIVES", "VIES"), "●".repeat(h.lives.coerceIn(0, 5)).ifEmpty { "–" })
        if (h.level != null) Stat(if (s.isGame) tr("LEVEL", "NIVEAU") else tr("FRAME", "IMAGE"), h.level.toString())
        if (h.detail != null) Stat(if (s.isGame) "" else tr("NOW", "EN COURS"), h.detail)
        if (s.isGame) Stat(tr("BEST", "RECORD"), s.best.toString())
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.display.copy(fontSize = 30.sp), color = P.text, maxLines = 1)
        if (label.isNotEmpty()) Text(label, style = Type.label, color = P.textDim)
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, P.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.title, color = P.text)
    }
}

/** How to play, one line. */
private fun hint(s: PlayState): String {
    s.builtIn?.let { return it.controls }
    val c = Graph.creations.get(s.creationId) ?: return ""
    val g = c.game
    if (g == null) return c.driver.description
    val move = if (g.template.moves.isNotEmpty()) g.move.description else ""
    val trigger = if (g.template.triggers.isNotEmpty()) g.trigger.description else ""
    return listOf(
        when (g.template) {
            GameTemplate.MAZE -> tr("Tilt to roll the ball to the blinking goal.", "Inclinez pour rouler la bille jusqu'à l'arrivée qui clignote.")
            else -> move
        },
        trigger,
    ).filter { it.isNotBlank() }.joinToString("  ")
}
