package app.glyphies.data

import app.glyphies.engine.Driver
import app.glyphies.engine.LoopMode
import app.glyphies.engine.Move
import app.glyphies.engine.Sprite
import app.glyphies.engine.Trigger
import app.glyphies.glyph.Frames
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import kotlinx.serialization.Serializable

enum class CreationKind { DRAWING, ANIMATION, GAME }

/** A sprite as stored: hex brightness, two characters per pixel (see [Frames.encode]). */
@Serializable
data class SpriteData(val w: Int, val h: Int, val px: String) {
    fun toSprite(): Sprite = Sprite(w, h, Frames.decode(px, w * h))

    companion object {
        fun of(s: Sprite): SpriteData = SpriteData(s.w, s.h, Frames.encode(s.px))
    }
}

@Serializable
data class GameData(
    val template: GameTemplate,
    val move: Move = Move.TILT,
    val trigger: Trigger = template.defaultTrigger,
    val speed: Int = 3,
    val lives: Int = 3,
    /** Role key (see [GameTemplate.roles]) → its picture. Missing roles use the defaults. */
    val sprites: Map<String, SpriteData> = emptyMap(),
) {
    fun sprite(role: Role, shape: MatrixShape): Sprite =
        sprites[role.key]?.toSprite()?.takeUnless { it.isEmpty } ?: role.default(shape)
}

/**
 * Something made in the editor. Drawings and animations are frames of the matrix; a game is a
 * template plus its pictures, settings and (for mazes and bricks) levels drawn as frames.
 */
@Serializable
data class Creation(
    val id: String,
    val name: String,
    val kind: CreationKind,
    /** 13 (Phone (4a) Pro) or 25 (Phone (3)). */
    val size: Int = 13,
    /** One hex-encoded frame per entry (see [Frames.encode]). */
    val frames: List<String> = listOf(""),
    val fps: Int = 6,
    val loop: LoopMode = LoopMode.LOOP,
    val driver: Driver = Driver.TIME,
    val game: GameData? = null,
    val created: Long = 0,
    val updated: Long = 0,
) {
    val shape: MatrixShape get() = MatrixShape.forSize(size)

    fun frame(i: Int): IntArray = Frames.decode(frames.getOrElse(i) { "" }, shape.cells)

    fun decodedFrames(): List<IntArray> = frames.map { Frames.decode(it, shape.cells) }.ifEmpty { listOf(shape.blank()) }

    fun withFrames(list: List<IntArray>): Creation = copy(frames = list.map { Frames.encode(it) })

    val kindLabel: String
        get() = when (kind) {
            CreationKind.DRAWING -> tr("DRAWING", "DESSIN")
            CreationKind.ANIMATION -> tr("ANIMATION", "ANIMATION")
            CreationKind.GAME -> game?.template?.label ?: tr("GAME", "JEU")
        }
}

/** A picture a game template needs (the ship, the falling rock…). */
data class Role(
    val key: String,
    private val en: String,
    private val fr: String,
    /** Largest picture for a 13 × 13 matrix (doubled for 25 × 25). */
    val maxW: Int,
    val maxH: Int,
    val default: (MatrixShape) -> Sprite,
    val optional: Boolean = false,
) {
    val label: String get() = tr(en, fr)
    fun maxW(shape: MatrixShape): Int = if (shape.width >= 20) maxW * 2 - 1 else maxW
    fun maxH(shape: MatrixShape): Int = if (shape.width >= 20) maxH * 2 - 1 else maxH
}

private fun big(shape: MatrixShape) = shape.width >= 20

