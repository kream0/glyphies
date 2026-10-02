package app.glyphies.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.data.GameData
import app.glyphies.data.GameTemplate
import app.glyphies.data.Role
import app.glyphies.data.SpriteData
import app.glyphies.data.description
import app.glyphies.data.label
import app.glyphies.engine.Driver
import app.glyphies.engine.LoopMode
import app.glyphies.engine.Marks
import app.glyphies.engine.Sprite
import app.glyphies.glyph.Frames
import app.glyphies.glyph.GlyphOutput
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.play.Catalog
import app.glyphies.tr
import app.glyphies.trCount
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.components.Block
import app.glyphies.ui.components.Chips
import app.glyphies.ui.components.IconBtn
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.Line
import app.glyphies.ui.components.LivePreview
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.MatrixView
import app.glyphies.ui.components.NothingSwitch
import app.glyphies.ui.components.Note
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.Section
import app.glyphies.ui.components.Segmented
import app.glyphies.ui.components.Stepper
import app.glyphies.ui.editor.PaintState
import app.glyphies.ui.editor.PixelEditor
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type
import kotlinx.coroutines.delay

/** Makes an overlay catch the touches that would otherwise reach the screen underneath. */
@Composable
fun Modifier.blockTouches(): Modifier = clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

/** Saves a change to the creation [id], always on top of its latest version. */
private fun update(id: String, block: (Creation) -> Creation) {
    Graph.creations.get(id)?.let { Graph.creations.save(block(it)) }
}

@Composable
fun EditorScreen(app: AppViewModel, id: String, onClose: () -> Unit) {
    val all by Graph.creations.all.collectAsStateWithLifecycle()
    val creation = all.firstOrNull { it.id == id }
    LaunchedEffect(creation == null) { if (creation == null) onClose() }
    if (creation == null) return
    val sheets = LocalSheets.current
    val context = LocalContext.current
    val playing by Graph.player.state.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()

    val game = creation.game
    val usesCanvas = game == null || game.template.usesLevels
    val paint = remember(id, creation.size) { PaintState(creation.shape, creation.decodedFrames()) }
    var live by remember { mutableStateOf(GlyphOutput.available) }
    var spriteRole by remember { mutableStateOf<Role?>(null) }

    // Save a moment after the last change.
    LaunchedEffect(paint.version) {
        if (paint.version == 0) return@LaunchedEffect
        delay(350)
        update(id) { c ->
            val kind = when {
                c.kind == CreationKind.GAME -> CreationKind.GAME
                paint.frames.size > 1 -> CreationKind.ANIMATION
                else -> CreationKind.DRAWING
            }
            c.withFrames(paint.frames.toList()).copy(kind = kind)
        }
    }

    // What you draw shows on the back of the phone as you draw it.
    LaunchedEffect(paint.version, paint.index, live, playing == null, usesCanvas, spriteRole) {
        if (playing != null || spriteRole != null) return@LaunchedEffect
        if (live && usesCanvas) GlyphOutput.show(context, Marks.strip(paint.current), paint.shape) else GlyphOutput.clear()
    }
    DisposableEffect(Unit) { onDispose { if (!Graph.player.isActive) GlyphOutput.release() } }

    Box(Modifier.fillMaxSize().background(P.background).blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Back, onClose, contentDescription = tr("Back", "Retour"))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { sheets(renameSheet(creation)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(creation.name, style = Type.titleLarge, color = P.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        creation.kindLabel + " · ${creation.size}×${creation.size}" +
                            if (usesCanvas) " · " + trCount(paint.frames.size, if (game != null) "level" else "frame", if (game != null) "levels" else "frames", if (game != null) "niveau" else "image", if (game != null) "niveaux" else "images") else "",
                        style = Type.label,
                        color = P.textDim,
                        maxLines = 1,
                    )
                }
                IconBtn(Ic.More, { sheets(creationSheet(app, creation, onDeleted = onClose)) }, contentDescription = tr("More", "Plus"))
                Spacer(Modifier.width(4.dp))
                PillButton(tr("Play", "Jouer"), { Graph.player.play(id) }, icon = Ic.Play, style = PillStyle.Accent)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 32.dp),
            ) {
                if (game != null) {
                    Text(game.template.description, style = Type.body, color = P.textDim, modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(12.dp))
                }
                if (usesCanvas) {
                    PixelEditor(
                        paint,
                        marks = game?.template == GameTemplate.MAZE,
                        frameLabel = { i -> if (game != null) tr("LEVEL ${i + 1}", "NIVEAU ${i + 1}") else "${i + 1}" },
                    )
                    Spacer(Modifier.height(6.dp))
                    when (game?.template) {
                        GameTemplate.MAZE -> Note(
                            tr(
                                "Lit dots are walls. Place a start (ring), a goal (red dot) and traps (×, back to the start). Each frame is a level.",
                                "Les points allumés sont des murs. Placez un départ (anneau), une arrivée (point rouge) et des pièges (×, retour au départ). Chaque image est un niveau.",
                            )
                        )
                        GameTemplate.BRICKS -> Note(
                            tr(
                                "Lit dots are bricks; keep the bottom rows free for the paddle. Each frame is a level. Empty = the default wall.",
                                "Les points allumés sont des briques ; laissez le bas libre pour la raquette. Chaque image est un niveau. Vide = le mur par défaut.",
                            )
                        )
                        else -> Note(
                            tr(
                                "Tap a lit dot to turn it off. Brightness: the four squares. Mirror draws both halves at once.",
                                "Touchez un point allumé pour l'éteindre. Luminosité : les quatre carrés. Miroir dessine les deux moitiés à la fois.",
                            )
                        )
                    }
                } else {
                    // Games without levels: a live preview of the game as it is set up.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(220.dp)
                                .clip(CircleShape)
                                .background(if (P.isDark) P.surface else P.surfaceHigh)
                                .padding(22.dp),
                        ) {
                            LivePreview(key = creation.updated, modifier = Modifier.fillMaxSize()) {
                                Graph.creations.get(id)?.let { c ->
                                    val g = c.game ?: return@let null
                                    // Previews steer by finger so they move on their own.
                                    Catalog.creation(c.copy(game = g.copy(move = app.glyphies.engine.Move.TOUCH)), settings)
                                }
                            }
                        }
                    }
                }

                if (game != null) {
                    GameSettings(creation, game, onEditSprite = { spriteRole = it })
                } else {
                    AnimationSettings(creation, paint.frames.size)
                }

                if (usesCanvas) {
                    Section("Glyph Matrix") {
                        if (GlyphSupport.isSupported) {
                            Line(
                                tr("Show while drawing", "Afficher en dessinant"),
                                tr("What you draw lights up on the back of the phone.", "Ce que vous dessinez s'allume au dos du téléphone."),
                            ) { NothingSwitch(live, { live = it }) }
                        } else {
                            Note(tr("This phone has no Glyph Matrix: what you draw stays on the screen.", "Ce téléphone n'a pas de Glyph Matrix : vos dessins restent à l'écran."))
                        }
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(tr("Clear", "Effacer"), { paint.clearFrame() }, icon = Ic.Delete)
                            PillButton(tr("Flip", "Retourner"), { paint.flip() })
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = spriteRole != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            var shown by remember { mutableStateOf<Role?>(null) }
            spriteRole?.let { shown = it }
            shown?.let { role -> SpriteEditor(creation, role, onDone = { spriteRole = null }) }
        }
    }
    BackHandler(enabled = spriteRole != null) { spriteRole = null }
}

