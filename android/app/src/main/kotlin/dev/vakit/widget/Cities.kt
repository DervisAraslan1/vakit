package dev.vakit.widget

import android.content.Context

class City(val name: String, val lat: Double, val lng: Double)

/** 81 il merkezi. */
object Cities {
    val ALL = listOf(
        City("Adana", 37.00, 35.32), City("Adıyaman", 37.76, 38.28), City("Afyonkarahisar", 38.76, 30.54),
        City("Ağrı", 39.72, 43.05), City("Aksaray", 38.37, 34.03), City("Amasya", 40.65, 35.83),
        City("Ankara", 39.93, 32.86), City("Antalya", 36.89, 30.71), City("Ardahan", 41.11, 42.70),
        City("Artvin", 41.18, 41.82), City("Aydın", 37.85, 27.85), City("Balıkesir", 39.65, 27.88),
        City("Bartın", 41.64, 32.34), City("Batman", 37.88, 41.13), City("Bayburt", 40.26, 40.22),
        City("Bilecik", 40.14, 29.98), City("Bingöl", 38.88, 40.50), City("Bitlis", 38.40, 42.11),
        City("Bolu", 40.73, 31.61), City("Burdur", 37.72, 30.29), City("Bursa", 40.19, 29.06),
        City("Çanakkale", 40.15, 26.41), City("Çankırı", 40.60, 33.62), City("Çorum", 40.55, 34.95),
        City("Denizli", 37.78, 29.09), City("Diyarbakır", 37.91, 40.24), City("Düzce", 40.84, 31.16),
        City("Edirne", 41.68, 26.56), City("Elazığ", 38.67, 39.22), City("Erzincan", 39.75, 39.49),
        City("Erzurum", 39.90, 41.27), City("Eskişehir", 39.78, 30.52), City("Gaziantep", 37.07, 37.38),
        City("Giresun", 40.91, 38.39), City("Gümüşhane", 40.46, 39.48), City("Hakkâri", 37.57, 43.74),
        City("Hatay", 36.20, 36.16), City("Iğdır", 39.92, 44.04), City("Isparta", 37.76, 30.55),
        City("İstanbul", 41.01, 28.98), City("İzmir", 38.42, 27.14), City("Kahramanmaraş", 37.58, 36.93),
        City("Karabük", 41.20, 32.62), City("Karaman", 37.18, 33.22), City("Kars", 40.60, 43.10),
        City("Kastamonu", 41.38, 33.78), City("Kayseri", 38.73, 35.48), City("Kilis", 36.72, 37.12),
        City("Kırıkkale", 39.85, 33.51), City("Kırklareli", 41.73, 27.22), City("Kırşehir", 39.15, 34.16),
        City("Kocaeli", 40.77, 29.92), City("Konya", 37.87, 32.48), City("Kütahya", 39.42, 29.98),
        City("Malatya", 38.35, 38.31), City("Manisa", 38.61, 27.43), City("Mardin", 37.31, 40.74),
        City("Mersin", 36.80, 34.63), City("Muğla", 37.22, 28.36), City("Muş", 38.73, 41.49),
        City("Nevşehir", 38.62, 34.71), City("Niğde", 37.97, 34.68), City("Ordu", 40.98, 37.88),
        City("Osmaniye", 37.07, 36.25), City("Rize", 41.02, 40.52), City("Sakarya", 40.76, 30.40),
        City("Samsun", 41.29, 36.33), City("Şanlıurfa", 37.16, 38.79), City("Siirt", 37.93, 41.94),
        City("Sinop", 42.03, 35.15), City("Sivas", 39.75, 37.02), City("Şırnak", 37.52, 42.46),
        City("Tekirdağ", 40.98, 27.51), City("Tokat", 40.31, 36.55), City("Trabzon", 41.00, 39.72),
        City("Tunceli", 39.11, 39.55), City("Uşak", 38.68, 29.41), City("Van", 38.49, 43.38),
        City("Yalova", 40.65, 29.27), City("Yozgat", 39.82, 34.81), City("Zonguldak", 41.45, 31.79),
    )
}

/** Diyanet servisindeki il kimliği: 500'den başlayıp bu listeyle aynı sırada gider, tek istisna Sivas/Şırnak. */
fun Cities.apiId(city: City) = when (city.name) {
    "Sivas" -> 571
    "Şırnak" -> 570
    else -> 500 + ALL.indexOfFirst { it.name == city.name }
}

/** Kullanıcı seçimleri: şehir adı ve cami kimliği. */
object Store {
    private const val DEFAULT_CITY = "İstanbul"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("vakit", Context.MODE_PRIVATE)

