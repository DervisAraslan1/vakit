package dev.vakit.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Konumdan il ve Diyanet ilçesini bulur. Kaba konum yeter; GPS gerekmez. */
object Locator {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun providers(ctx: Context): List<String> {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    /** Telefonun konum servisi açık mı. */
    fun enabled(ctx: Context) = providers(ctx).isNotEmpty()

    /**
     * Konumu verir: yarım saatten yeni bilinen konum varsa onu, yoksa taze bir
     * konum ister (en fazla 15 sn). [done] tam bir kez, ana iş parçacığında çağrılır.
     */
    @Suppress("MissingPermission")
    fun current(ctx: Context, done: (Location?) -> Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val once = AtomicBoolean(false)
        fun finish(loc: Location?) {
            if (once.compareAndSet(false, true)) done(loc)
        }
        try {
            val last = (providers(ctx) + LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000L) return finish(last)
            val provider = providers(ctx).firstOrNull() ?: return finish(last)
            val main = Handler(Looper.getMainLooper())
            if (Build.VERSION.SDK_INT >= 30) {
                val cancel = CancellationSignal()
                lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { finish(it ?: last) }
                main.postDelayed({
                    cancel.cancel()
                    finish(last)
                }, 15_000)
            } else {
                // Eski sürümlerde arayüzün tüm metotları uygulanmalı, yoksa çalışırken çöker.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        finish(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                main.postDelayed({
                    lm.removeUpdates(listener)
                    finish(last)
                }, 15_000)
            }
        } catch (e: SecurityException) {
            finish(null)
        }
    }

    /**
     * Koordinatı (il, (ilçe kimliği, ilçe adı)) olarak çözer; Türkiye dışındaysa null.
     * İlçe bulunamazsa ikinci değer null olur ve il merkezi kullanılır. Ağ çağrısı yapar.
     */
    fun resolve(ctx: Context, lat: Double, lng: Double): Pair<City, Pair<String, String>?>? {
        val nearest = Cities.ALL.minBy { distanceKm(lat, lng, it.lat, it.lng) }
        if (distanceKm(lat, lng, nearest.lat, nearest.lng) > 250) return null

        var city = nearest
        var sub: String? = null
        try {
            @Suppress("DEPRECATION")
            val a = Geocoder(ctx, Today.TR).getFromLocation(lat, lng, 1)?.firstOrNull()
            if (a != null) {
                Cities.ALL.firstOrNull { fold(it.name) == fold(a.adminArea ?: "") }?.let { city = it }
                sub = a.subAdminArea ?: a.locality
            }
        } catch (e: Exception) {
            // Adres servisi yok ya da ağ yok: en yakın il merkezi yeter.
        }

        val district = try {
            val list = Diyanet.districts(Cities.apiId(city))
            // "Melikgazi" gibi merkez ilçeler Diyanet'te ayrı yer almaz; il merkezine düşer.
            sub?.let { s -> list.firstOrNull { fold(it.second) == fold(s) } }
                ?: list.firstOrNull { fold(it.second) == fold(city.name) }
        } catch (e: Exception) {
            null
        }
        return city to district
    }

    /** "İstanbul", "Istanbul" ve "İstanbul İli" aynı sayılsın diye harfleri sadeleştirir. */
    private fun fold(s: String) = s.lowercase(Today.TR)
        .replace(" ili", "").replace(" ilçesi", "").replace(" province", "").replace(" district", "")
        .map {
            when (it) {
                'ı', 'î' -> 'i'
                'ş' -> 's'
                'ğ' -> 'g'
                'ü', 'û' -> 'u'
                'ö' -> 'o'
                'ç' -> 'c'
                'â' -> 'a'
                else -> it
            }
        }
        .joinToString("")
        .trim()

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        fun rad(d: Double) = Math.toRadians(d)
        val h = sin(rad(lat2 - lat1) / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(rad(lng2 - lng1) / 2).pow(2)
        return 2 * 6371 * asin(sqrt(h))
    }
}
