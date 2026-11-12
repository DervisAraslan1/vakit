package dev.vakit.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Minaret(val x: Float, val h: Float, val balconies: Int = 2)

/**
 * Bir caminin stilize cephe tarifi. Ölçüler birimsizdir: zemin y=0, yukarı
 * doğru negatif, tüm yapı yaklaşık ±50 genişliğe ve 100 yüksekliğe sığar.
 */
class Mosque(
    val id: String,
    val name: String,
    val bodyW: Float,
    val bodyH: Float,
    val tiers: List<FloatArray> = emptyList(), // [genişlik, yükseklik], aşağıdan yukarı
    val domeR: Float = 0f,
    val domeFlat: Float = 0.8f,
    val halfDomes: Boolean = false,
    val minarets: List<Minaret>,
    val roofDomes: Int = 0,
    val wing: Boolean = false,
    val wall: Int = 0xFFEADFC8.toInt(),
    /** Kurşun grisi yerine özel kubbe rengi (yeşil, altın, turkuaz...). */
    val domeColor: Int? = null,
    /** Yarım küre yerine sivri külah (Mevlana'nın Yeşil Kubbe'si gibi). */
    val cone: Boolean = false,
    /** Kubbe kasnağının yüksekliği. */
    val drum: Float = 3.6f,
    /** Gövdenin önüne Kâbe çizilir; zemin beyaz mermer olur. */
    val kaaba: Boolean = false,
)

object MosqueArt {
    /** widget.xml'deki vakit şeridinin yüksekliği (dp). */
    const val STRIP_DP = 46f

    private fun sym(vararg m: Minaret) = m.flatMap { listOf(Minaret(-it.x, it.h, it.balconies), it) }

