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
import app.glyphies.engine.AnimationPlayer
import app.glyphies.engine.Driver
import app.glyphies.glyph.Frames
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.components.IconBtn
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.LocalSheetClose
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.ScreenHeader
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.components.Thumbs
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

@Composable
fun CreateScreen(app: AppViewModel) {
    val creations by Graph.creations.all.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val newSheet = { sheets(newCreationSheet(app)) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                ScreenHeader(tr("CREATE", "CRÉER"), Modifier.padding(start = 0.dp)) {
                    IconBtn(Ic.Add, newSheet, bordered = true, contentDescription = tr("New", "Nouveau"))
                }
                Text(
                    tr(
                        "Draw on the matrix, animate it, make it react to your voice or the tilt, or build a game with your own pictures.",
                        "Dessinez sur la matrice, animez-la, faites-la réagir à votre voix ou à l'inclinaison, ou créez un jeu avec vos propres dessins.",
                    ),
                    style = Type.label,
                    color = P.textDim,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    PillButton(tr("Drawing", "Dessin"), { create(app, CreationKind.DRAWING) }, icon = Ic.Edit, style = PillStyle.Filled)
                    PillButton(tr("Game", "Jeu"), { sheets(templateSheet(app)) }, icon = Ic.Play)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        items(creations, key = { it.id }) { c ->
            CreationCard(c, onOpen = { app.edit(c.id) }, onMore = { sheets(creationSheet(app, c)) })
        }
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                PillButton(tr("Bring back the samples", "Remettre les exemples"), { Graph.creations.restoreSamples() }, icon = Ic.Refresh)
            }
        }
    }
}

@Composable
private fun CreationCard(c: Creation, onOpen: () -> Unit, onMore: () -> Unit) {
    val toy by Graph.settings.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(P.surface)
            .border(1.dp, P.outline, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (P.isDark) P.background else P.surfaceHigh)
                .padding(10.dp),
        ) {
            MatrixView(Thumbs.of(c), c.shape, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = Type.title, color = P.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.kindLabel + if (toy.toyCreation == c.id) " · TOY" else "",
                    style = Type.label,
                    color = if (toy.toyCreation == c.id) P.accent else P.textDim,
                    maxLines = 1,
                )
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(Ic.More, contentDescription = tr("More", "Plus"), tint = P.textDim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The size new creations are made for: this phone's matrix, or the preview size. */
fun newCreationSize(): Int = GlyphSupport.displayShape(Graph.settings.current.previewSize).width

fun create(app: AppViewModel, kind: CreationKind, template: GameTemplate? = null, frames: List<IntArray>? = null, name: String? = null, fps: Int = 6) {
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
        frames = (frames ?: listOf(shape.blank())).map { Frames.encode(it) },
        fps = fps,
        driver = Driver.TIME,
        game = template?.let { GameData(it) },
    )
    Graph.creations.save(c)
    app.edit(c.id)
}

private fun newCreationSheet(app: AppViewModel): SheetSpec = SheetSpec(
    title = tr("New", "Nouveau"),
    actions = listOf(
        SheetAction(tr("Drawing", "Dessin"), Ic.Edit) { create(app, CreationKind.DRAWING) },
        SheetAction(tr("Animation", "Animation"), Ic.Copy) { create(app, CreationKind.ANIMATION) },
        SheetAction(tr("Scrolling text", "Texte défilant"), Ic.Text, next = { textSheet(app) }),
        SheetAction(tr("Game", "Jeu"), Ic.Play, next = { templateSheet(app) }),
    ),
)

private fun templateSheet(app: AppViewModel): SheetSpec = SheetSpec(
    title = tr("New game", "Nouveau jeu"),
    subtitle = tr("Pick a kind of game", "Choisissez un type de jeu"),
    content = { TemplatePicker(app) },
)

@Composable
private fun TemplatePicker(app: AppViewModel) {
    val close = LocalSheetClose.current
    Column {
        GameTemplate.entries.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        close()
                        create(app, CreationKind.GAME, template = t)
                    }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(P.accent))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.label, style = Type.labelBold.copy(fontSize = Type.label.fontSize * 1.1f), color = P.text)
                    Spacer(Modifier.height(2.dp))
                    Text(t.description, style = Type.body, color = P.textDim)
                }
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
            add(SheetAction(tr("Edit", "Modifier"), Ic.Edit) { app.edit(c.id) })
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
