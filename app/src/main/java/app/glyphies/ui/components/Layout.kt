package app.glyphies.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/** A titled group of rows (red dot + mono caps label). */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(18.dp))
    SectionLabel(title, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    Column(Modifier.fillMaxWidth(), content = content)
}

/** Title, a full-width control, then a description. */
@Composable
fun Block(title: String, description: String?, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = Type.title, color = P.text)
        Spacer(Modifier.height(8.dp))
        control()
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(description, style = Type.label, color = P.textDim)
        }
    }
}

/** Title and description on the left, a small control on the right. */
@Composable
fun Line(title: String, description: String?, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.title, color = P.text)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = Type.label, color = P.textDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

/** A paragraph of dim text in the page's margins. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.label, color = P.textDim, modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}
