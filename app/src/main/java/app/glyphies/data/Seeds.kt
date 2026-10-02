package app.glyphies.data

import app.glyphies.engine.Driver
import app.glyphies.engine.LoopMode
import app.glyphies.engine.Marks
import app.glyphies.engine.Move
import app.glyphies.engine.Sprite
import app.glyphies.engine.Trigger
import app.glyphies.glyph.Frames
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The sample creations a new install starts with, one per idea the editor can do. */
object Seeds {
    val ids = listOf(
        "sample-heart", "sample-mouth", "sample-eye", "sample-compass", "sample-dust",
        "sample-maze", "sample-bricks", "sample-fly", "sample-catch", "sample-shooter",
    )

    /** A 13 × 13 frame from rows of characters: # + - * levels, S start, G goal, X trap. */
    fun grid(vararg rows: String): IntArray {
        val out = IntArray(169)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (y < 13 && x < 13) {
                    out[y * 13 + x] = when (c) {
                        'S' -> Marks.START
                        'G' -> Marks.GOAL
                        'X' -> Marks.TRAP
                        else -> Sprite.level(c)
                    }
                }
            }
        }
        return MatrixShape.PRO_4A.mask(out)
    }

    private fun enc(vararg frames: IntArray): List<String> = frames.map { Frames.encode(it) }

    private val heartBig = grid(
        ".............",
        ".............",
        "...##...##...",
        "..####.####..",
        ".###########.",
        ".###########.",
        "..#########..",
        "...#######...",
        "....#####....",
        ".....###.....",
        "......#......",
    )
    private val heartSmall = grid(
        ".............",
        ".............",
        ".............",
        "....##.##....",
        "...#######...",
        "...#######...",
        "....#####....",
        ".....###.....",
        "......#......",
    )

    private val mouths = listOf(
        grid("", "", "", "", "", "", "...#######..."),
        grid("", "", "", "", "", "....#####....", "...#.....#...", "....#####...."),
        grid("", "", "", "", "....#####....", "...#.....#...", "...#.....#...", "...#.....#...", "....#####...."),
        grid("", "", "", "....#####....", "...#.....#...", "..#.......#..", "..#.......#..", "..#.......#..", "...#.....#...", "....#####...."),
    )

    private fun eye(pupil: Int): IntArray {
        val f = grid(
            "",
            "",
            "",
            "....+++++....",
            "..++.....++..",
            ".+.........+.",
            "+...........+",
            ".+.........+.",
            "..++.....++..",
            "....+++++....",
        )
        for (y in 5..7) for (x in pupil - 1..pupil + 1) f[y * 13 + x] = 255
        return f
    }

    /** An arrow pointing north for each of 8 compass headings (as seen on the back). */
    private fun arrow(k: Int): IntArray {
        val f = IntArray(169)
        val a = -k * PI / 4
        val dx = sin(a)
        val dy = -cos(a)
        for (step in -4..5) {
            val x = (6 + dx * step).roundToInt()
            val y = (6 + dy * step).roundToInt()
            if (x in 0..12 && y in 0..12) f[y * 13 + x] = if (step >= 0) 255 else 90
        }
        // Arrow head
        for (side in listOf(-1, 1)) {
            val ha = a + side * 2.5
            val hx = (6 + dx * 5 + sin(ha) * 2).roundToInt()
            val hy = (6 + dy * 5 - cos(ha) * 2).roundToInt()
            if (hx in 0..12 && hy in 0..12) f[hy * 13 + hx] = 255
        }
        return MatrixShape.PRO_4A.mask(f)
    }

    private val dust = grid(
        "",
        "",
        "..##.....##..",
        "...#.....#...",
        "..#########..",
        ".##.#####.##.",
        "#############",
        "#.#########.#",
        "#.#.......#.#",
        "...##...##...",
    )

    private val maze1 = grid(
        ".............",
        "..S..........",
        ".#########...",
        ".............",
        ".............",
        "....#########",
        ".............",
        ".............",
        "#########....",
        ".............",
        ".........G...",
    )
    private val maze2 = grid(
        ".............",
        ".....S.......",
        "...#######...",
        ".#.........#.",
        ".#.#######.#.",
        ".#.#.....#.#.",
        "...#..G..#...",
        ".#.#.....#.#.",
        ".#.###.###.#.",
        ".#....X....#.",
        "..#########..",
    )

    private val bricks1 = grid(
        "",
        "",
        "...#######...",
        "..##+###+##..",
        "..#########..",
        "...##...##...",
    )
    private val bricks2 = grid(
        "",
        "",
        "..#.#.#.#.#..",
        ".#+#+#+#+#+#.",
        "..#.#.#.#.#..",
        ".+++++++++++.",
    )

    /** A level for the maze template's preview. */
    fun demoMaze(): IntArray = maze1.copyOf()

    fun all(): List<Creation> {
        val now = System.currentTimeMillis()
        var t = now
        fun c(id: String, name: String, kind: CreationKind, frames: List<String>, fps: Int = 6, loop: LoopMode = LoopMode.LOOP, driver: Driver = Driver.TIME, game: GameData? = null) =
            Creation(id = id, name = name, kind = kind, size = 13, frames = frames, fps = fps, loop = loop, driver = driver, game = game, created = t, updated = t--)
        return listOf(
            c("sample-heart", tr("Heartbeat", "Battement"), CreationKind.ANIMATION, enc(heartSmall, heartBig, heartSmall, heartSmall), fps = 4),
            c("sample-mouth", tr("Talking mouth", "Bouche qui parle"), CreationKind.ANIMATION, enc(*mouths.toTypedArray()), driver = Driver.MIC),
            c("sample-eye", tr("Watching eye", "Œil qui suit"), CreationKind.ANIMATION, enc(eye(3), eye(4), eye(6), eye(8), eye(9)), driver = Driver.TILT_X),
            c("sample-compass", tr("North arrow", "Flèche du nord"), CreationKind.ANIMATION, enc(*(0 until 8).map { arrow(it) }.toTypedArray()), driver = Driver.COMPASS),
            c("sample-dust", tr("Crumbling invader", "Envahisseur en sable"), CreationKind.DRAWING, enc(dust), driver = Driver.SAND),
            c(
                "sample-maze", tr("Tilt maze", "Labyrinthe"), CreationKind.GAME, enc(maze1, maze2),
                game = GameData(GameTemplate.MAZE),
            ),
            c(
                "sample-bricks", tr("Smiley bricks", "Briques souriantes"), CreationKind.GAME, enc(bricks1, bricks2),
                game = GameData(GameTemplate.BRICKS, move = Move.TILT, trigger = Trigger.TAP),
            ),
            c(
                "sample-fly", tr("Sing to fly", "Chantez pour voler"), CreationKind.GAME, enc(IntArray(169)),
                game = GameData(GameTemplate.FLY, trigger = Trigger.MIC),
            ),
            c(
                "sample-catch", tr("Catch the stars", "Attrape-étoiles"), CreationKind.GAME, enc(IntArray(169)),
                game = GameData(
                    GameTemplate.CATCH, move = Move.TILT,
                    sprites = mapOf(
                        "player" to SpriteData.of(Sprite.parse("#...#", ".###.")),
                        "item" to SpriteData.of(Sprite.parse("#")),
                        "bomb" to SpriteData.of(Sprite.parse("+")),
                    ),
                ),
            ),
            c(
                "sample-shooter", tr("Squid attack", "Attaque des calmars"), CreationKind.GAME, enc(IntArray(169)),
                game = GameData(
                    GameTemplate.SHOOTER, move = Move.TILT, trigger = Trigger.AUTO,
                    sprites = mapOf(
                        "ship" to SpriteData.of(Sprite.parse(".#.", "#+#")),
                        "invader" to SpriteData.of(Sprite.parse(".#.", "###", "#.#")),
                        "invader2" to SpriteData.of(Sprite.parse(".#.", "###", ".#.")),
                    ),
                ),
            ),
        )
    }
}
