package app.glyphies.ui.screens

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.engine.DotEmoji
import app.glyphies.glyph.EmojiRaster
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.LocalSheetClose
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/**
 * The emojis drawn for the matrix, in a row you can scroll; the picked one has a red ring.
 * The last button types any other emoji (drawn from the phone's emoji font).
 */
@Composable
fun EmojiPicker(modifier: Modifier = Modifier) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val current = DotEmoji.find(settings.emoji)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        val i = DotEmoji.all.indexOf(current)
        if (i > 2) scroll.scrollTo(with(density) { ((i - 2) * 54).dp.roundToPx() })
    }
    Row(
        modifier.horizontalScroll(scroll).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotEmoji.all.forEach { e ->
            val on = e == current
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(if (on) 2.dp else 1.dp, if (on) P.accent else P.outline, CircleShape)
                    .clickable { Graph.settings.update { it.copy(emoji = e.key) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(e.key, style = TextStyle(fontSize = 24.sp))
            }
        }
        val custom = current == null
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(if (custom) 2.dp else 1.dp, if (custom) P.accent else P.outline, CircleShape)
                .clickable { sheets(customEmojiSheet()) },
            contentAlignment = Alignment.Center,
        ) {
            if (custom) {
                Text(settings.emoji, style = TextStyle(fontSize = 24.sp))
            } else {
                Icon(Ic.Add, contentDescription = tr("Another emoji", "Un autre emoji"), tint = P.text, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun customEmojiSheet(): SheetSpec = SheetSpec(
    title = tr("Another emoji", "Un autre emoji"),
    subtitle = tr("Type it with the emoji keyboard", "Tapez-le avec le clavier emoji"),
    content = { CustomEmoji() },
)

@Composable
private fun CustomEmoji() {
    val close = LocalSheetClose.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun done() {
        val e = firstCharacter(text)
        val drawable = e.isNotEmpty() && (DotEmoji.find(e) != null || EmojiRaster.render(e, MatrixShape.PRO_4A) != null)
        if (drawable) {
            Graph.settings.update { it.copy(emoji = e) }
            close()
        } else {
            error = true
        }
    }
    Column {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.take(16)
                error = false
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 28.sp, color = P.text),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(P.surfaceHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focus),
        )
        if (error) {
            Spacer(Modifier.height(6.dp))
            Text(tr("[That one can't be drawn in dots]", "[Celui-là ne peut pas être dessiné en points]"), style = Type.label, color = P.accent)
        }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Show it", "L'afficher"), { done() }, style = PillStyle.Accent, enabled = text.isNotBlank())
    }
}

/** The first character as the user sees it (an emoji with its skin tone or ZWJ parts). */
private fun firstCharacter(s: String): String {
    val t = s.trim()
    if (t.isEmpty()) return ""
    val it = BreakIterator.getCharacterInstance()
    it.setText(t)
    val end = it.next()
    return if (end > 0) t.substring(0, end) else t
}
