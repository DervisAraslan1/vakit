package dev.vakit.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Diyanet'in ilçe bazındaki resmi vakit tablosu. 30 günlük tablo ağdan çekilip
 * dosyada saklanır; tablo yoksa çağıranlar [PrayerCalc] formülüne düşer.
 */
object Diyanet {
    private const val BASE = "https://ezanvakti.emushaf.net"
    private val API_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val HM = Regex("""\d\d:\d\d""")

    /** times: İmsak..Yatsı "HH:mm"; hijri: "2.6.1448"; hijriLong: "2 Cemaziyelahir 1448". */
    class Day(val times: List<String>, val hijri: String, val hijriLong: String)

    private var loadedFor: String? = null
    private var days = HashMap<String, Day>() // anahtar: yyyy-MM-dd

    private fun file(ctx: Context, districtId: String) = File(ctx.filesDir, "vakit_$districtId.json")

    private fun load(ctx: Context) {
        val id = Store.districtId(ctx)
        if (id == loadedFor) return
        loadedFor = id
        days = HashMap()
        val f = file(ctx, id)
        if (id.isEmpty() || !f.exists()) return
        try {
            val root = JSONObject(f.readText())
            for (key in root.keys()) {
                val d = root.getJSONObject(key)
                val t = d.getJSONArray("t")
                days[key] = Day(List(6) { t.getString(it) }, d.optString("h"), d.optString("hl"))
            }
        } catch (e: Exception) {
            days = HashMap() // bozuk dosya: formüle düşülür, sonraki çekimde yenilenir
        }
    }

    @Synchronized
    fun day(ctx: Context, date: LocalDate): Day? {
        load(ctx)
        return days[date.toString()]
    }

    fun times(ctx: Context, date: LocalDate): List<ZonedDateTime>? =
        day(ctx, date)?.times?.map { LocalTime.parse(it).atDate(date).atZone(Today.ZONE) }

    /** Elimizdeki tablo bir haftadan kısa sürede bitiyorsa yenilemek gerekir. */
    fun needsRefresh(ctx: Context) = day(ctx, LocalDate.now(Today.ZONE).plusDays(7)) == null

    /**
     * Seçili ilçenin tablosunu ağdan çeker ve saklar. İlçe henüz seçilmemişse il
     * merkezini bulup seçer. Ağ çağrısı yapar: ana iş parçacığında çağrılmaz.
     */
    fun fetch(ctx: Context): Boolean = try {
        var id = Store.districtId(ctx)
        if (id.isEmpty()) {
            val city = Store.city(ctx)
            val list = districts(Cities.apiId(city))
            val center = list.firstOrNull { it.second.equals(city.name, ignoreCase = true) } ?: list.first()
            Store.setDistrict(ctx, center.first, center.second)
            id = center.first
        }
        val rows = JSONArray(get("$BASE/vakitler/$id"))
        val fresh = HashMap<String, Day>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val t = listOf("Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi").map { r.getString(it) }
            if (t.any { !HM.matches(it) }) continue
            val date = LocalDate.parse(r.getString("MiladiTarihKisa"), API_DATE)
            fresh[date.toString()] = Day(t, r.optString("HicriTarihKisa"), r.optString("HicriTarihUzun"))
        }
        if (fresh.isEmpty()) {
            false
        } else {
            synchronized(this) {
                // Kullanıcı bu arada ilçe değiştirdiyse eski isteğin sonucunu yazma.
                if (Store.districtId(ctx) == id) {
                    load(ctx)
                    val cutoff = LocalDate.now(Today.ZONE).minusDays(2).toString()
                    days.keys.removeAll { it < cutoff }
                    days.putAll(fresh)
                    val root = JSONObject()
                    for ((key, d) in days) {
                        root.put(key, JSONObject().put("t", JSONArray(d.times)).put("h", d.hijri).put("hl", d.hijriLong))
                    }
                    file(ctx, id).writeText(root.toString())
                }
            }
            true
        }
    } catch (e: Exception) {
        false
    }

    /** Bir ilin ilçeleri: (kimlik, ad). Ağ çağrısı yapar; hata fırlatabilir. */
    fun districts(cityApiId: Int): List<Pair<String, String>> {
        val rows = JSONArray(get("$BASE/ilceler/$cityApiId"))
        return List(rows.length()) {
            val r = rows.getJSONObject(it)
            r.getString("IlceID") to titleCase(r.getString("IlceAdi"))
        }
    }

    /** "KAYSERİ" -> "Kayseri" (Türkçe büyük/küçük harf kurallarıyla). */
    private fun titleCase(s: String) = s.lowercase(Today.TR).split(' ').joinToString(" ") { w ->
        w.replaceFirstChar { it.titlecase(Today.TR) }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
