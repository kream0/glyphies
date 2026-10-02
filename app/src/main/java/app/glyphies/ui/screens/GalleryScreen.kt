package app.glyphies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.data.CreationStore
import app.glyphies.data.GameData
import app.glyphies.data.GameTemplate
import app.glyphies.data.Seeds
import app.glyphies.engine.AnimationPlayer
import app.glyphies.engine.Driver
import app.glyphies.glyph.Frames
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.components.CountLabel
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.IconBtn
import app.glyphies.ui.components.LocalSheetClose
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.MatrixTile
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.components.NewTile
import app.glyphies.ui.components.Pictos
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.ScreenHeader
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.components.Thumbs
import app.glyphies.ui.home.Entry
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/**
 * The editor tab when nothing is open: start something new (drawing, animation, text, game),
 * or pick one of your creations to carry on with.
 */
@Composable
fun GalleryScreen(app: AppViewModel) {
    val creations by Graph.creations.all.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        state = app.galleryGrid,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader(tr("EDITOR", "ÉDITEUR"), Modifier.padding(start = 0.dp))
                Text(
                    tr(
                        "Draw on the matrix, animate it, make it react to the phone, or build a game with your own pictures.",
                        "Dessinez sur la matrice, animez-la, faites-la réagir au téléphone, ou créez un jeu avec vos dessins.",
                    ),
                    style = Type.body,
                    color = P.textDim,
                )
                Spacer(Modifier.height(20.dp))
                CountLabel(tr("New", "Nouveau"), null)
            }
        }
        item(key = "new-drawing") {
            NewTile(tr("Drawing", "Dessin"), tr("Dot by dot, shown live on the back", "Point par point, affiché en direct au dos"), Pictos.DRAW, { create(app, CreationKind.DRAWING) })
        }
        item(key = "new-animation") {
            NewTile(tr("Animation", "Animation"), tr("Frames, driven by time, voice, tilt…", "Des images, pilotées par le temps, la voix, l'inclinaison…"), Pictos.FRAMES, { create(app, CreationKind.ANIMATION) })
        }
        item(key = "new-text") {
            NewTile(tr("Scrolling text", "Texte défilant"), tr("Type it, it scrolls across", "Tapez-le, il défile"), Pictos.TEXT, { sheets(textSheet(app)) })
        }
        item(key = "new-game") {
            NewTile(tr("Game", "Jeu"), tr("Six templates, your pictures and controls", "Six modèles, vos dessins et commandes"), Pictos.GAME, { sheets(templateSheet(app)) })
        }
        item(key = "mine-label", span = { GridItemSpan(maxLineSpan) }) {
            CountLabel(tr("My creations", "Mes créations"), creations.size, Modifier.padding(top = 20.dp))
        }
        items(creations, key = { it.id }) { c ->
            val e = Entry.of(c)
            MatrixTile(
                category = e.kindLabel,
                title = c.name,
                subtitle = e.subtitle,
                previewKey = "gallery:${c.id}:${c.updated}",
                still = Thumbs.of(c),
                shape = c.shape,
                animate = false,
                status = if (settings.toyCreation == c.id) "TOY" else null,
                onClick = { app.openEditor(c.id) },
                onMore = { sheets(creationSheet(app, c)) },
            ) { null }
        }
        if (creations.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(tr("Nothing yet. Start with a drawing.", "Rien pour l'instant. Commencez par un dessin."), style = Type.label, color = P.textFaint)
            }
        }
        item(key = "samples", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                PillButton(tr("Bring back the samples", "Remettre les exemples"), { Graph.creations.restoreSamples() }, icon = Ic.Refresh)
            }
        }
    }
}

/** The size new creations are made for: this phone's matrix, or the preview size. */
fun newCreationSize(): Int = GlyphSupport.displayShape(Graph.settings.current.previewSize).width

fun create(
    app: AppViewModel,
    kind: CreationKind,
    template: GameTemplate? = null,
    frames: List<IntArray>? = null,
    name: String? = null,
    fps: Int = 6,
    driver: Driver = Driver.TIME,
) {
    val size = newCreationSize()
    val shape = MatrixShape.forSize(size)
    val count = Graph.creations.all.value.count { it.kind == kind } + 1
    val c = Creation(
        id = CreationStore.newId(),
        name = name ?: when (kind) {
            CreationKind.DRAWING -> tr("Drawing $count", "Dessin $count")
            CreationKind.ANIMATION -> tr("Animation $count", "Animation $count")
            CreationKind.GAME -> template?.label?.lowercase()?.replaceFirstChar { it.uppercase() } ?: tr("Game", "Jeu")
        },
        kind = kind,
        size = size,
        frames = (frames ?: templateFrames(template, shape)).map { Frames.encode(it) },
        fps = fps,
        driver = driver,
        game = template?.let { GameData(it) },
    )
    Graph.creations.save(c)
    app.openEditor(c.id)
}

/** A maze starts with a sample level so there's something to play straight away. */
private fun templateFrames(t: GameTemplate?, shape: MatrixShape): List<IntArray> = when (t) {
    GameTemplate.MAZE -> listOf(shape.mask(Frames.fit(Seeds.demoMaze(), 13, 13, shape.width, shape.height)))
    else -> listOf(shape.blank())
}