enum class GameTemplate(
    private val en: String,
    private val fr: String,
    private val descEn: String,
    private val descFr: String,
    val usesLevels: Boolean,
    val moves: List<Move>,
    val triggers: List<Trigger>,
    val defaultTrigger: Trigger,
) {
    SHOOTER(
        "SHOOTER", "TIR",
        "Space Invaders with your own ship and invaders. Slide the ship, shoot the formation.",
        "Space Invaders avec votre vaisseau et vos envahisseurs. Glissez le vaisseau, tirez sur la formation.",
        usesLevels = false,
        moves = listOf(Move.TILT, Move.TOUCH, Move.MIC),
        triggers = listOf(Trigger.AUTO, Trigger.TAP, Trigger.CLAP, Trigger.SHAKE),
        defaultTrigger = Trigger.AUTO,
    ),
    DODGE(
        "DODGE", "ESQUIVE",
        "Things fall from the top: stay out of their way. Every one dodged scores.",
        "Des choses tombent du haut : évitez-les. Chaque esquive rapporte un point.",
        usesLevels = false,
        moves = listOf(Move.TILT, Move.TOUCH, Move.MIC),
        triggers = emptyList(),
        defaultTrigger = Trigger.TAP,
    ),
    CATCH(
        "CATCH", "ATTRAPE",
        "Catch what falls, avoid the bombs (dimmer). A missed catch costs a life.",
        "Attrapez ce qui tombe, évitez les bombes (plus pâles). Un objet raté coûte une vie.",
        usesLevels = false,
        moves = listOf(Move.TILT, Move.TOUCH, Move.MIC),
        triggers = emptyList(),
        defaultTrigger = Trigger.TAP,
    ),
    MAZE(
        "MAZE", "LABYRINTHE",
        "Tilt to roll the ball to the blinking goal. Each level is a frame: draw walls, a start, a goal and traps.",
        "Inclinez pour rouler la bille jusqu'à l'arrivée qui clignote. Chaque niveau est une image : dessinez murs, départ, arrivée et pièges.",
        usesLevels = true,
        moves = emptyList(),
        triggers = emptyList(),
        defaultTrigger = Trigger.TAP,
    ),
    FLY(
        "FLY", "ENVOL",
        "Keep the bird between the pipes: tap, clap, shake, or fly with your voice.",
        "Gardez l'oiseau entre les tuyaux : touchez, tapez dans vos mains, secouez, ou volez à la voix.",
        usesLevels = false,
        moves = emptyList(),
        triggers = listOf(Trigger.TAP, Trigger.CLAP, Trigger.SHAKE, Trigger.MIC),
        defaultTrigger = Trigger.TAP,
    ),
    BRICKS(
        "BRICKS", "CASSE-BRIQUES",
        "Breakout: each level is a frame whose lit dots are bricks. Slide the paddle, keep the ball up.",
        "Casse-briques : chaque niveau est une image dont les points allumés sont des briques. Glissez la raquette, gardez la balle en jeu.",
        usesLevels = true,
        moves = listOf(Move.TILT, Move.TOUCH, Move.MIC),
        triggers = listOf(Trigger.TAP, Trigger.AUTO, Trigger.CLAP, Trigger.SHAKE),
        defaultTrigger = Trigger.TAP,
    );

    val label: String get() = tr(en, fr)
    val description: String get() = tr(descEn, descFr)

    val roles: List<Role>
        get() = when (this) {
            SHOOTER -> listOf(
                Role("ship", "Ship", "Vaisseau", 5, 3, { s -> if (big(s)) Sprite.parse("..#..", ".###.", "#####") else Sprite.parse(".#.", "###") }),
                Role("invader", "Invader", "Envahisseur", 5, 3, { s -> if (big(s)) Sprite.parse(".###.", "#.#.#", "#####", ".#.#.") else Sprite.parse("###", "#.#") }),
                Role("invader2", "Invader, 2nd step", "Envahisseur, 2e pas", 5, 3, { s -> if (big(s)) Sprite.parse(".###.", "#.#.#", "#####", "#...#") else Sprite.parse("###", ".#.") }, optional = true),
            )
            DODGE -> listOf(
                Role("player", "Player", "Joueur", 5, 3, { s -> if (big(s)) Sprite.parse("..#..", ".###.") else Sprite.parse(".#.", "###") }),
                Role("obstacle", "Obstacle", "Obstacle", 4, 3, { s -> if (big(s)) Sprite.parse(".##.", "####", ".##.") else Sprite.parse("##") }),
            )
            CATCH -> listOf(
                Role("player", "Basket", "Panier", 5, 3, { s -> if (big(s)) Sprite.parse("#...#", "#####") else Sprite.parse("#.#", "###") }),
                Role("item", "Item", "Objet", 3, 3, { s -> if (big(s)) Sprite.parse(".#.", "###", ".#.") else Sprite.parse("#") }),
                Role("bomb", "Bomb", "Bombe", 3, 3, { s -> if (big(s)) Sprite.parse("##", "##") else Sprite.parse("#") }, optional = true),
            )
            MAZE -> emptyList()
            FLY -> listOf(
                Role("bird", "Bird", "Oiseau", 3, 2, { s -> if (big(s)) Sprite.parse("##", "##") else Sprite.parse("#") }),
            )
            BRICKS -> listOf(
                Role("paddle", "Paddle (one row)", "Raquette (une ligne)", 5, 1, { s -> if (big(s)) Sprite.parse("#####") else Sprite.parse("###") }),
            )
        }

    fun role(key: String): Role? = roles.firstOrNull { it.key == key }
}

