package dev.vakit.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * Flutter arayüzünün kabuğu. Vakit verisi, ayarlar, widget'lar ve bildirimler
 * yerel tarafta yaşar; Dart bunlara "vakit/native" kanalından ulaşır.
 */
class MainActivity : FlutterActivity() {
    private var permissionResult: MethodChannel.Result? = null
    private var locateResult: MethodChannel.Result? = null
    private var soundResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "vakit/compass")
            .setStreamHandler(CompassStream(applicationContext))
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "vakit/native")
            .setMethodCallHandler { call, result ->
                try {
                    handle(call, result)
                } catch (e: Exception) {
                    result.error("native", e.message, null)
                }
            }
    }

    /** Ayar değişince widget'lar ve bildirim alarmı yeni duruma göre kurulur. */
    private fun applied() {
        Widgets.updateAll(this)
        Notifier.schedule(this)
    }

    /** Ağ gerektiren işi arka planda çalıştırır, sonucu ana iş parçacığından döner. */
    private fun <T> background(result: MethodChannel.Result, work: () -> T) {
        thread {
            val value = try {
                Result.success(work())
            } catch (e: Exception) {
                Result.failure(e)
            }
            runOnUiThread {
                value.fold({ result.success(it) }, { result.error("network", it.message, null) })
            }
        }
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "state" -> result.success(state())

            "days" -> {
                val start = LocalDate.now(Today.ZONE)
                result.success(List(call.argument<Int>("count") ?: 30) { i ->
                    val date = start.plusDays(i.toLong())
                    val official = Diyanet.day(this, date)
                    mapOf(
                        "date" to date.toString(),
                        "times" to Today.timesFor(this, date).map { it.format(Today.HM) },
                        "hijri" to (official?.hijri ?: ""),
                        "hijriLong" to (official?.hijriLong ?: ""),
                        "official" to (official != null),
                    )
                })
            }

            "refresh" -> background(result) {
                val ok = if (Diyanet.needsRefresh(this)) Diyanet.fetch(this) else true
                runOnUiThread { applied() }
                ok
            }

            "districts" -> {
                val city = Cities.ALL.first { it.name == call.argument<String>("city") }
                background(result) {
                    Diyanet.districts(Cities.apiId(city)).map { mapOf("id" to it.first, "name" to it.second) }
                }
            }

            "locate" -> {
                val silent = call.argument<Boolean>("silent") ?: false
                // Sessiz mod açılışta çağrılır: izin istemez, sık sık konum sormaz.
                if (silent && (!Store.autoLocation(this) || !Locator.hasPermission(this) ||
                        System.currentTimeMillis() - Store.lastLocate(this) < 3 * 3600_000L)
                ) {
                    result.success(mapOf("ok" to false))
                } else if (Locator.hasPermission(this)) {
                    locate(result)
                } else {
                    locateResult = result
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 2)
                }
            }

            "setLocation" -> {
                val city = Cities.ALL.first { it.name == call.argument<String>("city") }
                Store.setAutoLocation(this, false) // elle seçildi: konum artık ezmesin
                Store.setCity(this, city)
                val id = call.argument<String>("districtId")
                if (id != null) Store.setDistrict(this, id, call.argument<String>("districtName") ?: city.name)
                background(result) {
                    val ok = Diyanet.fetch(this)
                    runOnUiThread { applied() }
                    ok
                }
            }

            "set" -> {
                val value = call.argument<Any>("value")
                when (call.argument<String>("key")) {
                    "mosque" -> Store.setMosque(this, MosqueArt.byId(value as String))
                    "lead" -> Store.setLead(this, value as Int)
                    "atTime" -> Store.setAtTime(this, value as Boolean)
                    "notif" -> Store.setNotif(this, value as Boolean)
                    "prayerOn" -> Store.setPrayerOn(this, call.argument<Int>("index")!!, value as Boolean)
                    "missed" -> Store.setMissed(this, value as Boolean)
                    "autoLocation" -> Store.setAutoLocation(this, value as Boolean)
                }
                applied()
                result.success(null)
            }

            "enableNotifications" -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionResult = result
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    result.success(notificationsAllowed())
                }
            }

            "pickSound" -> {
                soundResult = result
                if (call.argument<String>("kind") == "file") {
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),
                        PICK_FILE,
                    )
                } else {
                    val current = Store.soundUri(this).takeIf { Store.soundMode(this) == "custom" }
                    startActivityForResult(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Vakit girince çalacak ses")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse)),
                        PICK_SYSTEM,
                    )
                }
            }

            "setSound" -> {
                Sounds.set(this, call.argument<String>("mode") ?: "default")
                result.success(null)
            }

            "testNotification" -> {
                Notifier.test(this)
                result.success(null)
            }

            "fixBattery" -> {
                Reliability.askBattery(this)
                result.success(null)
            }

            "fixAutostart" -> {
                Store.setMissed(this, false)
                Reliability.openAutostart(this)
                result.success(null)
            }

            "getString" -> result.success(Store.raw(this, call.argument<String>("key")!!))

            "setString" -> {
                Store.setRaw(this, call.argument<String>("key")!!, call.argument<String>("value")!!)
                result.success(null)
            }

            "mosqueImage" -> result.success(png(MosqueArt.sprite(
                call.argument<Int>("size") ?: 600,
                MosqueArt.byId(call.argument<String>("id")),
                call.argument<Boolean>("night") ?: false,
            )))

            "widgetPreview" -> result.success(widgetPreview(Kind.valueOf(call.argument<String>("kind")!!)))

            "pinWidget" -> result.success(pinWidget(Kind.valueOf(call.argument<String>("kind")!!)))

            else -> result.notImplemented()
        }
    }

    private fun state(): Map<String, Any?> {
        val st = Today.of(this)
        val today = st.now.toLocalDate()
        val official = Diyanet.day(this, today)
        // İçinde bulunulan vaktin başlangıcı (ilerleme çubuğu için); imsaktan önce dünün yatsısıdır.
        val currentStart = when {
            st.afterIsha -> st.times[5]
            st.next == 0 -> Today.timesFor(this, today.minusDays(1))[5]
            else -> st.times[st.next - 1]
        }
        val notif = Store.notif(this)
        return mapOf(
            "city" to st.city.name,
            "district" to Store.districtName(this),
            "districtId" to Store.districtId(this),
            "autoLocation" to Store.autoLocation(this),
            // Kıble için: konum açıksa son bulunan nokta, değilse il merkezi.
            "lat" to (Store.coords(this)?.takeIf { Store.autoLocation(this) }?.first ?: st.city.lat),
            "lng" to (Store.coords(this)?.takeIf { Store.autoLocation(this) }?.second ?: st.city.lng),
            "coordsExact" to (Store.autoLocation(this) && Store.coords(this) != null),
            "ramadan" to st.ramadan,
            "ramadanDay" to st.ramadanDay,
            "nextLabel" to st.nextLabel,
            "soundMode" to Store.soundMode(this),
            "soundName" to Store.soundName(this),
            "canPickFile" to (Build.VERSION.SDK_INT >= 29),
            "mosque" to Store.mosque(this).id,
            "night" to st.night,
            "official" to (official != null),
            "hijri" to (official?.hijriLong ?: ""),
            "date" to st.now.format(Today.DATE),
            "now" to st.now.toInstant().toEpochMilli(),
            "next" to st.next,
            "current" to st.current,
            "afterIsha" to st.afterIsha,
            "nextTime" to st.nextTime.toInstant().toEpochMilli(),
            "currentStart" to currentStart.toInstant().toEpochMilli(),
            "times" to st.times.map { it.toInstant().toEpochMilli() },
            "timeTexts" to st.times.map { it.format(Today.HM) },
            "nextText" to st.nextTime.format(Today.HM),
            "notif" to notif,
            "prayerOn" to List(6) { Store.prayerOn(this, it) },
            "lead" to Store.lead(this),
            "atTime" to Store.atTime(this),
            "canPost" to Notifier.canPost(this),
            "batteryOk" to Reliability.batteryOk(this),
            "missed" to Store.missed(this),
            "needsAutostart" to Reliability.needsAutostart,
            "mosques" to MosqueArt.ALL.map { mapOf("id" to it.id, "name" to it.name) },
            "cities" to Cities.ALL.map { it.name },
        )
    }

    /** İzin alındıktan sonra: bildirimleri aç ve sistemin pil onay penceresini göster. */
    private fun notificationsAllowed(): Boolean {
        Store.setNotif(this, true)
        if (!Reliability.batteryOk(this)) Reliability.askBattery(this)
        applied()
        return true
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        val granted = results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (code) {
            1 -> {
                val result = permissionResult ?: return
                permissionResult = null
                result.success(if (granted) notificationsAllowed() else false)
            }
            2 -> {
                val result = locateResult ?: return
                locateResult = null
                if (granted) locate(result) else result.success(mapOf("ok" to false, "error" to "denied"))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SYSTEM && requestCode != PICK_FILE) return
        val result = soundResult ?: return
        soundResult = null
        if (resultCode != Activity.RESULT_OK || data == null) return result.success(null)

        if (requestCode == PICK_SYSTEM) {
            @Suppress("DEPRECATION")
            val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // "Varsayılan" seçilirse adres boş ya da varsayılan bildirim adresi gelir.
            if (uri == null || RingtoneManager.isDefault(uri)) Sounds.set(this, "default")
            else Sounds.set(this, "custom", uri, Sounds.title(this, uri))
            result.success(Store.soundName(this))
            return
        }

        val src = data.data ?: return result.success(null)
        thread {
            val reply = runCatching { Sounds.importFile(this, src) }
            runOnUiThread {
                reply.fold(
                    { (uri, name) ->
                        Sounds.set(this, "custom", uri, name)
                        result.success(name)
                    },
                    { result.error("sound", it.message, null) },
                )
            }
        }
    }

    /** Konumu alır, il/ilçeyi çözer, değiştiyse yeni vakitleri çeker. */
    private fun locate(result: MethodChannel.Result) {
        if (!Locator.enabled(this)) {
            result.success(mapOf("ok" to false, "error" to "disabled"))
            return
        }
        Locator.current(this) { loc ->
            if (loc == null) {
                result.success(mapOf("ok" to false, "error" to "nolocation"))
                return@current
            }
            thread {
                val found = Locator.resolve(this, loc.latitude, loc.longitude)
                val reply: Map<String, Any?> = if (found == null) {
                    mapOf("ok" to false, "error" to "abroad")
                } else {
                    val (city, district) = found
                    val changed = city.name != Store.city(this).name ||
                        (district != null && district.first != Store.districtId(this))
                    if (changed) {
                        Store.setCity(this, city)
                        if (district != null) Store.setDistrict(this, district.first, district.second)
                        Diyanet.fetch(this)
                    }
                    Store.setAutoLocation(this, true)
                    Store.setLastLocate(this, System.currentTimeMillis())
                    Store.setCoords(this, loc.latitude, loc.longitude)
                    val name = district?.second?.takeIf { it != city.name }?.let { "$it, ${city.name}" } ?: city.name
                    mapOf("ok" to true, "changed" to changed, "place" to name)
                }
                runOnUiThread {
                    applied()
                    result.success(reply)
                }
            }
        }
    }

    private fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    /** Gerçek widget düzenini ekran dışında çizip PNG olarak verir (köşe yuvarlamasını Dart yapar). */
    private fun widgetPreview(kind: Kind): ByteArray {
        val density = resources.displayMetrics.density
        val w = (kind.w * density).toInt()
        val h = (kind.h * density).toInt()
        val view = Widgets.build(this, kind, kind.w, kind.h, clickable = false).apply(applicationContext, FrameLayout(this))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return png(bmp)
    }

    private fun pinWidget(kind: Kind): Boolean {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        val extras = Bundle().apply {
            putParcelable(
                AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                Widgets.build(this@MainActivity, kind, kind.w, kind.h, clickable = false),
            )
        }
        return mgr.requestPinAppWidget(ComponentName(this, kind.provider), extras, null)
    }

    private companion object {
        const val PICK_SYSTEM = 10
        const val PICK_FILE = 11
    }
}
