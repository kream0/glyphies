package app.glyphies.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.glyphies.tr
import app.glyphies.ui.components.DotGlyphs
import app.glyphies.ui.components.DotIcon
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/**
 * The drawing surface: the matrix (draw with a finger), the tools, the brightness, and — when
 * [frameLabel] is given — the strip of frames (or levels) underneath.
 */
@Composable
fun PixelEditor(
    state: PaintState,
    modifier: Modifier = Modifier,
    canvasWidth: Dp = Dp.Unspecified,
    marks: Boolean = false,
    frameLabel: ((Int) -> String)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .then(if (canvasWidth != Dp.Unspecified) Modifier.width(canvasWidth) else Modifier.fillMaxWidth(0.92f))
                .clip(RoundedCornerShape(if (state.shape.isRound) 400.dp else 18.dp))
                .background(if (P.isDark) P.surface else P.surfaceHigh)
                .padding(if (state.shape.isRound) 14.dp else 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            MatrixView(
                frame = state.current,
                shape = state.shape,
                modifier = Modifier.fillMaxWidth(),
                ghost = if (state.onion) state.previous else null,
                marks = marks,
                onPaint = state::paint,
                onStrokeEnd = state::endStroke,
            )
        }
        Spacer(Modifier.height(14.dp))

        // Tools
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(DotGlyphs.PEN, tr("Pen", "Crayon"), state.tool == Tool.PEN) { state.tool = Tool.PEN }
            ToolButton(DotGlyphs.ERASE, tr("Eraser", "Gomme"), state.tool == Tool.ERASE) { state.tool = Tool.ERASE }
            ToolButton(DotGlyphs.FILL, tr("Fill", "Remplir"), state.tool == Tool.FILL) { state.tool = Tool.FILL }
            ToolButton(DotGlyphs.MOVE, tr("Move", "Déplacer"), state.tool == Tool.MOVE) { state.tool = Tool.MOVE }
            if (marks) {
                Divider()
                ToolButton(DotGlyphs.START, tr("Start", "Départ"), state.tool == Tool.START) { state.tool = Tool.START }
                ToolButton(DotGlyphs.GOAL, tr("Goal", "Arrivée"), state.tool == Tool.GOAL, accent = true) { state.tool = Tool.GOAL }
                ToolButton(DotGlyphs.TRAP, tr("Trap", "Piège"), state.tool == Tool.TRAP, accent = true) { state.tool = Tool.TRAP }
            }
            Divider()
            ToolButton(DotGlyphs.MIRROR, tr("Mirror", "Miroir"), state.mirror) { state.mirror = !state.mirror }
            if (frameLabel != null) {
                ToolButton(DotGlyphs.ONION, tr("Onion", "Calque"), state.onion) { state.onion = !state.onion }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Brightness, undo / redo
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LEVELS.forEach { v ->
                val selected = state.level == v
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) P.accent else P.outline, CircleShape)
                        .clickable {
                            state.level = v
                            if (state.tool !in listOf(Tool.PEN, Tool.FILL)) state.tool = Tool.PEN
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(mix(P.ledOff, P.ledOn, 0.25f + 0.75f * v / 255f)),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallIcon(Ic.Undo, tr("Undo", "Annuler"), enabled = state.undoDepth > 0) { state.undo() }
            SmallIcon(Ic.Redo, tr("Redo", "Rétablir"), enabled = state.redoDepth > 0) { state.redo() }
        }

        if (frameLabel != null) {
            Spacer(Modifier.height(14.dp))
            FrameStrip(state, frameLabel)
        }
    }
}

@Composable
private fun FrameStrip(state: PaintState, label: (Int) -> String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.frames.forEachIndexed { i, f ->
                val selected = i == state.index
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (P.isDark) P.surface else P.surfaceHigh)
                            .border(if (selected) 2.dp else 1.dp, if (selected) P.accent else P.outline, RoundedCornerShape(14.dp))
                            .clickable { state.select(i) }
                            .padding(6.dp),
                    ) {
                        MatrixView(f, state.shape, Modifier.fillMaxSize(), gap = 0.12f, marks = true)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(label(i), style = Type.label, color = if (selected) P.text else P.textFaint)
                }
            }
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, P.outline, RoundedCornerShape(14.dp))
                    .clickable { state.addFrame(duplicate = true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Add, contentDescription = tr("Add a frame", "Ajouter une image"), tint = P.text, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIcon(Ic.ChevronLeft, tr("Move left", "Vers la gauche"), enabled = state.index > 0) { state.moveFrame(-1) }
            SmallIcon(Ic.ChevronRight, tr("Move right", "Vers la droite"), enabled = state.index < state.frames.lastIndex) { state.moveFrame(1) }
            SmallIcon(Ic.Copy, tr("Duplicate", "Dupliquer")) { state.addFrame(duplicate = true) }
            SmallIcon(Ic.Add, tr("Blank frame", "Image vide")) { state.addFrame(duplicate = false) }
            Spacer(Modifier.weight(1f))
            SmallIcon(Ic.Delete, tr("Delete this one", "Supprimer celle-ci"), tint = P.accent) { state.deleteFrame() }
        }
    }
}

@Composable
private fun ToolButton(pattern: List<String>, label: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 52.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (selected) P.inverse else Color.Transparent)
                .border(1.dp, if (selected) P.inverse else P.outline, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            DotIcon(pattern, if (selected) P.onInverse else if (accent) P.accent else P.text, Modifier.size(18.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label.uppercase(), style = Type.label.copy(fontSize = Type.label.fontSize * 0.85f), color = if (selected) P.text else P.textFaint, maxLines = 1)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 2.dp).size(width = 1.dp, height = 28.dp).background(P.outline))
}

@Composable
fun SmallIcon(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = P.text, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else P.textFaint.copy(alpha = 0.5f), modifier = Modifier.size(21.dp))
    }
}

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