val Move.label: String
    get() = when (this) {
        Move.TILT -> tr("TILT", "INCLINER")
        Move.TOUCH -> tr("FINGER", "DOIGT")
        Move.MIC -> tr("VOICE", "VOIX")
    }

val Move.description: String
    get() = when (this) {
        Move.TILT -> tr("Tilt the phone left and right.", "Inclinez le téléphone à gauche et à droite.")
        Move.TOUCH -> tr("Slide a finger across the screen (it works behind the phone too).", "Glissez un doigt sur l'écran (ça marche aussi derrière le téléphone).")
        Move.MIC -> tr("Quiet = left, loud = right: hum to move.", "Silence = à gauche, fort = à droite : fredonnez pour bouger.")
    }

val Trigger.label: String
    get() = when (this) {
        Trigger.AUTO -> "AUTO"
        Trigger.TAP -> tr("TAP", "TOUCHER")
        Trigger.CLAP -> tr("CLAP", "CLAP")
        Trigger.SHAKE -> tr("SHAKE", "SECOUER")
        Trigger.MIC -> tr("VOICE", "VOIX")
    }

val Trigger.description: String
    get() = when (this) {
        Trigger.AUTO -> tr("Happens on its own.", "Se fait tout seul.")
        Trigger.TAP -> tr("Tap the screen or press a volume key.", "Touchez l'écran ou appuyez sur une touche de volume.")
        Trigger.CLAP -> tr("Clap your hands or make a short loud sound.", "Tapez dans vos mains ou faites un bruit court et fort.")
        Trigger.SHAKE -> tr("Give the phone a quick shake.", "Secouez le téléphone d'un coup sec.")
        Trigger.MIC -> tr("Make noise: the louder, the stronger.", "Faites du bruit : plus c'est fort, plus ça monte.")
    }

val Driver.label: String
    get() = when (this) {
        Driver.TIME -> tr("TIME", "TEMPS")
        Driver.MIC -> tr("VOICE", "VOIX")
        Driver.TILT_X -> tr("TILT ↔", "INCLIN. ↔")
        Driver.TILT_Y -> tr("TILT ↕", "INCLIN. ↕")
        Driver.COMPASS -> tr("COMPASS", "BOUSSOLE")
        Driver.LIGHT -> tr("LIGHT", "LUMIÈRE")
        Driver.PROXIMITY -> tr("HAND", "MAIN")
        Driver.SHAKE -> tr("SHAKE", "SECOUER")
        Driver.TAP -> tr("TAP", "TOUCHER")
        Driver.CLAP -> "CLAP"
        Driver.SAND -> tr("SAND", "SABLE")
    }

val Driver.description: String
    get() = when (this) {
        Driver.TIME -> tr("Plays on its own at the frame rate.", "Se joue toute seule à la cadence choisie.")
        Driver.MIC -> tr("The louder you are, the later the frame: first frame = silence, last = shouting.", "Plus vous parlez fort, plus l'image est loin : première = silence, dernière = cri.")
        Driver.TILT_X -> tr("Tilting from left to right goes from the first frame to the last.", "Incliner de gauche à droite va de la première image à la dernière.")
        Driver.TILT_Y -> tr("Tilting from forward to back goes from the first frame to the last.", "Incliner de l'avant vers l'arrière va de la première image à la dernière.")
        Driver.COMPASS -> tr("The frames go round as you turn: frame 1 facing north, then clockwise.", "Les images tournent avec vous : image 1 face au nord, puis dans le sens horaire.")
        Driver.LIGHT -> tr("Dark room = first frame, bright light = last.", "Pièce sombre = première image, forte lumière = dernière.")
        Driver.PROXIMITY -> tr("Frame 1 normally; the others play while a hand covers the top of the screen.", "Image 1 au repos ; les autres se jouent quand une main couvre le haut de l'écran.")
        Driver.SHAKE -> tr("Each shake shows the next frame.", "Chaque secousse montre l'image suivante.")
        Driver.TAP -> tr("Each tap or volume key shows the next frame.", "Chaque toucher ou touche de volume montre l'image suivante.")
        Driver.CLAP -> tr("Each clap shows the next frame.", "Chaque clap montre l'image suivante.")
        Driver.SAND -> tr("Shake and the first frame crumbles into sand that follows the tilt. Tap to rebuild it.", "Secouez : la première image s'effrite en sable qui suit l'inclinaison. Touchez pour la reconstruire.")
    }

val LoopMode.label: String
    get() = when (this) {
        LoopMode.LOOP -> tr("LOOP", "BOUCLE")
        LoopMode.BOUNCE -> tr("BOUNCE", "ALLER-RETOUR")
        LoopMode.ONCE -> tr("ONCE", "UNE FOIS")
    }
