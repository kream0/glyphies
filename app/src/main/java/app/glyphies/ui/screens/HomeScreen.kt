package app.glyphies.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.data.AppSettings
import app.glyphies.data.CreationKind
import app.glyphies.data.GameTemplate
import app.glyphies.engine.Driver
import app.glyphies.glyph.GlyphSupport
import app.glyphies.play.BuiltIn
import app.glyphies.play.FaceDown
import app.glyphies.tr
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.HomeFilter
import app.glyphies.ui.components.CountLabel
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.IconBtn
import app.glyphies.ui.components.LivePreview
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
import app.glyphies.ui.components.StatusChip
import app.glyphies.ui.components.Tags
import app.glyphies.ui.duration
import app.glyphies.ui.home.Category
import app.glyphies.ui.home.Entry
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type

/**
 * The library: what you played last (or the featured one) as a big live matrix, then every
 * game and animation — built in and yours — as tiles. Tap to play, long press or ⋯ for more.
 */
@Composable
fun HomeScreen(app: AppViewModel) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val creations by Graph.creations.all.collectAsStateWithLifecycle()
    val best by Graph.scores.best.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val entries = remember(creations, settings.previewSize, settings.hourglassSeconds, settings.autoFire, settings.language) {
        Entry.all(creations, settings)
    }
    val featured = entries.firstOrNull { it.key == settings.lastPlayed }
    val hero = featured ?: entries.first { it.builtIn == BuiltIn.SAND }
    val filter = app.homeFilter
    val shown = entries.filter {
        when (filter) {
            HomeFilter.ALL -> true
            HomeFilter.GAMES -> it.category == Category.GAME
            HomeFilter.ANIMATIONS -> it.category == Category.ANIMATION
            HomeFilter.MINE -> it.mine
        }
    }
    val games = shown.filter { it.category == Category.GAME }
    val animations = shown.filter { it.category == Category.ANIMATION }
    val shape = GlyphSupport.displayShape(settings.previewSize)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        state = app.homeGrid,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        full("header") {
            Column {
                ScreenHeader("GLYPHIES", Modifier.padding(start = 0.dp)) {
                    StatusChip(
                        if (GlyphSupport.isSupported) "${shape.width}×${shape.height}" else tr("PREVIEW", "APERÇU"),
                        on = GlyphSupport.isSupported && settings.glyphOutput,
                    )
                }
                Text(
                    if (GlyphSupport.isSupported) {
                        tr("${GlyphSupport.deviceName} · Glyph Matrix ready", "${GlyphSupport.deviceName} · Glyph Matrix prête").uppercase()
                    } else {
                        tr("No Glyph Matrix here · plays on screen", "Pas de Glyph Matrix ici · se joue à l'écran").uppercase()
                    },
                    style = Type.label,
                    color = P.textFaint,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        full("hero") { Hero(hero, continuing = featured != null, best = best, settings = settings, onMore = { sheets(entrySheet(app, hero, settings)) }) }
        full("filters") {
            Tags(HomeFilter.entries.map { it.label }, filter.ordinal, { app.homeFilter = HomeFilter.entries[it] }, Modifier.padding(top = 8.dp))
        }

        if (games.isNotEmpty()) {
            full("games-label") { CountLabel(tr("Games", "Jeux"), games.size, Modifier.padding(top = 16.dp)) }
            tiles(games, app, settings, best)
        }
        if (animations.isNotEmpty()) {
            full("animations-label") { CountLabel(tr("Animations", "Animations"), animations.size, Modifier.padding(top = 16.dp)) }
            tiles(animations, app, settings, best)
        }
        if (shown.isEmpty()) {
            full("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Nothing here yet", "Rien ici pour l'instant"), style = Type.title, color = P.textDim)
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Make your first one in the editor.", "Créez le premier dans l'éditeur."), style = Type.label, color = P.textFaint)
                }
            }
        }
        if (filter == HomeFilter.ALL || filter == HomeFilter.MINE) {
            item(key = "new") {
                NewTile(
                    tr("Make one", "Créer"),
                    tr("Draw, animate or build a game", "Dessiner, animer ou créer un jeu"),
                    Pictos.DRAW,
                    onClick = { app.startNew() },
                )
            }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.tiles(list: List<Entry>, app: AppViewModel, settings: AppSettings, best: Map<String, Int>) {
    items(list, key = { it.key }) { e ->
        val sheets = LocalSheets.current
        val score = best[e.creation?.id ?: e.builtIn?.id ?: ""]
        val onBack = (settings.toy ?: FaceDown.DEFAULT) == e.key
        MatrixTile(
            category = e.kindLabel,
            title = e.title,
            subtitle = e.subtitle,
            previewKey = "${e.key}:${e.creation?.updated}:${e.shape.width}",
            still = remember(e.key, e.creation?.updated, e.shape.width) { e.still(settings) },
            shape = e.shape,
            animate = settings.animatedTiles,
            status = when {
                onBack -> onBackLabel
                score != null && score > 0 -> tr("BEST $score", "RECORD $score")
                else -> null
            },
            onClick = { e.play() },
            onMore = { sheets(entrySheet(app, e, settings)) },
        ) { e.preview(settings) }
    }
}

/**
 * Not in a card: the one big round matrix on the screen, floating, with the single red button.
 * That's this screen's one break from the grid.
 */
@Composable
private fun Hero(e: Entry, continuing: Boolean, best: Map<String, Int>, settings: AppSettings, onMore: () -> Unit) {
    val score = best[e.creation?.id ?: e.builtIn?.id ?: ""]
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
            if (settings.animatedTiles) {
                LivePreview("hero:${e.key}:${e.creation?.updated}:${e.shape.width}", Modifier.fillMaxWidth(), fps = 20) { e.preview(settings) }
            } else {
                MatrixView(remember(e.key, e.creation?.updated) { e.still(settings) }, e.shape, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(0.5f)) {
            Text(
                if (continuing) tr("CONTINUE", "REPRENDRE") else tr("START HERE", "POUR COMMENCER"),
                style = Type.label,
                color = P.textDim,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                e.title.uppercase(),
                style = Type.display.copy(fontSize = 28.sp, lineHeight = 30.sp),
                color = P.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(e.subtitle.uppercase(), style = Type.label, color = P.textFaint, maxLines = 2)
            if (score != null && score > 0) {
                Spacer(Modifier.height(4.dp))
                Text(tr("BEST $score", "RECORD $score"), style = Type.labelBold, color = P.text)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(tr("Play", "Jouer"), { e.play() }, icon = Ic.Play, style = PillStyle.Accent)
                Spacer(Modifier.width(4.dp))
                IconBtn(Ic.More, onMore, contentDescription = tr("More", "Plus"))
            }
        }
    }
}

/** Long press / ⋯ on a tile. */
fun entrySheet(app: AppViewModel, e: Entry, settings: AppSettings): SheetSpec {
    e.creation?.let { return creationSheet(app, it) }
    val b = e.builtIn ?: return SheetSpec(e.title)
    return SheetSpec(
        title = b.title,
        subtitle = e.subtitle,
        content = { Text(b.blurb, style = Type.body, color = P.textDim) },
        actions = buildList {
            add(SheetAction(tr("Play", "Jouer"), Ic.Play) { Graph.player.play(b) })
            if (FaceDown.canShow(b)) add(faceDownAction(FaceDown.key(b)))
            if (b == BuiltIn.HOURGLASS) {
                add(
                    SheetAction(tr("Duration · ${duration(settings.hourglassSeconds)}", "Durée · ${duration(settings.hourglassSeconds)}"), Ic.Refresh, next = {
                        SheetSpec(
                            title = tr("Hourglass", "Sablier"),
                            subtitle = tr("How long?", "Combien de temps ?"),
                            actions = listOf(30, 60, 180, 300, 600).map { s ->
                                SheetAction(duration(s), if (s == settings.hourglassSeconds) Ic.Check else null) {
                                    Graph.settings.update { it.copy(hourglassSeconds = s) }
                                }
                            },
                        )
                    })
                )
            }
            if (b == BuiltIn.INVADERS) {
                add(
                    SheetAction(tr("Make my own version", "Créer ma version"), Ic.Edit) {
                        create(app, CreationKind.GAME, template = GameTemplate.SHOOTER, name = tr("My invaders", "Mes envahisseurs"))
                    }
                )
            }
            if (b == BuiltIn.SAND) {
                add(
                    SheetAction(tr("Draw something that crumbles", "Dessiner quelque chose qui s'effrite"), Ic.Edit) {
                        create(app, CreationKind.DRAWING, name = tr("Sand drawing", "Dessin de sable"), driver = Driver.SAND)
                    }
                )
            }
        },
    )
}
