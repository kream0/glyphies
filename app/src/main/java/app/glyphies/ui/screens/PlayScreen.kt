package app.glyphies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.data.Creation
import app.glyphies.engine.GameConfig
import app.glyphies.engine.HourglassToy
import app.glyphies.engine.InvadersGame
import app.glyphies.engine.Move
import app.glyphies.engine.SandToy
import app.glyphies.engine.Trigger
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.play.BuiltIn
import app.glyphies.tr
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.Tab
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.LivePreview
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.ScreenHeader
import app.glyphies.ui.components.Section
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.components.StatusChip
import app.glyphies.ui.components.Thumbs
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

@Composable
fun PlayScreen(app: AppViewModel) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val creations by Graph.creations.all.collectAsStateWithLifecycle()
    val best by Graph.scores.best.collectAsStateWithLifecycle()
    val shape = GlyphSupport.displayShape(settings.previewSize)
    val sheets = LocalSheets.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader("GLYPHIES") {
            StatusChip(
                if (GlyphSupport.isSupported) "${shape.width}×${shape.height}" else tr("PREVIEW", "APERÇU"),
                on = GlyphSupport.isSupported && settings.glyphOutput,
            )
        }
        Text(
            if (GlyphSupport.isSupported) {
                tr(
                    "${GlyphSupport.deviceName}: everything you start shows on the Glyph Matrix on the back.",
                    "${GlyphSupport.deviceName} : tout ce que vous lancez s'affiche sur la Glyph Matrix au dos.",
                )
            } else {
                tr(
                    "No Glyph Matrix on this phone: everything plays on the screen, as it would on a Phone (${if (shape.width == 13) "4a) Pro" else "3)"}.",
                    "Pas de Glyph Matrix sur ce téléphone : tout se joue à l'écran, comme sur un Phone (${if (shape.width == 13) "4a) Pro" else "3)"}.",
                )
            },
            style = Type.label,
            color = P.textDim,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Section(tr("Animations", "Animations")) {
            BuiltInCard(BuiltIn.SAND, shape, null) { Graph.player.play(BuiltIn.SAND) }
            BuiltInCard(BuiltIn.HOURGLASS, shape, null) {
                val choices = listOf(30, 60, 180, 300, 600)
                sheets(
                    SheetSpec(
                        title = BuiltIn.HOURGLASS.title,
                        subtitle = tr("How long?", "Combien de temps ?"),
                        actions = choices.map { s ->
                            SheetAction(duration(s), if (s == settings.hourglassSeconds) Ic.Check else null) {
                                Graph.settings.update { it.copy(hourglassSeconds = s) }
                                Graph.player.play(BuiltIn.HOURGLASS)
                            }
                        },
                    )
                )
            }
        }

        Section(tr("Games", "Jeux")) {
            BuiltInCard(BuiltIn.INVADERS, shape, best[BuiltIn.INVADERS.id]) { Graph.player.play(BuiltIn.INVADERS) }
        }

        Section(tr("Your creations", "Vos créations")) {
            if (creations.isEmpty()) {
                Text(
                    tr("Nothing yet: draw something in Create.", "Rien pour l'instant : dessinez quelque chose dans Créer."),
                    style = Type.body,
                    color = P.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            creations.take(8).forEach { c ->
                CreationRow(c, best[c.id], onPlay = { Graph.player.play(c.id) }, onEdit = { app.edit(c.id) })
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                PillButton(tr("Open the editor", "Ouvrir l'éditeur"), { app.selectTab(Tab.CREATE) }, icon = Ic.Edit)
            }
        }
    }
}

fun duration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

@Composable
private fun BuiltInCard(b: BuiltIn, shape: MatrixShape, best: Int?, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(P.surface)
            .border(1.dp, P.outline, RoundedCornerShape(24.dp))
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(if (P.isDark) P.background else P.surfaceHigh)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            LivePreview(key = "${b.name}:${shape.width}", modifier = Modifier.fillMaxSize()) {
                when (b) {
                    BuiltIn.SAND -> SandToy(shape)
                    BuiltIn.HOURGLASS -> HourglassToy(shape, seconds = 20)
                    BuiltIn.INVADERS -> InvadersGame(shape, GameConfig(move = Move.TOUCH, trigger = Trigger.AUTO))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title.uppercase(), style = Type.displaySmall, color = P.text)
            Spacer(Modifier.height(4.dp))
            Text(b.blurb, style = Type.body, color = P.textDim)
            Spacer(Modifier.height(6.dp))
            Text(b.controls.uppercase(), style = Type.label, color = P.textFaint)
            if (best != null && best > 0) {
                Spacer(Modifier.height(6.dp))
                Text(tr("BEST $best", "RECORD $best"), style = Type.labelBold, color = P.accent)
            }
        }
    }
}

@Composable
private fun CreationRow(c: Creation, best: Int?, onPlay: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (P.isDark) P.surface else P.surfaceHigh)
                .padding(5.dp),
        ) {
            MatrixView(Thumbs.of(c), c.shape, Modifier.fillMaxSize(), gap = 0.12f)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = Type.title, color = P.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                c.kindLabel + (if (best != null && best > 0) tr(" · BEST $best", " · RECORD $best") else ""),
                style = Type.label,
                color = P.textDim,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                Icon(Ic.Edit, contentDescription = tr("Edit", "Modifier"), tint = P.textDim, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(P.inverse).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Play, contentDescription = tr("Play", "Jouer"), tint = P.onInverse, modifier = Modifier.size(22.dp))
            }
        }
    }
}