@Composable
private fun AnimationSettings(c: Creation, frameCount: Int) {
    Section(tr("How it plays", "Comment elle se joue")) {
        val drivers = Driver.entries
        Block(tr("Driven by", "Pilotée par"), c.driver.description) {
            Chips(drivers.map { it.label }, drivers.indexOf(c.driver), { i -> update(c.id) { it.copy(driver = drivers[i]) } })
        }
        if (frameCount > 1 && (c.driver == Driver.TIME || c.driver == Driver.PROXIMITY)) {
            Line(tr("Speed", "Vitesse"), tr("Frames per second", "Images par seconde")) {
                Stepper(c.fps, 1..30) { v -> update(c.id) { it.copy(fps = v) } }
            }
        }
        if (frameCount > 1 && c.driver == Driver.TIME) {
            Block(tr("Loop", "Boucle"), null) {
                Segmented(LoopMode.entries.map { it.label }, c.loop.ordinal, { i -> update(c.id) { it.copy(loop = LoopMode.entries[i]) } })
            }
        }
        if (frameCount == 1 && c.driver != Driver.SAND && c.driver != Driver.TIME) {
            Note(tr("Add frames for the sensor to switch between.", "Ajoutez des images entre lesquelles le capteur passera."))
        }
    }
}

@Composable
private fun GameSettings(c: Creation, g: GameData, onEditSprite: (Role) -> Unit) {
    val t = g.template
    fun set(block: (GameData) -> GameData) = update(c.id) { cur -> cur.copy(game = block(cur.game ?: g)) }

    if (t.roles.isNotEmpty()) {
        Section(tr("Pictures", "Dessins")) {
            t.roles.forEach { role ->
                val sprite = g.sprites[role.key]?.toSprite()
                val shown = sprite?.takeUnless { it.isEmpty } ?: if (sprite != null && role.optional) null else role.default(c.shape)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEditSprite(role) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (P.isDark) P.surface else P.surfaceHigh)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shown != null) {
                            MatrixView(shown.px, MatrixShape.rect(shown.w, shown.h), Modifier.size((8 * maxOf(shown.w, shown.h)).coerceAtMost(40).dp))
                        } else {
                            Text("—", style = Type.title, color = P.textFaint)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(role.label, style = Type.title, color = P.text)
                        Text(
                            when {
                                sprite == null -> tr("Built-in picture", "Dessin par défaut")
                                shown == null -> tr("None", "Aucun")
                                else -> tr("Your drawing · ${shown.w}×${shown.h}", "Votre dessin · ${shown.w}×${shown.h}")
                            } + if (role.optional) tr(" · optional", " · facultatif") else "",
                            style = Type.label,
                            color = P.textDim,
                        )
                    }
                    PillButton(tr("Draw", "Dessiner"), { onEditSprite(role) }, icon = Ic.Edit)
                }
            }
        }
    }

    Section(tr("Controls", "Commandes")) {
        if (t.moves.isNotEmpty()) {
            Block(tr("Move with", "Bouger avec"), g.move.description) {
                Segmented(t.moves.map { it.label }, t.moves.indexOf(g.move).coerceAtLeast(0), { i -> set { it.copy(move = t.moves[i]) } })
            }
        } else if (t == GameTemplate.MAZE) {
            Note(tr("Tilt the phone to roll the ball.", "Inclinez le téléphone pour rouler la bille."))
        }
        if (t.triggers.isNotEmpty()) {
            val title = when (t) {
                GameTemplate.FLY -> tr("Flap with", "Battre des ailes avec")
                GameTemplate.BRICKS -> tr("Launch the ball with", "Lancer la balle avec")
                else -> tr("Fire with", "Tirer avec")
            }
            Block(title, g.trigger.description) {
                Chips(t.triggers.map { it.label }, t.triggers.indexOf(g.trigger).coerceAtLeast(0), { i -> set { it.copy(trigger = t.triggers[i]) } })
            }
        }
        Line(tr("Speed", "Vitesse"), tr("1 calm · 5 frantic", "1 calme · 5 frénétique")) {
            Stepper(g.speed, 1..5) { v -> set { it.copy(speed = v) } }
        }
        if (t != GameTemplate.MAZE && t != GameTemplate.FLY) {
            Line(tr("Lives", "Vies"), null) {
                Stepper(g.lives, 1..5) { v -> set { it.copy(lives = v) } }
            }
        }
    }
}