private fun templateSheet(app: AppViewModel): SheetSpec = SheetSpec(
    title = tr("New game", "Nouveau jeu"),
    subtitle = tr("Pick a template · you'll draw the pictures", "Choisissez un modèle · vous dessinerez les images"),
    content = { TemplatePicker(app) },
)

/** Each template as a tile playing a demo of itself. */
@Composable
private fun TemplatePicker(app: AppViewModel) {
    val close = LocalSheetClose.current
    val settings = Graph.settings.current
    val size = newCreationSize()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTemplate.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t ->
                    val demo = remember(t, size) {
                        val shape = MatrixShape.forSize(size)
                        Creation(
                            id = "template-${t.name}",
                            name = t.label,
                            kind = CreationKind.GAME,
                            size = size,
                            frames = templateFrames(t, shape).map { Frames.encode(it) },
                            game = GameData(t),
                        )
                    }
                    MatrixTile(
                        category = tr("Template", "Modèle"),
                        title = t.label.lowercase().replaceFirstChar { it.uppercase() },
                        subtitle = Entry.subtitleOf(demo).substringAfter(" · "),
                        previewKey = "template:${t.name}:$size",
                        still = demo.frame(0),
                        shape = demo.shape,
                        animate = true,
                        onClick = {
                            close()
                            create(app, CreationKind.GAME, template = t)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Entry.previewOf(demo, settings) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun textSheet(app: AppViewModel): SheetSpec = SheetSpec(
    title = tr("Scrolling text", "Texte défilant"),
    subtitle = tr("Letters, digits and punctuation", "Lettres, chiffres et ponctuation"),
    content = { TextEntry(app) },
)

@Composable
private fun TextEntry(app: AppViewModel) {
    val close = LocalSheetClose.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val done = {
        val t = text.trim()
        if (t.isNotEmpty()) {
            val shape = MatrixShape.forSize(newCreationSize())
            close()
            create(app, CreationKind.ANIMATION, frames = AnimationPlayer.scrollingText(shape, t), name = t.take(24), fps = 14)
        }
    }
    Column {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            singleLine = true,
            textStyle = Type.input.copy(color = P.text),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(P.surfaceHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Create", "Créer"), { done() }, style = PillStyle.Accent, enabled = text.isNotBlank())
    }
}

/** ⋯ on a creation. */
fun creationSheet(app: AppViewModel, c: Creation, onDeleted: () -> Unit = {}): SheetSpec {
    val isToy = Graph.settings.current.toyCreation == c.id
    return SheetSpec(
        title = c.name,
        subtitle = c.kindLabel,
        actions = buildList {
            add(SheetAction(tr("Play", "Jouer"), Ic.Play) { Graph.player.play(c.id) })
            add(SheetAction(tr("Edit", "Modifier"), Ic.Edit) { app.openEditor(c.id) })
            add(SheetAction(tr("Rename", "Renommer"), Ic.Text, next = { renameSheet(c) }))
            add(SheetAction(tr("Duplicate", "Dupliquer"), Ic.Copy) { Graph.creations.duplicate(c, c.name + tr(" (copy)", " (copie)")) })
            if (c.kind != CreationKind.GAME) {
                add(
                    SheetAction(
                        if (isToy) tr("Stop using as Glyph Toy", "Ne plus utiliser comme Glyph Toy") else tr("Use as Glyph Toy", "Utiliser comme Glyph Toy"),
                        Ic.Glyph,
                    ) {
                        Graph.settings.update { it.copy(toyCreation = if (isToy) null else c.id) }
                        Graph.toast(
                            if (isToy) tr("The Glyph Toy shows the invader again", "Le Glyph Toy affiche à nouveau l'envahisseur")
                            else tr("The Glyphies Glyph Toy now shows \"${c.name}\"", "Le Glyph Toy Glyphies affiche maintenant « ${c.name} »")
                        )
                    }
                )
            }
            add(
                SheetAction(tr("Delete", "Supprimer"), Ic.Delete, destructive = true, next = {
                    SheetSpec(
                        title = tr("Delete \"${c.name}\"?", "Supprimer « ${c.name} » ?"),
                        actions = listOf(
                            SheetAction(tr("Delete", "Supprimer"), Ic.Delete, destructive = true) {
                                Graph.creations.delete(c.id)
                                Graph.scores.forget(c.id)
                                if (Graph.settings.current.toyCreation == c.id) Graph.settings.update { it.copy(toyCreation = null) }
                                onDeleted()
                            },
                            SheetAction(tr("Keep", "Garder"), Ic.Close) {},
                        ),
                    )
                })
            )
        },
    )
}

fun renameSheet(c: Creation): SheetSpec = SheetSpec(
    title = tr("Rename", "Renommer"),
    content = { RenameField(c) },
)

@Composable
private fun RenameField(c: Creation) {
    val close = LocalSheetClose.current
    var text by remember { mutableStateOf(c.name) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val done = {
        val t = text.trim()
        if (t.isNotEmpty()) {
            Graph.creations.get(c.id)?.let { Graph.creations.save(it.copy(name = t.take(40))) }
            close()
        }
    }
    Column {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            textStyle = Type.input.copy(color = P.text),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(P.surfaceHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Save", "Enregistrer"), { done() }, style = PillStyle.Accent, enabled = text.isNotBlank())
    }
}
