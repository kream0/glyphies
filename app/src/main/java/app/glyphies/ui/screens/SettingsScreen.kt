package app.glyphies.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.BuildConfig
import app.glyphies.Graph
import app.glyphies.data.Facing
import app.glyphies.data.Language
import app.glyphies.data.Sensitivity
import app.glyphies.data.ThemeMode
import app.glyphies.glyph.GlyphLink
import app.glyphies.glyph.GlyphStatus
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.GlyphTest
import app.glyphies.play.FaceDown
import app.glyphies.ui.components.LocalSheets
import app.glyphies.tr
import app.glyphies.ui.components.Block
import app.glyphies.ui.components.Line
import app.glyphies.ui.components.LivePreview
import app.glyphies.ui.components.NothingSwitch
import app.glyphies.ui.components.Note
import app.glyphies.ui.components.PillButton
import app.glyphies.ui.components.PillStyle
import app.glyphies.ui.components.ScreenHeader
import app.glyphies.ui.components.Section
import app.glyphies.ui.components.Segmented
import app.glyphies.ui.theme.P
import app.glyphies.ui.theme.Type
import app.glyphies.update.UpdateState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen() {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var micAllowed by remember { mutableStateOf(Graph.sensors.mic.permitted) }
    val micAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAllowed = it }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(tr("SETTINGS", "RÉGLAGES"))

        Section("Glyph Matrix") { GlyphSection(settings.glyphOutput, settings.brightness, settings.previewSize) }

        Section(tr("Controls", "Commandes")) {
            Block(tr("You look at", "Vous regardez"), settings.facing.description) {
                Segmented(Facing.entries.map { it.label }, settings.facing.ordinal, { i -> Graph.settings.update { it.copy(facing = Facing.entries[i]) } })
            }
            Block(tr("Tilt sensitivity", "Sensibilité à l'inclinaison"), tr("How far you tilt to reach an edge in the games.", "Jusqu'où incliner pour atteindre un bord dans les jeux.")) {
                Segmented(Sensitivity.entries.map { it.label }, settings.tilt.ordinal, { i -> Graph.settings.update { it.copy(tilt = Sensitivity.entries[i]) } })
            }
            Line(
                tr("Invaders fire on their own", "Tir automatique des Envahisseurs"),
                tr("Off: tap the screen or press a volume key to fire", "Désactivé : touchez l'écran ou une touche de volume pour tirer"),
            ) { NothingSwitch(settings.autoFire, { on -> Graph.settings.update { it.copy(autoFire = on) } }) }
            Line(
                tr("Volume keys as buttons", "Touches de volume comme boutons"),
                tr("While something plays, they fire, flap or add sand instead of changing the volume", "Pendant le jeu, elles tirent, font voler ou ajoutent du sable au lieu de changer le volume"),
            ) { NothingSwitch(settings.volumeKeys, { on -> Graph.settings.update { it.copy(volumeKeys = on) } }) }
            Line(
                tr("Vibrations", "Vibrations"),
                tr("Feel the hits, the catches and the end of the hourglass", "Ressentez les impacts, les prises et la fin du sablier"),
            ) { NothingSwitch(settings.haptics, { on -> Graph.settings.update { it.copy(haptics = on) } }) }
        }

        Section(tr("Microphone", "Micro")) {
            Block(tr("Voice sensitivity", "Sensibilité de la voix"), tr("Higher picks up quieter sounds (for voice games and animations).", "Plus haut capte des sons plus faibles (jeux et animations à la voix).")) {
                Segmented(Sensitivity.entries.map { it.label }, settings.mic.ordinal, { i -> Graph.settings.update { it.copy(mic = Sensitivity.entries[i]) } })
            }
            Line(
                tr("Microphone access", "Accès au micro"),
                if (micAllowed) {
                    tr("Allowed. Only the loudness is measured; nothing is recorded or sent.", "Autorisé. Seul le volume sonore est mesuré ; rien n'est enregistré ni envoyé.")
                } else {
                    tr("Needed only for what reacts to your voice or claps.", "Nécessaire seulement pour ce qui réagit à la voix ou aux claps.")
                },
            ) {
                if (!micAllowed) PillButton(tr("Allow", "Autoriser"), { micAsk.launch(Manifest.permission.RECORD_AUDIO) })
            }
        }

        Section(tr("Appearance", "Apparence")) {
            Block(tr("Theme", "Thème"), settings.theme.description) {
                Segmented(ThemeMode.entries.map { it.label }, settings.theme.ordinal, { i -> Graph.settings.update { it.copy(theme = ThemeMode.entries[i]) } })
            }
            Block(tr("Language", "Langue"), tr("The app's menus and messages.", "Les menus et les messages de l'app.")) {
                Segmented(Language.entries.map { it.label }, settings.language.ordinal, { i -> Graph.settings.update { it.copy(language = Language.entries[i]) } })
            }
            Line(
                tr("Live tiles", "Tuiles animées"),
                tr("The home screen's tiles play a preview of each game and animation. Off: still pictures, saves battery", "Les tuiles de l'accueil jouent un aperçu de chaque jeu et animation. Désactivé : images fixes, économise la batterie"),
            ) { NothingSwitch(settings.animatedTiles, { on -> Graph.settings.update { it.copy(animatedTiles = on) } }) }
        }

        Section(tr("Updates", "Mises à jour")) { UpdatesSection(settings.autoUpdate) }

        Section(tr("About", "À propos")) {
            Text(
                tr(
                    "Glyphies ${BuildConfig.VERSION_NAME}\nSand physics after Adafruit's PixelDust · Fonts: Doto, Space Grotesk, Space Mono (OFL)\n" +
                        "Glyph Matrix SDK © Nothing. Glyphies isn't affiliated with Nothing.",
                    "Glyphies ${BuildConfig.VERSION_NAME}\nPhysique du sable d'après PixelDust d'Adafruit · Polices : Doto, Space Grotesk, Space Mono (OFL)\n" +
                        "Glyph Matrix SDK © Nothing. Glyphies n'est pas affilié à Nothing.",
                ),
                style = Type.label,
                color = P.textFaint,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun GlyphSection(output: Boolean, brightness: Int, previewSize: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheets = LocalSheets.current
    val shape = GlyphSupport.displayShape(previewSize)
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val onBack = settings.toy ?: FaceDown.DEFAULT
    // What the back shows with the app closed, live.
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        LivePreview(key = "facedown:$onBack:${shape.width}", modifier = Modifier.size(150.dp)) { FaceDown.playable(onBack, shape) }
    }
    Line(
        tr("Face down", "Face cachée"),
        tr(
            "${FaceDown.name(onBack)} · shown on the back with the app closed, instead of Nothing's clock",
            "${FaceDown.name(onBack)} · affiché au dos app fermée, à la place de l'horloge de Nothing",
        ),
    ) { PillButton(tr("Change", "Changer"), { sheets(faceDownPicker()) }) }
    if (!GlyphSupport.isSupported) {
        Note(
            tr(
                "Preview only: the Glyph Matrix is on the Nothing Phone (4a) Pro and Phone (3). This phone: ${Build.MANUFACTURER} ${Build.MODEL}.",
                "Aperçu seulement : la Glyph Matrix équipe les Nothing Phone (4a) Pro et Phone (3). Ce téléphone : ${Build.MANUFACTURER} ${Build.MODEL}.",
            )
        )
        Block(tr("Matrix to preview", "Matrice à prévisualiser"), tr("New creations are made for this size.", "Les nouvelles créations sont faites pour cette taille.")) {
            Segmented(listOf("(4A) PRO · 13×13", "(3) · 25×25"), if (previewSize == 25) 1 else 0, { i -> Graph.settings.update { it.copy(previewSize = if (i == 1) 25 else 13) } })
        }
        return
    }
    val status by GlyphLink.status.collectAsStateWithLifecycle()
    val testing by GlyphTest.running.collectAsStateWithLifecycle()

    Line(
        tr("Light up the Glyph Matrix", "Allumer la Glyph Matrix"),
        tr("Games, animations and drawings show on the back of the phone", "Jeux, animations et dessins s'affichent au dos du téléphone"),
    ) { NothingSwitch(output, { on -> Graph.settings.update { it.copy(glyphOutput = on) } }) }
    Block(tr("Brightness", "Luminosité"), null) {
        val steps = listOf(25, 50, 75, 100)
        Segmented(steps.map { "$it %" }, steps.indexOf(brightness).coerceAtLeast(0), { i -> Graph.settings.update { it.copy(brightness = steps[i]) } })
    }
    Line(
        tr("Turn it on once", "À activer une fois"),
        FaceDown.howTo,
    ) {
        PillButton(tr("Open", "Ouvrir"), {
            if (!GlyphSupport.openToysManager(context)) {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        })
    }
    Line(
        tr("Test the matrix", "Tester la matrice"),
        if (testing) tr("Look at the back of the phone…", "Regardez le dos du téléphone…") else tr("Scrolls a test message for 8 seconds", "Fait défiler un message de test pendant 8 secondes"),
    ) {
        PillButton(if (testing) tr("Testing", "En cours") else tr("Test", "Tester"), { scope.launch { GlyphTest.run(context) } }, enabled = !testing)
    }
    GlyphDiagnostics(status)
}

/** What the Glyph service told us, so a dark matrix can be explained (and reported). */
@Composable
private fun GlyphDiagnostics(s: GlyphStatus) {
    fun mark(ok: Boolean?) = when (ok) {
        true -> "OK"
        false -> tr("NO", "NON")
        null -> "–"
    }
    val (hint, problem) = when {
        s.error != null -> s.error to true
        s.serviceFound == false ->
            tr(
                "Nothing's Glyph service isn't reachable. Update the phone (Settings › System › System update).",
                "Le service Glyph de Nothing est injoignable. Mettez à jour le téléphone (Paramètres › Système › Mise à jour du système).",
            ) to true
        s.registered == false ->
            tr(
                "The Glyph service refused Glyphies. Check that Glyph Interface is on and the phone is up to date.",
                "Le service Glyph a refusé Glyphies. Vérifiez que Glyph Interface est activée et que le téléphone est à jour.",
            ) to true
        s.connected && s.appFrames + s.toyFrames > 0 ->
            tr(
                "Connected and sending. If the matrix stays dark, check that Glyph Interface is on; notifications and other Glyph effects take priority over apps.",
                "Connecté, envoi en cours. Si la matrice reste éteinte, vérifiez que Glyph Interface est activée ; les notifications et autres effets Glyph passent avant les apps.",
            ) to false
        s.connected -> tr("Connected.", "Connecté.") to false
        s.linking -> tr("Waiting for Nothing's Glyph service to answer…", "En attente de réponse du service Glyph de Nothing…") to false
        else -> tr("Tap Test, or play something, to connect.", "Touchez Tester ou lancez quelque chose pour établir la connexion.") to false
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            "${GlyphSupport.deviceName} · ${GlyphSupport.model} · Android ${Build.VERSION.RELEASE}\n" +
                tr(
                    "Service ${mark(s.serviceFound)} · link ${mark(s.connected)} · access ${mark(s.registered)}\n",
                    "Service ${mark(s.serviceFound)} · liaison ${mark(s.connected)} · accès ${mark(s.registered)}\n",
                ) +
                tr("Frames: app ${s.appFrames} · toy ${s.toyFrames}", "Images : app ${s.appFrames} · toy ${s.toyFrames}") +
                (if (s.toyBound) tr(" · toy active", " · toy actif") else "") +
                (s.lastToyEvent?.let { tr(" · last event $it", " · dernier évènement $it") } ?: ""),
            style = Type.label,
            color = P.textFaint,
        )
        Spacer(Modifier.height(6.dp))
        Text(hint, style = Type.label, color = if (problem) P.accent else P.textDim)
    }
}

@Composable
private fun UpdatesSection(autoUpdate: Boolean) {
    val context = LocalContext.current
    val state by Graph.updater.state.collectAsStateWithLifecycle()
    val last = Graph.updater.lastChecked
    val status = when (val s = state) {
        UpdateState.Idle, UpdateState.UpToDate ->
            if (last > 0) {
                val checked = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))
                tr("Up to date · checked $checked", "À jour · vérifié le $checked")
            } else {
                tr("Not checked yet", "Pas encore vérifié")
            }
        UpdateState.Checking -> tr("Checking…", "Vérification…")
        is UpdateState.Available -> tr("v${s.remote.versionName} available", "v${s.remote.versionName} disponible")
        is UpdateState.Downloading -> {
            val percent = (s.progress * 100).toInt()
            tr("Downloading v${s.remote.versionName} · $percent%", "Téléchargement de la v${s.remote.versionName} · $percent %")
        }
        is UpdateState.Ready -> tr("v${s.remote.versionName} ready to install", "v${s.remote.versionName} prête à installer")
        is UpdateState.Failed -> tr("Update check failed: ${s.message}", "Échec de la vérification : ${s.message}")
    }
    Line("Glyphies ${Graph.updater.currentVersion}", status) {
        when (state) {
            is UpdateState.Ready -> PillButton(tr("Install", "Installer"), { (context as? Activity)?.let { Graph.updater.install(it) } }, style = PillStyle.Accent)
            is UpdateState.Available -> PillButton(tr("Get", "Obtenir"), { Graph.updater.startDownload() })
            UpdateState.Checking, is UpdateState.Downloading -> Unit
            else -> PillButton(tr("Check", "Vérifier"), { Graph.updater.check(manual = true) })
        }
    }
    Line(
        tr("Auto-download updates", "Téléchargement auto des mises à jour"),
        tr(
            "When a new release is published, download it in the background, then ask before installing",
            "Quand une nouvelle version sort, la télécharger en arrière-plan, puis demander avant de l'installer",
        ),
    ) {
        NothingSwitch(autoUpdate, { on -> Graph.settings.update { it.copy(autoUpdate = on) } })
    }
}