    val ALL = listOf(
        Mosque(
            "ortakoy", "Ortaköy", bodyW = 34f, bodyH = 36f, domeR = 15f, domeFlat = 0.85f,
            minarets = sym(Minaret(20f, 90f, 2)), wing = true,
        ),
        Mosque(
            "sultanahmet", "Sultanahmet", bodyW = 62f, bodyH = 20f,
            tiers = listOf(floatArrayOf(46f, 10f), floatArrayOf(30f, 9f)), domeR = 12f, halfDomes = true,
            minarets = sym(Minaret(26f, 84f, 3), Minaret(38f, 84f, 3), Minaret(48f, 62f, 2)),
        ),
        Mosque(
            "suleymaniye", "Süleymaniye", bodyW = 60f, bodyH = 22f,
            tiers = listOf(floatArrayOf(44f, 11f), floatArrayOf(28f, 9f)), domeR = 14f, halfDomes = true,
            minarets = sym(Minaret(33f, 92f, 3), Minaret(46f, 68f, 2)),
        ),
        Mosque(
            "ayasofya", "Ayasofya", bodyW = 58f, bodyH = 26f,
            tiers = listOf(floatArrayOf(36f, 12f)), domeR = 17f, domeFlat = 0.55f, halfDomes = true,
            minarets = sym(Minaret(36f, 78f, 1), Minaret(47f, 72f, 1)), wall = 0xFFE2B49C.toInt(),
        ),
        Mosque(
            "selimiye", "Selimiye", bodyW = 46f, bodyH = 26f,
            tiers = listOf(floatArrayOf(34f, 10f)), domeR = 16f, domeFlat = 0.9f,
            minarets = sym(Minaret(19f, 97f, 3), Minaret(29f, 97f, 3)),
        ),
        Mosque(
            "yenicami", "Yeni Cami", bodyW = 54f, bodyH = 20f,
            tiers = listOf(floatArrayOf(40f, 10f), floatArrayOf(26f, 8f)), domeR = 11f, halfDomes = true,
            minarets = sym(Minaret(32f, 86f, 3)),
        ),
        Mosque(
            "kocatepe", "Kocatepe", bodyW = 60f, bodyH = 24f,
            tiers = listOf(floatArrayOf(42f, 10f)), domeR = 15f, halfDomes = true,
            minarets = sym(Minaret(34f, 95f, 3), Minaret(45f, 95f, 3)),
        ),
        Mosque(
            "ulucami", "Bursa Ulu Cami", bodyW = 78f, bodyH = 28f, roofDomes = 6,
            minarets = sym(Minaret(44f, 72f, 1)), wall = 0xFFE8D9B5.toInt(),
        ),
        Mosque(
            "camlica", "Çamlıca", bodyW = 64f, bodyH = 22f,
            tiers = listOf(floatArrayOf(48f, 11f), floatArrayOf(32f, 9f)), domeR = 15f, halfDomes = true,
            minarets = sym(Minaret(28f, 97f, 3), Minaret(40f, 97f, 3), Minaret(50f, 74f, 2)),
        ),
        Mosque(
            "fatih", "Fatih", bodyW = 56f, bodyH = 22f,
            tiers = listOf(floatArrayOf(40f, 10f)), domeR = 14f, halfDomes = true,
            minarets = sym(Minaret(32f, 88f, 2)),
        ),
        Mosque(
            "eyupsultan", "Eyüp Sultan", bodyW = 44f, bodyH = 22f,
            tiers = listOf(floatArrayOf(30f, 8f)), domeR = 12f, halfDomes = true,
            minarets = sym(Minaret(27f, 80f, 2)),
        ),
        Mosque(
            "sabanci", "Sabancı Merkez", bodyW = 62f, bodyH = 22f,
            tiers = listOf(floatArrayOf(44f, 10f), floatArrayOf(30f, 8f)), domeR = 14f, halfDomes = true,
            minarets = sym(Minaret(27f, 96f, 3), Minaret(38f, 76f, 2), Minaret(49f, 96f, 3)),
        ),
        Mosque(
            "mevlana", "Mevlana", bodyW = 52f, bodyH = 22f, domeR = 8f, cone = true, drum = 9f,
            minarets = listOf(Minaret(-20f, 78f, 2)), roofDomes = 0,
            tiers = listOf(floatArrayOf(22f, 8f)), domeColor = 0xFF2FA89A.toInt(),
        ),
        Mosque(
            "kabe", "Kâbe", bodyW = 82f, bodyH = 14f, kaaba = true,
            minarets = sym(Minaret(36f, 92f, 3), Minaret(46f, 92f, 3)), wall = 0xFFF4F1EA.toInt(),
        ),
        Mosque(
            "nebevi", "Mescid-i Nebevî", bodyW = 78f, bodyH = 20f, domeR = 9f, drum = 6f,
            minarets = sym(Minaret(30f, 92f, 3), Minaret(46f, 92f, 3)),
            wall = 0xFFF4F1EA.toInt(), domeColor = 0xFF3F8F5A.toInt(),
        ),
        Mosque(
            "aksa", "Mescid-i Aksa", bodyW = 66f, bodyH = 20f, domeR = 10f, domeFlat = 1f, drum = 5f,
            minarets = listOf(Minaret(-40f, 70f, 1), Minaret(40f, 60f, 1)),
            wall = 0xFFE6DCC6.toInt(), domeColor = 0xFF9AA6B2.toInt(),
        ),
        Mosque(
            "kubbetussahra", "Kubbetü's-Sahra", bodyW = 46f, bodyH = 22f, domeR = 13f, domeFlat = 1f, drum = 8f,
            minarets = emptyList(), wall = 0xFF7FA9CF.toInt(), domeColor = 0xFFE2B33C.toInt(),
        ),
    )

    fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: ALL[0]

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt() shl shift
        }
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    private class Palette(m: Mosque, val night: Boolean) {
        private val navy = 0xFF22315E.toInt()
        val wall = if (night) mix(m.wall, navy, 0.5f) else m.wall
        val wallShade = mix(wall, 0xFF000000.toInt(), 0.13f)
        val stone = if (night) mix(0xFFD8CDB4.toInt(), navy, 0.55f) else 0xFFD8CDB4.toInt()
        private val tint = m.domeColor?.let { if (night) mix(it, navy, 0.4f) else it }
        val domeLight = tint?.let { mix(it, 0xFFFFFFFF.toInt(), 0.25f) }
            ?: if (night) 0xFF3D5590.toInt() else 0xFFA3B2C6.toInt()
        val domeDark = tint?.let { mix(it, 0xFF000000.toInt(), 0.25f) }
            ?: if (night) 0xFF1E2C55.toInt() else 0xFF66768F.toInt()
        val window = if (night) 0xFFFFC65C.toInt() else 0xFF55627A.toInt()
        val lawn = when {
            m.kaaba -> if (night) mix(0xFFEDEAE2.toInt(), navy, 0.55f) else 0xFFEDEAE2.toInt()
            night -> 0xFF27433F.toInt()
            else -> 0xFF86AD6E.toInt()
        }
        val gold = 0xFFE7C36A.toInt()
        val goldDark = 0xFFB08A35.toInt()
        val kiswa = if (night) 0xFF0E0F18.toInt() else 0xFF171717.toInt()
        val kiswaShade = if (night) 0xFF07080E.toInt() else 0xFF0B0B0B.toInt()
    }

    /** Camiyi (cx, groundY) noktasına oturtarak çizer; s = birim başına piksel. */
    fun draw(c: Canvas, m: Mosque, cx: Float, groundY: Float, s: Float, night: Boolean) {
        val pal = Palette(m, night)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) {
            p.color = color
            c.drawRect(l, t, r, b, p)
        }

        fun window(x: Float, bottom: Float, w: Float, h: Float) {
            if (night) {
                p.color = pal.window
                p.alpha = 70
                c.drawRoundRect(x - w, bottom - h - w / 2, x + w, bottom + w / 2, w, w, p)
                p.alpha = 255
            }
            p.color = pal.window
            c.drawRoundRect(x - w / 2, bottom - h, x + w / 2, bottom, w / 2, w / 2, p)
            c.drawRect(x - w / 2, bottom - h / 2, x + w / 2, bottom, p)
        }

        fun windowRow(x0: Float, x1: Float, bottom: Float, h: Float, n: Int) {
            if (n <= 0) return
            val step = (x1 - x0) / n
            for (i in 0 until n) window(x0 + step * (i + 0.5f), bottom, min(2.6f, step * 0.45f), h)
        }

        fun dome(x: Float, base: Float, r: Float, flat: Float = 1f) {
            p.shader = LinearGradient(x - r, 0f, x + r, 0f, pal.domeLight, pal.domeDark, Shader.TileMode.CLAMP)
            c.drawArc(x - r, base - r * flat, x + r, base + r * flat, 180f, 180f, true, p)
            p.shader = null
        }

        fun smallDome(x: Float, base: Float, r: Float) {
            rect(x - r * 0.8f, base - r * 0.5f, x + r * 0.8f, base, pal.wall)
            dome(x, base - r * 0.5f, r)
        }

        /** Sağ tarafı gölgeli duvar bloğu. */
        fun block(l: Float, t: Float, r: Float, b: Float) {
            rect(l, t, r, b, pal.wall)
            rect(l + (r - l) * 0.68f, t, r, b, pal.wallShade)
            rect(l - 0.8f, t - 1.2f, r + 0.8f, t, pal.stone)
        }

        c.save()
        c.translate(cx, groundY)
        c.scale(s, s)

        val bw = m.bodyW / 2
        val wingW = if (m.wing) 24f else 0f
        val left = min(-bw - wingW, (m.minarets.minOfOrNull { it.x } ?: 0f) - 3f)
        val right = max(bw, (m.minarets.maxOfOrNull { it.x } ?: 0f) + 3f)

        // Kaide ve çimen
        p.color = pal.stone
        c.drawRoundRect(left - 5f, 0f, right + 5f, 5f, 1.5f, 1.5f, p)
        p.color = pal.lawn
        c.drawRoundRect(left - 5f, -1f, right + 5f, 1.6f, 1.3f, 1.3f, p)

        // Minareler (gövdenin arkasında)
        for (mn in m.minarets) {
            val w = 3.4f
            val cone = 10f
            val top = -mn.h
            rect(mn.x - w / 2, top + cone, mn.x + w / 2, 0f, pal.wall)
            rect(mn.x + w / 6, top + cone, mn.x + w / 2, 0f, pal.wallShade)
            for (i in 0 until mn.balconies) {
                val y = top + cone + 5f + 13f * i
                rect(mn.x - w / 2 - 1.3f, y, mn.x + w / 2 + 1.3f, y + 1.7f, pal.stone)
                if (night) rect(mn.x - w / 2 - 0.9f, y + 1.7f, mn.x + w / 2 + 0.9f, y + 2.5f, pal.window)
            }
            p.color = pal.domeDark
            c.drawPath(Path().apply {
                moveTo(mn.x - w / 2 - 0.5f, top + cone)
                lineTo(mn.x, top)
                lineTo(mn.x + w / 2 + 0.5f, top + cone)
                close()
            }, p)
            p.color = pal.gold
            c.drawCircle(mn.x, top, 0.8f, p)
        }

        // Yan kanat (Ortaköy'ün hünkâr kasrı gibi)
        if (m.wing) {
            val wh = m.bodyH * 0.58f
            block(-bw - wingW, -wh, -bw, 0f)
            windowRow(-bw - wingW + 1f, -bw - 1f, -wh * 0.56f, wh * 0.28f, 5)
            windowRow(-bw - wingW + 1f, -bw - 1f, -wh * 0.12f, wh * 0.28f, 5)
        }

        // Ana gövde
        block(-bw, -m.bodyH, bw, 0f)
        val cols = (m.bodyW / 6.5f).toInt()
        windowRow(-bw + 1.5f, bw - 1.5f, -m.bodyH * 0.56f, m.bodyH * 0.3f, cols)
        windowRow(-bw + 1.5f, bw - 1.5f, -m.bodyH * 0.1f, m.bodyH * 0.3f, cols)

        // Kâbe: revakların önünde siyah örtülü küp, altın kuşak ve kapı.
        if (m.kaaba) {
            val k = 12f
            val kh = 27f
            rect(-k, -kh, k, 0f, pal.kiswa)
            rect(k * 0.36f, -kh, k, 0f, pal.kiswaShade)
            rect(-k, -kh + 5f, k, -kh + 7.6f, pal.gold)
            p.color = pal.goldDark
            for (i in 0 until 9) c.drawRect(-k + 1.3f + i * 2.6f, -kh + 5.9f, -k + 2.5f + i * 2.6f, -kh + 6.7f, p)
            rect(2.2f, -16f, 7.6f, -4.5f, pal.gold)
            rect(3.2f, -15f, 6.6f, -5.5f, pal.goldDark)
        }

        // Çatı kubbeleri (çok kubbeli ulu cami tipi)
        if (m.roofDomes > 0) {
            val step = m.bodyW / m.roofDomes
            for (i in 0 until m.roofDomes) smallDome(-bw + step * (i + 0.5f), -m.bodyH - 1.2f, step * 0.4f)
        }

        // Kademeler ve köşe kubbeleri
        var y = -m.bodyH - 1.2f
        for (t in m.tiers) {
            val tw = t[0] / 2
            val th = t[1]
            smallDome(-tw - 3.6f, y, 3.2f)
            smallDome(tw + 3.6f, y, 3.2f)
            block(-tw, y - th, tw, y)
            windowRow(-tw + 1f, tw - 1f, y - th * 0.22f, th * 0.5f, (t[0] / 5f).toInt())
            y -= th + 1.2f
        }

        if (m.domeR > 0f) {
            val r = m.domeR
            if (m.halfDomes) {
                dome(-r * 0.95f, y, r * 0.62f)
                dome(r * 0.95f, y, r * 0.62f)
            }
            val drum = m.drum
            rect(-r * 0.92f, y - drum, r * 0.92f, y, pal.wall)
            windowRow(-r * 0.85f, r * 0.85f, y - drum * 0.2f, min(drum * 0.6f, 4.5f), (r / 2f).toInt())
            val tip: Float
            if (m.cone) {
                tip = y - drum - r * 2.4f
                p.shader = LinearGradient(-r, 0f, r, 0f, pal.domeLight, pal.domeDark, Shader.TileMode.CLAMP)
                c.drawPath(Path().apply {
                    moveTo(-r * 0.95f, y - drum)
                    lineTo(0f, tip)
                    lineTo(r * 0.95f, y - drum)
                    close()
                }, p)
                p.shader = null
            } else {
                dome(0f, y - drum, r, m.domeFlat)
                tip = y - drum - r * m.domeFlat
            }
            p.color = pal.gold
            c.drawRect(-0.3f, tip - 4f, 0.3f, tip, p)
            c.drawCircle(0f, tip - 4f, 0.9f, p)
        }
        c.restore()
    }

    private val STARS = floatArrayOf(
        // Sol yarı yazıya ayrılmıştır; yıldızlar sağ yarıda kalır.
        0.52f, 0.14f, 0.63f, 0.40f, 0.60f, 0.07f, 0.68f, 0.30f, 0.80f, 0.07f,
        0.93f, 0.16f, 0.56f, 0.52f, 0.74f, 0.20f, 0.97f, 0.40f, 0.86f, 0.30f,
    )

    /** deep: üstüne beyaz yazı gelen küçük widget'lar için daha koyu gündüz mavisi. */
    private fun sky(c: Canvas, w: Int, h: Int, night: Boolean, deep: Boolean = false): Int {
        val top = if (night) 0xFF0D1840.toInt() else if (deep) 0xFF2460BF.toInt() else 0xFF2E6CCB.toInt()
        val bottom = if (night) 0xFF22336C.toInt() else if (deep) 0xFF4F86DB.toInt() else 0xFF8FB6EE.toInt()
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return top
    }

    /** Widget arka planı: gökyüzü, güneş ya da ay, sağda cami. */
    fun scene(w: Int, h: Int, m: Mosque, night: Boolean, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        sky(c, w, h, night)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val groundY = h - (STRIP_DP + 8f) * density
        val s = min((groundY - 5f * density) / 98f, w * 0.34f / 100f)
        val leftExtent = leftExtent(m)
        val mosqueCx = w - 14f * density - 50f * s
        val mosqueLeft = mosqueCx - (leftExtent + 5f) * s

        // Güneş/ay, yazı ile cami arasındaki boşlukta durur.
        val r = h * 0.10f
        val sx = min(w * 0.53f, mosqueLeft - r * 1.6f)
        val sy = h * 0.27f
        if (night) {
            p.color = 0xFFFFFFFF.toInt()
            for (i in STARS.indices step 2) {
                p.alpha = if (i % 4 == 0) 220 else 150
                c.drawCircle(w * STARS[i], groundY * STARS[i + 1] * 1.6f, 1.3f * density, p)
            }
            p.alpha = 255
            p.color = 0xFFF3E3B0.toInt()
            val moon = Path().apply { addCircle(sx, sy, r, Path.Direction.CW) }
            val cut = Path().apply { addCircle(sx + r * 0.45f, sy - r * 0.25f, r * 0.85f, Path.Direction.CW) }
            moon.op(cut, Path.Op.DIFFERENCE)
            c.drawPath(moon, p)
        } else {
            p.color = 0x40FFFFFF
            c.drawCircle(sx, sy, r * 1.55f, p)
            p.color = 0xFFF7CE68.toInt()
            c.drawCircle(sx, sy, r, p)
        }

        draw(c, m, mosqueCx, groundY, s, night)
        return bmp
    }

    private fun leftExtent(m: Mosque) =
        max(m.bodyW / 2 + if (m.wing) 24f else 0f, -(m.minarets.minOfOrNull { it.x } ?: 0f) + 3f)

    private fun rightExtent(m: Mosque) = max(m.bodyW / 2, (m.minarets.maxOfOrNull { it.x } ?: 0f) + 3f)

    /** Flutter arayüzü için saydam zeminli cami görseli; kaide alt kenara oturur, yatayda ortalanır. */
    fun sprite(size: Int, m: Mosque, night: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val left = leftExtent(m)
        val right = rightExtent(m)
        val s = min(size / (left + right + 12f), size / 108f)
        draw(Canvas(bmp), m, size / 2f + (left - right) / 2f * s, size - 6f * s, s, night)
        return bmp
    }

    /** Gündüz güneş, gece hilal. */
    private fun orb(c: Canvas, x: Float, y: Float, r: Float, night: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (night) {
            p.color = 0xFFF3E3B0.toInt()
            val moon = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
            val cut = Path().apply { addCircle(x + r * 0.45f, y - r * 0.25f, r * 0.85f, Path.Direction.CW) }
            moon.op(cut, Path.Op.DIFFERENCE)
            c.drawPath(moon, p)
        } else {
            p.color = 0x40FFFFFF
            c.drawCircle(x, y, r * 1.55f, p)
            p.color = 0xFFF7CE68.toInt()
            c.drawCircle(x, y, r, p)
        }
    }

    /** Yıldızları genişliğin fromX oranından sağına, yüksekliğin üst kısmına serper. */
    private fun stars(c: Canvas, w: Int, h: Float, density: Float, fromX: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt()
        for (i in STARS.indices step 2) {
            p.alpha = if (i % 4 == 0) 220 else 150
            val nx = (STARS[i] - 0.5f) * 2f
            c.drawCircle(w * (fromX + (1f - fromX) * nx), h * STARS[i + 1] * 1.6f, 1.3f * density, p)
        }
    }

    enum class Scene { WIDE, PLAIN, ORB, SQUARE, TINY }

    /** Widget arka planı; boyuta göre sahne seçilir. */
    fun widget(w: Int, h: Int, m: Mosque, night: Boolean, density: Float, style: Scene): Bitmap {
        if (style == Scene.WIDE) return scene(w, h, m, night, density)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        sky(c, w, h, night, deep = style != Scene.SQUARE)
        when (style) {
            Scene.SQUARE -> {
                // Yazı üstte durur; cami alt yarıya, sağa yaslı oturur.
                val groundY = h - 12f * density
                val right = rightExtent(m)
                val s = min(
                    (groundY - 70f * density) / 98f,
                    w * 0.82f / (leftExtent(m) + right + 10f),
                ).coerceAtLeast(0.2f * density)
                if (night) stars(c, w, h * 0.5f, density, 0.5f)
                val r = h * 0.075f
                orb(c, w - r * 2.4f, r * 2.2f, r, night)
                draw(c, m, w - 10f * density - (right + 5f) * s, groundY, s, night)
            }
            Scene.ORB -> {
                if (night) stars(c, w, h.toFloat(), density, 0.55f)
                // Sağ üst köşe: saatli geri sayım ("2:16:36") güneşin altından geçmesin.
                orb(c, w - h * 0.32f, h * 0.32f, h * 0.15f, night)
            }
            Scene.TINY -> orb(c, w / 2f, h * 0.22f, h * 0.10f, night)
            else -> {}
        }
        return bmp
    }

    /**
     * Uygulamanın üst kısmındaki büyük sahne. Cami, [top] pikselinin altına
     * ortalanır; alt köşeler [page] rengiyle yuvarlatılır.
     */
    fun hero(w: Int, h: Int, m: Mosque, night: Boolean, density: Float, top: Float, orbY: Float, page: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        sky(c, w, h, night)
        if (night) stars(c, w, h * 0.55f, density)
        orb(c, w - 54f * density, orbY, 17f * density, night)

        val groundY = h - 24f * density
        val left = leftExtent(m)
        val right = rightExtent(m)
        val s = min((groundY - top) / 98f, w * 0.8f / (left + right + 10f)).coerceAtLeast(0.2f * density)
        draw(c, m, w / 2f + (left - right) / 2f * s, groundY, s, night)

        val radius = 28f * density
        val corners = Path().apply { addRect(0f, h - radius, w.toFloat(), h.toFloat(), Path.Direction.CW) }
        val keep = Path().apply {
            addRoundRect(0f, h - radius * 4, w.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW)
        }
        corners.op(keep, Path.Op.DIFFERENCE)
        c.drawPath(corners, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = page })
        return bmp
    }

    /** Ayarlar ekranındaki seçim kartı için kare küçük resim. */
    fun thumb(size: Int, m: Mosque): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        sky(c, size, size, false)
        val s = size * 0.9f / 112f
        // Kanatlı camilerde gövde sola taşar; görseli ortalamak için kaydır.
        val shift = if (m.wing) 9f * s else 0f
        draw(c, m, size / 2f + shift, size * 0.90f, s, false)
        return bmp
    }
}
