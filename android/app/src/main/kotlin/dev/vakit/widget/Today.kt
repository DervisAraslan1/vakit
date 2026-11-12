package dev.vakit.widget

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Şu anki durum: bugünün vakitleri, sıradaki vakit ve gece/gündüz. */
class Today(
    val now: ZonedDateTime,
    val city: City,
    val times: List<ZonedDateTime>,
    /** Sıradaki vaktin indeksi (0 = İmsak ... 5 = Yatsı). */
    val next: Int,
    val nextTime: ZonedDateTime,
    /** Yatsı geçti; sıradaki vakit yarının imsağı. */
    val afterIsha: Boolean,
    /** Sıradaki vaktin düştüğü gün Ramazan'da mı (iftar/sahur dili için). */
    val ramadan: Boolean,
    /** Bugün Ramazan'ın kaçıncı günü; Ramazan değilse null. */
    val ramadanDay: Int?,
) {
    /** İçinde bulunulan vakit. İmsaktan önce de yatsıdır. */
    val current get() = if (next == 0) 5 else next - 1

    val night get() = now.isBefore(times[1]) || !now.isBefore(times[4])

    /** "İkindi'ye", Ramazan'da akşam için "İftara", imsak için "Sahura". */
    val nextLabel get() = label(next, ramadan)

    /** Görünümün bir sonraki yenilenmesi gereken an: vakit girişi ya da gün değişimi. */
    val refreshAt: ZonedDateTime
        get() {
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZONE)
            return if (nextTime.isBefore(midnight)) nextTime else midnight
        }

    val targetText get() = "${if (afterIsha) "Yarın" else "Bugün"} ${nextTime.format(HM)}"

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
        val TR: Locale = Locale.forLanguageTag("tr")
        val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM · EEEE", TR)

        /** Hicri takvimde Ramazan 9. aydır. */
        const val RAMADAN = 9

        fun label(prayer: Int, ramadan: Boolean) = when {
            ramadan && prayer == 4 -> "İftara"
            ramadan && prayer == 0 -> "Sahura"
            else -> PrayerCalc.DATIVE[prayer]
        }

        /** Resmi tablodan hicri gün ve ay; tablo yoksa null. */
        fun hijri(ctx: Context, date: LocalDate): Pair<Int, Int>? {
            val parts = Diyanet.day(ctx, date)?.hijri?.split('.') ?: return null
            val day = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return day to month
        }

        fun isRamadan(ctx: Context, date: LocalDate) = hijri(ctx, date)?.second == RAMADAN

        /** Bir günün vakitleri: varsa Diyanet'in resmi tablosu, yoksa formül. */
        fun timesFor(ctx: Context, date: LocalDate): List<ZonedDateTime> {
            val city = Store.city(ctx)
            return Diyanet.times(ctx, date) ?: PrayerCalc.forDate(date, city.lat, city.lng, ZONE)
        }

        fun of(ctx: Context): Today {
            val now = ZonedDateTime.now(ZONE)
            val city = Store.city(ctx)
            val today = now.toLocalDate()
            val times = timesFor(ctx, today)
            var next = times.indexOfFirst { it.isAfter(now) }
            val afterIsha = next < 0
            val nextTime = if (afterIsha) {
                next = 0
                timesFor(ctx, today.plusDays(1))[0]
            } else {
                times[next]
            }
            val todayHijri = hijri(ctx, today)
            return Today(
                now, city, times, next, nextTime, afterIsha,
                ramadan = isRamadan(ctx, nextTime.toLocalDate()),
                ramadanDay = todayHijri?.takeIf { it.second == RAMADAN }?.first,
            )
        }
    }
}
