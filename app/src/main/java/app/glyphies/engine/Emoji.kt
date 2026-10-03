package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.hypot

/**
 * An emoji drawn for the Glyph Matrix: one or more 13 × 13 frames (doubled on the Phone (3)'s
 * 25 × 25) and how long each frame stays, so faces blink, hearts beat and flames flicker.
 * Faces use the round matrix itself as the head: only the eyes and mouth light up.
 */
class DotEmoji(
    val key: String,
    private val en: String,
    private val fr: String,
    private val frames13: List<IntArray>,
    val durations: IntArray,
) {
    init {
        require(frames13.size == durations.size)
    }

    fun name(french: Boolean): String = if (french) fr else en

    fun frames(shape: MatrixShape): List<IntArray> = frames13.map { f ->
        if (shape.width == 13) {
            shape.mask(f.copyOf())
        } else {
            // Each dot becomes a 2 × 2 block, centred on the bigger matrix.
            val n = shape.width
            val off = (n - 26) / 2
            shape.mask(IntArray(n * n) { i -> f[(((i / n) - off) / 2).coerceIn(0, 12) * 13 + (((i % n) - off) / 2).coerceIn(0, 12)] })
        }
    }

    companion object {
        /** Rows of '#' (full), '+' (bright), '-' (dim), '*' (faint), '.' (off); missing rows are blank. */
        private fun pic(vararg rows: String): IntArray {
            val out = IntArray(169)
            rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (y < 13 && x < 13) out[y * 13 + x] = Sprite.level(c) } }
            return out
        }

        private fun shifted(f: IntArray, dx: Int, dy: Int): IntArray = IntArray(169) { i ->
            val x = i % 13 - dx
            val y = i / 13 - dy
            if (x in 0..12 && y in 0..12) f[y * 13 + x] else 0
        }

        private fun dimmed(f: IntArray, k: Float): IntArray = IntArray(169) { (f[it] * k).toInt() }

        private fun shape(test: (x: Float, y: Float) -> Int): IntArray = IntArray(169) { i -> test((i % 13).toFloat(), (i / 13).toFloat()) }

        // ------------------------------------------------------------------ faces

        private const val E = "...##...##..." // eyes, 2 wide
        private const val SHUT = "..###...###.." // closed eyes

        private val grin = pic("", "", "", E, E, E, "", "..#.......#..", "..#########..", "...#######...", "....#####....")
        private val grinBlink = pic("", "", "", "", "", SHUT, "", "..#.......#..", "..#########..", "...#######...", "....#####....")

        private val smile = pic("", "", "", E, E, "", "", "", "..#.......#..", "...#.....#...", "....#####....")
        private val smileBlink = pic("", "", "", "", SHUT, "", "", "", "..#.......#..", "...#.....#...", "....#####....")

        private val wink = pic("", "", "", "...##........", "...##..####..", "", "", "", "..#.......#..", "...#.....#...", "....#####....")

        private val laugh = pic("", "", "", "..#.......#..", "...#.....#...", "..#.......#..", "", "..#########..", "..#########..", "...#######...", "....#####....")

        private val tongue = pic("", "", "", E, E, "", "", "..#.......#..", "..#########..", ".......+++...", ".......+++...", "........+....")
        private val tongueWag = pic("", "", "", E, E, "", "", "..#.......#..", "..#########..", "......+++....", "......+++....", ".......+.....")

        private val wow = pic("", "", "", E, E, "", "", ".....###.....", "....#...#....", "....#...#....", ".....###.....")
        private val wowBlink = pic("", "", "", "", SHUT, "", "", ".....###.....", "....#...#....", "....#...#....", ".....###.....")

        private val sad = pic("", "", "", E, E, "", "", "", "....#####....", "...#.....#...", "..#.......#..")
        private fun sadTear(row: Int): IntArray = sad.copyOf().also { it[row * 13 + 3] = 170 }

        private val angry = pic("", "", "..##.....##..", "....#...#....", E, E, "", "", "....#####....", "...#.....#...")

        private val cool = pic("", "", "", ".###########.", ".####...####.", "..##.....##..", "", "", "..#.......#..", "...#.....#...", "....#####....")
        private val coolGlint = pic("", "", "", ".###########.", ".#+##...#+##.", "..##.....##..", "", "", "..#.......#..", "...#.....#...", "....#####....")

        private val sleepyFace = pic("", "", "", "", "", SHUT, "", "", ".....###.....")
        private fun sleepy(z: Int): IntArray = sleepyFace.copyOf().also { f ->
            // a small "z" drifting up to the right
            val (x, y) = if (z == 0) 8 to 2 else 9 to 1
            for ((dx, dy) in listOf(0 to 0, 1 to 0, 1 to 1, 0 to 2, 1 to 2)) f[(y + dy) * 13 + x + dx] = if (z == 0) 170 else 90
        }

        // ------------------------------------------------------------------ things

        private val heartBig = pic(
            "", "", "...##...##...", "..####.####..", ".###########.", ".###########.",
            "..#########..", "...#######...", "....#####....", ".....###.....", "......#......",
        )
        private val heartSmall = pic(
            "", "", "", "....##.##....", "...#######...", "...#######...",
            "....#####....", ".....###.....", "......#......",
        )

        private val star = pic(
            "......#......", "......#......", ".....###.....", ".....###.....", "#############", ".###########.",
            "..#########..", "...#######...", "...#######...", "..####.####..", "..###...###..", "..##.....##..",
        )

        private val fireA = pic(
            "", "......#......", "......##.....", ".....###.....", "....####.#...", "...#####.##..",
            "...########..", "..##########.", "..####++####.", "..###++++###.", "...##++++##..", "....##++##...",
        )
        private val fireB = pic(
            "", ".....#.......", ".....##......", ".....###.....", "..#.####.....", "..#######....",
            "..########...", ".##########..", ".####++####..", ".###++++###..", "..##++++##...", "...##++##....",
        )

        private val bolt = pic(
            "", ".......##....", "......##.....", ".....##......", "....##.......", "...#######...",
            ".......##....", "......##.....", ".....##......", "....##.......", "...##........",
        )

        private val skull = pic(
            "", "....#####....", "..#########..", ".###########.", ".##...#...##.", ".##...#...##.",
            ".###########.", "..####.####..", "...#######...", "...#.#.#.#...", "...#######...",
        )

        private val ghost = pic(
            "", "....#####....", "...#######...", "..#########..", "..##.###.##..", "..##.###.##..",
            "..#########..", "..#########..", "..#########..", "..#########..", "..#.##.##.#..",
        )

        private val alien = pic(
            "", "....#####....", "..#########..", ".###########.", ".#...###...#.", ".##...#...##.",
            "..##.###.##..", "..#########..", "...#######...", "....#####....", ".....###.....",
        )

        private val crabA = pic(
            "", "", "...#.....#...", "....#...#....", "...#######...", "..##.###.##..",
            ".###########.", ".#.#######.#.", ".#.#.....#.#.", "....##.##....",
        )
        private val crabB = pic(
            "", "", "...#.....#...", ".#..#...#..#.", ".#.#######.#.", ".###.###.###.",
            ".###########.", "..#########..", "...#.....#...", "..#.......#..",
        )

        private val note = pic(
            "", "......#......", "......##.....", "......#.#....", "......#..#...", "......#......",
            "......#......", "......#......", "...####......", "..#####......", "..#####......", "...###.......",
        )

        private val check = pic(
            "", "", "", "..........#..", ".........##..", "........##...", ".#.....##....",
            ".##...##.....", "..##.##......", "...###.......", "....#........",
        )

        private val cross = pic(
            "", "", "...#.....#...", "..###...###..", "...###.###...", "....#####....", ".....###.....",
            "....#####....", "...###.###...", "..###...###..", "...#.....#...",
        )

        /** A crescent: a disc with an offset disc taken out. */
        private val moon = shape { x, y ->
            val inner = hypot(x - 6f, y - 6f) <= 5.1f
            val bite = hypot(x - 8.4f, y - 5.2f) <= 4.6f
            if (inner && !bite) 255 else 0
        }

        /** A sun: a disc and eight rays (alternating which ones shine brightest). */
        private fun sun(phase: Int): IntArray = shape { x, y ->
            val d = hypot(x - 6f, y - 6f)
            when {
                d <= 2.6f -> 255
                d in 4.0f..6.2f -> {
                    val dx = x - 6
                    val dy = y - 6
                    val straight = dx == 0f || dy == 0f
                    val diagonal = kotlin.math.abs(dx) == kotlin.math.abs(dy)
                    when {
                        straight -> if (phase == 0) 255 else 120
                        diagonal -> if (phase == 0) 120 else 255
                        else -> 0
                    }
                }
                else -> 0
            }
        }

        /** The set offered in the picker, in order. */
        val all: List<DotEmoji> by lazy {
            listOf(
                DotEmoji("😀", "Grin", "Sourire", listOf(grin, grinBlink), intArrayOf(2600, 140)),
                DotEmoji("🙂", "Smile", "Content", listOf(smile, smileBlink), intArrayOf(3000, 140)),
                DotEmoji("😉", "Wink", "Clin d'œil", listOf(smile, wink), intArrayOf(1600, 700)),
                DotEmoji("😆", "Laugh", "Rire", listOf(laugh, shifted(laugh, 0, 1)), intArrayOf(170, 170)),
                DotEmoji("😛", "Tongue", "Langue", listOf(tongue, tongueWag), intArrayOf(450, 450)),
                DotEmoji("😮", "Wow", "Étonné", listOf(wow, wowBlink), intArrayOf(2400, 140)),
                DotEmoji("😢", "Sad", "Triste", listOf(sad, sadTear(5), sadTear(6), sadTear(7)), intArrayOf(900, 350, 350, 350)),
                DotEmoji("😠", "Angry", "Fâché", listOf(angry, shifted(angry, -1, 0), angry, shifted(angry, 1, 0), angry), intArrayOf(1200, 90, 90, 90, 90)),
                DotEmoji("😎", "Cool", "Cool", listOf(cool, coolGlint), intArrayOf(2000, 400)),
                DotEmoji("😴", "Sleepy", "Endormi", listOf(sleepy(0), sleepy(1)), intArrayOf(900, 900)),
                DotEmoji("❤️", "Heart", "Cœur", listOf(heartSmall, heartBig, heartSmall, heartBig), intArrayOf(600, 180, 140, 260)),
                DotEmoji("⭐", "Star", "Étoile", listOf(star, dimmed(star, 0.55f)), intArrayOf(700, 700)),
                DotEmoji("🔥", "Fire", "Feu", listOf(fireA, fireB), intArrayOf(160, 160)),
                DotEmoji("⚡", "Lightning", "Éclair", listOf(bolt, dimmed(bolt, 0.4f), bolt, dimmed(bolt, 0.4f)), intArrayOf(1400, 80, 80, 80)),
                DotEmoji("💀", "Skull", "Crâne", listOf(skull), intArrayOf(1000)),
                DotEmoji("👻", "Ghost", "Fantôme", listOf(ghost, shifted(ghost, 0, -1)), intArrayOf(600, 600)),
                DotEmoji("👽", "Alien", "Extraterrestre", listOf(alien), intArrayOf(1000)),
                DotEmoji("👾", "Invader", "Envahisseur", listOf(crabA, crabB), intArrayOf(500, 500)),
                DotEmoji("🌙", "Moon", "Lune", listOf(moon), intArrayOf(1000)),
                DotEmoji("☀️", "Sun", "Soleil", listOf(sun(0), sun(1)), intArrayOf(700, 700)),
                DotEmoji("🎵", "Music", "Musique", listOf(note, shifted(note, 0, -1)), intArrayOf(400, 400)),
                DotEmoji("✅", "Yes", "Oui", listOf(check), intArrayOf(1000)),
                DotEmoji("❌", "No", "Non", listOf(cross), intArrayOf(1000)),
            )
        }

        /** Looks an emoji up, ignoring the "emoji style" selector (❤ and ❤️ are the same). */
        fun find(key: String): DotEmoji? {
            val k = key.replace("️", "")
            return all.firstOrNull { it.key.replace("️", "") == k }
        }
    }
}

