package app.glyphies.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.Graph
import app.glyphies.tr
import app.glyphies.ui.components.Ic
import app.glyphies.ui.components.LocalSheets
import app.glyphies.ui.components.SheetAction
import app.glyphies.ui.components.SheetHost
import app.glyphies.ui.components.SheetSpec
import app.glyphies.ui.screens.EditorScreen
import app.glyphies.ui.screens.GalleryScreen
import app.glyphies.ui.screens.HomeScreen
import app.glyphies.ui.screens.PlayerScreen
import app.glyphies.ui.screens.SettingsScreen
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type
import app.glyphies.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppRoot(app: AppViewModel) {
    var sheet by remember { mutableStateOf<SheetSpec?>(null) }
    val openSheet: (SheetSpec) -> Unit = remember { { sheet = it } }
    val playing by Graph.player.state.collectAsStateWithLifecycle()

    // A new build finished downloading in the background: offer to install it (once per build).
    val update by Graph.updater.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    LaunchedEffect(update) {
        val ready = update as? UpdateState.Ready ?: return@LaunchedEffect
        if (Graph.updater.dismissedVersion == ready.remote.versionCode || activity == null) return@LaunchedEffect
        Graph.updater.dismissedVersion = ready.remote.versionCode
        sheet = SheetSpec(
            title = tr("Update ready · v${ready.remote.versionName}", "Mise à jour prête · v${ready.remote.versionName}"),
            subtitle = ready.remote.headline ?: tr("A new Glyphies release is downloaded", "Une nouvelle version de Glyphies est téléchargée"),
            actions = listOf(
                SheetAction(tr("Install now", "Installer maintenant"), Ic.Check) { Graph.updater.install(activity) },
                SheetAction(tr("Later", "Plus tard"), Ic.Close) {},
            ),
        )
    }

    // Registered first so the screens' own back handlers (sheets, the sprite editor, the
    // player) come before it: back leaves an open creation for the gallery, then goes home.
    BackHandler(enabled = playing == null && sheet == null && app.tab != Tab.HOME) {
        if (app.tab == Tab.EDITOR && app.editing != null) app.closeEditor() else app.selectTab(Tab.HOME)
    }

    CompositionLocalProvider(LocalSheets provides openSheet) {
        Box(Modifier.fillMaxSize().background(P.background)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    // Tabs swap with a quick fade (Nothing: elements fade, they don't slide).
                    Crossfade(targetState = app.tab, animationSpec = tween(180), label = "tab") { tab ->
                        when (tab) {
                            Tab.HOME -> HomeScreen(app)
                            Tab.EDITOR -> EditorTab(app)
                            Tab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
                BottomNav(tab = app.tab, onTab = app::selectTab)
            }

            AnimatedVisibility(
                visible = playing != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                PlayerScreen(onClose = { Graph.player.stop() })
            }

            ToastHost(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))
            SheetHost(sheet, onShow = { sheet = it }) { sheet = null }
        }
    }
}

/** The editor tab: the creation you're working on, or the gallery to start or pick one. */
@Composable
private fun EditorTab(app: AppViewModel) {
    val all by Graph.creations.all.collectAsStateWithLifecycle()
    val open = app.editing?.takeIf { id -> all.any { it.id == id } }
    Crossfade(targetState = open, animationSpec = tween(180), label = "editor") { id ->
        if (id == null) GalleryScreen(app) else EditorScreen(app, id, onClose = { app.closeEditor() })
    }
}

@Composable
private fun BottomNav(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(P.background)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (selected) P.accent else Color.Transparent),
                )
                Spacer(Modifier.height(5.dp))
                Text(t.label, style = Type.labelBold, color = if (selected) P.text else P.textFaint)
            }
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Graph.messages.collectLatest {
            last = it
            message = it
            delay(2600)
            message = null
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(CircleShape)
                .background(if (P.isDark) Color(0xFF1E1E1E) else Color(0xFF111111))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(P.accent))
            Spacer(Modifier.width(10.dp))
            Text(last, style = Type.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
