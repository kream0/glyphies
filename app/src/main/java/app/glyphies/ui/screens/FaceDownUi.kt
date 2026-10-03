package app.glyphies.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.glyph.GlyphSupport
import app.glyphies.play.FaceDown
import app.glyphies.tr
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/** Status shown on the tile of what's on the back. */
val onBackLabel: String get() = tr("ON BACK", "AU DOS")

/**
 * "Show face down" for [key]: puts it on the back (in place of Nothing's clock) and follows up
 * with where to switch the Glyphies toy on, once, in the system settings.
 */
fun faceDownAction(key: String): SheetAction {
    val on = FaceDown.current == key
    return SheetAction(
        if (on) tr("Shown face down", "Affiché face cachée") else tr("Show face down", "Afficher face cachée"),
        if (on) Ic.Check else Ic.Glyph,
        next = {
            FaceDown.set(key)
            faceDownGuide(FaceDown.name(key))
        },
    )
}

fun faceDownGuide(name: String): SheetSpec = SheetSpec(
    title = tr("\"$name\" is on the back", "« $name » est au dos"),
    subtitle = tr("When the phone lies face down", "Quand le téléphone est retourné"),
    content = {
        Text(
            if (GlyphSupport.isSupported) {
                FaceDown.howTo
            } else {
                tr(
                    "Saved. It shows on the back of a Nothing Phone (4a) Pro or Phone (3); this phone has no Glyph Matrix.",
                    "C'est enregistré. Ça s'affiche au dos d'un Nothing Phone (4a) Pro ou Phone (3) ; ce téléphone n'a pas de Glyph Matrix.",
                )
            },
            style = Type.body,
            color = P.textDim,
        )
    },
    actions = buildList {
        if (GlyphSupport.isSupported) {
            add(
                SheetAction(tr("Open Glyph settings", "Ouvrir les réglages Glyph"), Ic.Glyph) {
                    val ctx = Graph.app
                    if (!GlyphSupport.openToysManager(ctx)) {
                        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            )
        }
        add(SheetAction("OK", Ic.Check) {})
    },
)

/** Picks what's on the back, from Settings. */
fun faceDownPicker(): SheetSpec = SheetSpec(
    title = tr("Face down", "Face cachée"),
    subtitle = tr("What the back shows with the app closed", "Ce que le dos affiche, app fermée"),
    actions = FaceDown.choices().map { key ->
        SheetAction(
            FaceDown.name(key),
            if (FaceDown.current == key) Ic.Check else null,
            next = {
                FaceDown.set(key)
                faceDownGuide(FaceDown.name(key))
            },
        )
    },
)

/**
 * The "Show face down" button of a detail screen (the player, the editor): puts [key] on the
 * back, then says where to switch the Glyphies toy on.
 */
@Composable
fun FaceDownButton(key: String, modifier: Modifier = Modifier) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val on = (settings.toy ?: FaceDown.DEFAULT) == key
    PillButton(
        if (on) tr("On the back, face down", "Au dos, face cachée") else tr("Show face down", "Afficher face cachée"),
        {
            FaceDown.set(key)
            sheets(faceDownGuide(FaceDown.name(key)))
        },
        modifier,
        icon = if (on) Ic.Check else Ic.Glyph,
        style = if (on) PillStyle.Outline else PillStyle.Filled,
    )
}
