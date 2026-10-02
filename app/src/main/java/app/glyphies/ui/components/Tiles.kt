package app.glyphies.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.glyphies.engine.Playable
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/** Cards: flat, 16dp corners, a hairline border (Nothing: no shadows, no gradients). */
val TileShape = RoundedCornerShape(16.dp)

/**
 * A game or animation as a tile: category label (+ a status) on top, the matrix itself as the
 * picture — playing live when [animate] — then the title and what it's played with.
 * Tap plays; long press (or ⋯) opens its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatrixTile(
    category: String,
    title: String,
    subtitle: String,
    previewKey: Any,
    still: IntArray,
    shape: MatrixShape,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    onMore: (() -> Unit)? = null,
    preview: () -> Playable?,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .clip(TileShape)
            .background(P.surface)
            .border(1.dp, P.outline, TileShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMore?.let { more ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        more()
                    }
                },
                onLongClickLabel = tr("Actions", "Actions"),
            )
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(category.uppercase(), style = Type.label, color = P.textDim, maxLines = 1)
            if (status != null) {
                Spacer(Modifier.width(6.dp))
                Text(status, style = Type.labelBold, color = P.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            if (onMore != null) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.More, contentDescription = tr("Actions for $title", "Actions pour $title"), tint = P.textDim, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(end = 10.dp, top = 2.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            val m = Modifier.fillMaxWidth(0.84f)
            if (animate) {
                LivePreview(previewKey, m, fps = 15, factory = preview)
            } else {
                MatrixView(still, shape, m)
            }
        }
        Text(title, style = Type.title, color = P.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp))
        Spacer(Modifier.height(2.dp))
        Text(subtitle.uppercase(), style = Type.label, color = P.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp))
    }
}

/** A dashed tile that makes something new: a dot pictogram, a title, one line of help. */
@Composable
fun NewTile(title: String, description: String, pattern: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = P.outline
    Column(
        modifier
            .clip(TileShape)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        DotIcon(pattern, P.text, Modifier.size(28.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, style = Type.title, color = P.text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(description, style = Type.label, color = P.textDim, maxLines = 3)
    }
}

/** Filter tags (Nothing chips: outlined, mono caps; the active one in full contrast). */
@Composable
fun Tags(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (on) P.text else P.outline, CircleShape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.labelBold, color = if (on) P.text else P.textFaint, maxLines = 1)
            }
        }
    }
}

/** Section label with a count on the right: "GAMES ······· 4". */
@Composable
fun CountLabel(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title)
        Spacer(Modifier.weight(1f))
        if (count != null) Text(count.toString().padStart(2, '0'), style = Type.label, color = P.textFaint)
    }
}

/** Dot pictograms for the new-creation tiles and the navigation. */
object Pictos {
    val DRAW = listOf("....X", "...X.", "..X..", ".X...", "X....")
    val FRAMES = listOf("XX.XX", "XX.XX", ".....", "XX.XX", "XX.XX")
    val TEXT = listOf("XXXXX", "..X..", "..X..", "..X..", "..X..")
    val GAME = listOf(".X.X.", "XXXXX", "X.X.X", "XXXXX", ".X.X.")
    val HOME = listOf("XX.XX", "XX.XX", ".....", "XX.XX", "XX.XX")
    val EDIT = listOf("....X", "...XX", "..XX.", ".XX..", "X....")
    val SETTINGS = listOf("X.X.X", ".....", "X.X.X", ".....", "X.X.X")
}