/**
 * Shows the chosen emoji on the matrix, animated. Tap or volume + for the next one, volume −
 * for the previous one. Emoji outside the drawn set come from [raster] (the phone's emoji font).
 */
class EmojiToy(
    override val shape: MatrixShape,
    private val selected: () -> String,
    private val onSelect: (String) -> Unit = {},
    private val raster: ((String, MatrixShape) -> IntArray?)? = null,
    private val french: Boolean = true,
) : Playable {
    override val id: String = "emoji"
    override val frameMs: Long = 60L

    private var key: String? = null
    private var frames: List<IntArray> = listOf(shape.blank())
    private var durations = intArrayOf(1000)
    private var name = ""
    private var clock = 0f

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        val want = selected()
        if (want != key) load(want)
        val taps = input.presses - input.volumeUp - input.volumeDown
        if (input.volumeUp > 0 || taps > 0) step(1, fx)
        if (input.volumeDown > 0) step(-1, fx)
        clock += dt
    }

    private fun step(by: Int, fx: Fx) {
        val list = DotEmoji.all
        val at = list.indexOfFirst { it == DotEmoji.find(key ?: "") }
        val next = list[((if (at < 0) 0 else at + by) % list.size + list.size) % list.size]
        onSelect(next.key)
        load(next.key)
        fx.buzz(Buzz.TICK)
    }

    private fun load(k: String) {
        key = k
        clock = 0f
        val def = DotEmoji.find(k)
        if (def != null) {
            frames = def.frames(shape)
            durations = def.durations
            name = def.name(french)
            return
        }
        frames = listOf(raster?.invoke(k, shape) ?: shape.blank())
        durations = intArrayOf(1000)
        name = k
    }

    override fun render(out: IntArray) {
        if (key == null) load(selected())
        val total = durations.sum().coerceAtLeast(1)
        var t = ((clock * 1000).toLong() % total).toInt()
        var i = 0
        while (i < durations.size - 1 && t >= durations[i]) {
            t -= durations[i]
            i++
        }
        val f = frames[i.coerceIn(0, frames.lastIndex)]
        for (k in out.indices) out[k] = if (k < f.size) f[k] else 0
    }

    override fun hud(): Hud = Hud(detail = name.uppercase())

    override fun restart() {
        clock = 0f
    }
}