/** Full-screen editor for one picture of a game (the ship, the obstacle…). */
@Composable
private fun SpriteEditor(c: Creation, role: Role, onDone: () -> Unit) {
    val g = c.game ?: return
    val stored = g.sprites[role.key]?.toSprite()
    val start = stored?.takeUnless { it.isEmpty } ?: role.default(c.shape)
    val state = remember(c.id, role.key) { PaintState(MatrixShape.rect(start.w, start.h), listOf(start.px.copyOf())) }
    val maxW = role.maxW(c.shape)
    val maxH = role.maxH(c.shape)

    fun save(s: Sprite) = update(c.id) { cur ->
        val game = cur.game ?: g
        cur.copy(game = game.copy(sprites = game.sprites + (role.key to SpriteData.of(s))))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.background)
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Close, onDone, contentDescription = tr("Cancel", "Annuler"))
            Text(role.label, style = Type.titleLarge, color = P.text, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
            PillButton(tr("Done", "OK"), {
                save(Sprite(state.shape.width, state.shape.height, state.current.copyOf()))
                onDone()
            }, icon = Ic.Check, style = PillStyle.Accent)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cell = 44
            PixelEditor(state, canvasWidth = (state.shape.width * cell).coerceAtMost(300).dp + 20.dp)
            Spacer(Modifier.height(8.dp))
            Line(tr("Width", "Largeur"), null) {
                Stepper(state.shape.width, 1..maxW) { w -> state.resize(w, state.shape.height) }
            }
            Line(tr("Height", "Hauteur"), null) {
                Stepper(state.shape.height, 1..maxH) { h -> state.resize(state.shape.width, h) }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Built-in", "Par défaut"), {
                    update(c.id) { cur ->
                        val game = cur.game ?: g
                        cur.copy(game = game.copy(sprites = game.sprites - role.key))
                    }
                    onDone()
                }, icon = Ic.Refresh)
                if (role.optional) {
                    PillButton(tr("None", "Aucun"), {
                        save(Sprite(1, 1, intArrayOf(0)))
                        onDone()
                    }, icon = Ic.Delete)
                }
            }
            Note(
                tr(
                    "Dots on the edges that stay dark are trimmed. Brighter dots stand out more on the matrix.",
                    "Les bords restés éteints sont rognés. Les points plus lumineux ressortent mieux sur la matrice.",
                )
            )
        }
    }
}
