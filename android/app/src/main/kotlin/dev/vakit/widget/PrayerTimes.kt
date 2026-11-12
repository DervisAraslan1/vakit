package dev.vakit.widget

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Diyanet yöntemiyle çevrimdışı namaz vakti hesabı: imsak 18°, yatsı 17°,
 * temkin payları güneş -7, öğle +5, ikindi +4, akşam +7 dk.
 * Diyanet'in yayımladığı vakitlerle en fazla ±1 dk fark eder.
 */
object PrayerCalc {
    val NAMES = arrayOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")
    val DATIVE = arrayOf("İmsak'a", "Güneş'e", "Öğle'ye", "İkindi'ye", "Akşam'a", "Yatsı'ya")

    private val TEMKIN = doubleArrayOf(0.0, -7.0, 5.0, 4.0, 7.0, 0.0)

    private fun rad(d: Double) = d * Math.PI / 180
    private fun deg(r: Double) = r * 180 / Math.PI
    private fun fix(a: Double, m: Double) = ((a % m) + m) % m

    private fun julian(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y--; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    /** [sapma (derece), zaman denklemi (saat)] */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15, 24.0)
        return doubleArrayOf(deg(asin(sin(rad(e)) * sin(rad(l)))), q / 15 - ra)
    }

    /** Verilen gün için 6 vakit (İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı). */
    fun forDate(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): List<ZonedDateTime> {
        val jd0 = julian(date) - lng / 360
        fun mid(h: Double) = fix(12 - sun(jd0 + h / 24)[1], 24.0)
        fun angle(a: Double, h: Double, before: Boolean): Double {
            val decl = sun(jd0 + h / 24)[0]
            val x = (-sin(rad(a)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val v = deg(acos(x.coerceIn(-1.0, 1.0))) / 15
            return mid(h) + if (before) -v else v
        }
        fun asr(h: Double): Double {
            val decl = sun(jd0 + h / 24)[0]
            return angle(-deg(atan(1 / (1 + tan(rad(abs(lat - decl)))))), h, false)
        }

        // Yerel güneş saatiyle başlangıç tahminleri; üç yinelemede yakınsar.
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(3) {
            t = doubleArrayOf(
                angle(18.0, t[0], true),
                angle(0.833, t[1], true),
                mid(t[2]),
                asr(t[3]),
                angle(0.833, t[4], false),
                angle(17.0, t[5], false),
            )
        }

        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC)
        return t.mapIndexed { i, hours ->
            val minutes = (hours - lng / 15) * 60 + TEMKIN[i]
            // Akşam ve yatsı ihtiyaten yukarı yuvarlanır.
            val rounded = if (i >= 4) ceil(minutes).toLong() else minutes.roundToLong()
            utcMidnight.plusMinutes(rounded).withZoneSameInstant(zone)
        }
    }
}