    fun city(ctx: Context): City {
        val name = prefs(ctx).getString("city", DEFAULT_CITY)
        return Cities.ALL.firstOrNull { it.name == name } ?: Cities.ALL.first { it.name == DEFAULT_CITY }
    }

    /** Şehri değiştirir; ilçe seçimi sıfırlanır (ilk veri çekiminde il merkezi seçilir). */
    fun setCity(ctx: Context, city: City) =
        prefs(ctx).edit().putString("city", city.name).remove("districtId").remove("districtName").apply()

    /** Diyanet ilçe kimliği; boşsa henüz seçilmemiştir. */
    fun districtId(ctx: Context) = prefs(ctx).getString("districtId", "") ?: ""
    fun districtName(ctx: Context) = prefs(ctx).getString("districtName", "") ?: ""
    fun setDistrict(ctx: Context, id: String, name: String) =
        prefs(ctx).edit().putString("districtId", id).putString("districtName", name).apply()

    /** Flutter tarafının serbest verisi (ör. zikirmatik sayaçları). */
    fun raw(ctx: Context, key: String) = prefs(ctx).getString("x_$key", null)
    fun setRaw(ctx: Context, key: String, value: String) = prefs(ctx).edit().putString("x_$key", value).apply()

    /** Konum açıkken il/ilçe uygulama her açıldığında (en fazla 3 saatte bir) konumdan güncellenir. */
    fun autoLocation(ctx: Context) = prefs(ctx).getBoolean("autoLocation", false)
    fun setAutoLocation(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("autoLocation", on).apply()

    /** Son bulunan konum; kıble açısı için. Yoksa il merkezi kullanılır. */
    fun coords(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        if (!p.contains("lat")) return null
        return p.getFloat("lat", 0f).toDouble() to p.getFloat("lng", 0f).toDouble()
    }

    fun setCoords(ctx: Context, lat: Double, lng: Double) =
        prefs(ctx).edit().putFloat("lat", lat.toFloat()).putFloat("lng", lng.toFloat()).apply()

    /** "Vakit girdi" sesi: default, silent ya da custom. */
    fun soundMode(ctx: Context) = prefs(ctx).getString("soundMode", "default") ?: "default"
    fun soundUri(ctx: Context) = prefs(ctx).getString("soundUri", "") ?: ""
    fun soundName(ctx: Context) = prefs(ctx).getString("soundName", "") ?: ""
    fun setSound(ctx: Context, mode: String, uri: String, name: String) =
        prefs(ctx).edit().putString("soundMode", mode).putString("soundUri", uri).putString("soundName", name).apply()

    fun channelVersion(ctx: Context) = prefs(ctx).getInt("channelVersion", 1)
    fun bumpChannel(ctx: Context) = prefs(ctx).edit().putInt("channelVersion", channelVersion(ctx) + 1).apply()

    fun lastLocate(ctx: Context) = prefs(ctx).getLong("lastLocate", 0)
    fun setLastLocate(ctx: Context, at: Long) = prefs(ctx).edit().putLong("lastLocate", at).apply()

    fun mosque(ctx: Context): Mosque = MosqueArt.byId(prefs(ctx).getString("mosque", null))

    fun setMosque(ctx: Context, mosque: Mosque) = prefs(ctx).edit().putString("mosque", mosque.id).apply()

    // --- Bildirim ayarları ---

    /** Ana anahtar; bildirim izni alınınca açılır. */
    fun notif(ctx: Context) = prefs(ctx).getBoolean("notif", false)
    fun setNotif(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("notif", on).apply()

    /** Vakit bazında bildirim; Güneş varsayılan olarak kapalı. */
    fun prayerOn(ctx: Context, i: Int) = prefs(ctx).getBoolean("prayer$i", i != 1)
    fun setPrayerOn(ctx: Context, i: Int, on: Boolean) = prefs(ctx).edit().putBoolean("prayer$i", on).apply()

    /** Vakitten kaç dakika önce hatırlatılacağı. */
    fun lead(ctx: Context) = prefs(ctx).getInt("lead", 15)
    fun setLead(ctx: Context, minutes: Int) = prefs(ctx).edit().putInt("lead", minutes).apply()

    fun atTime(ctx: Context) = prefs(ctx).getBoolean("atTime", true)
    fun setAtTime(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("atTime", on).apply()

    /** Kurulu bildirim alarmının zamanı (epoch ms); 0 = yok. Kaçan bildirimi fark etmek için. */
    fun pendingAt(ctx: Context) = prefs(ctx).getLong("pendingAt", 0)
    fun setPendingAt(ctx: Context, at: Long) = prefs(ctx).edit().putLong("pendingAt", at).apply()

    fun missed(ctx: Context) = prefs(ctx).getBoolean("missed", false)
    fun setMissed(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("missed", on).apply()
}
