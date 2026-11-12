package dev.vakit.widget

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.time.ZonedDateTime
import kotlin.math.ceil

/** Widget yenileme, bildirim ve sistem olayları (açılış, saat değişimi) için tek alıcı. */
class VakitReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Resmi tablo bitmek üzereyse arka planda tazele; uygulama hiç açılmasa da güncel kalsın.
        if (intent.action != Notifier.ACTION && Diyanet.needsRefresh(ctx)) {
            val pending = goAsync()
            Thread {
                try {
                    if (Diyanet.fetch(ctx)) {
                        Widgets.updateAll(ctx)
                        Notifier.schedule(ctx)
                    }
                } finally {
                    pending.finish()
                }
            }.start()
        }
        when (intent.action) {
            Widgets.ACTION_TICK -> {
                Widgets.updateAll(ctx)
                Notifier.schedule(ctx)
            }
            Notifier.ACTION -> Notifier.fire(ctx, intent)
            else -> {
                // Açılış, saat/saat dilimi değişimi, güncelleme: eski alarm zamanı artık
                // anlamsız; "kaçtı" sayılmasın diye sıfırla ve her şeyi yeniden kur.
                Store.setPendingAt(ctx, 0)
                Widgets.updateAll(ctx)
                Notifier.schedule(ctx)
            }
        }
    }
}

/**
 * Vakit bildirimleri. Aynı anda yalnızca sıradaki tek olay için alarm kurulur;
 * o çalınca bir sonraki kurulur.
 */
object Notifier {
    const val ACTION = "dev.vakit.widget.NOTIFY"

    /** Alarmın bu kadar gecikmesi normal sayılır; fazlası "kaçtı" demektir. */
    private const val GRACE_MS = 120_000L

    private class Event(val at: ZonedDateTime, val prayer: Int, val pre: Boolean, val time: ZonedDateTime)

    private fun pending(ctx: Context, ev: Event?) = PendingIntent.getBroadcast(
        ctx, 1,
        Intent(ctx, VakitReceiver::class.java).setAction(ACTION).apply {
            if (ev != null) {
                putExtra("prayer", ev.prayer)
                putExtra("pre", ev.pre)
                putExtra("time", ev.time.toInstant().toEpochMilli())
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    private fun nextEvent(ctx: Context): Event? {
        val now = ZonedDateTime.now(Today.ZONE)
        val lead = Store.lead(ctx).toLong()
        val atTime = Store.atTime(ctx)
        val events = ArrayList<Event>()
        for (day in 0L..1L) {
            val times = Today.timesFor(ctx, now.toLocalDate().plusDays(day))
            for (i in 0..5) {
                if (!Store.prayerOn(ctx, i)) continue
                events += Event(times[i].minusMinutes(lead), i, true, times[i])
                if (atTime) events += Event(times[i], i, false, times[i])
            }
        }
        return events.filter { it.at.isAfter(now) }.minByOrNull { it.at.toInstant() }
    }

    /** Ayarlara göre sıradaki bildirimin alarmını kurar ya da kaldırır. Tekrar çağrılması zararsızdır. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val nowMs = System.currentTimeMillis()
        if (!Store.notif(ctx) || !canPost(ctx)) {
            am.cancel(pending(ctx, null))
            Store.setPendingAt(ctx, 0)
            return
        }
        val pendingAt = Store.pendingAt(ctx)
        // Zamanı yeni gelmiş, birazdan çalacak alarmı ezme.
        if (pendingAt in (nowMs - GRACE_MS)..nowMs) return
        // Zamanı çoktan geçmiş ama hiç çalmamış: sistem uygulamayı engellemiş.
        if (pendingAt in 1 until nowMs - GRACE_MS) Store.setMissed(ctx, true)

        val ev = nextEvent(ctx) ?: return
        val at = ev.at.toInstant().toEpochMilli()
        Store.setPendingAt(ctx, at)
        Widgets.setExact(am, at, pending(ctx, ev))
    }

    fun fire(ctx: Context, intent: Intent) {
        Store.setPendingAt(ctx, 0)
        val prayer = intent.getIntExtra("prayer", -1)
        val pre = intent.getBooleanExtra("pre", false)
        val time = intent.getLongExtra("time", 0)
        val now = System.currentTimeMillis()
        // Çok geç çalan alarm yanıltıcı bildirim göstermesin.
        val fresh = if (pre) now < time else now < time + 20 * 60_000
        if (prayer in 0..5 && fresh && canPost(ctx)) post(ctx, prayer, pre, time, now)
        schedule(ctx)
    }

    /** Sıradaki vaktin "vakit girdi" bildirimini hemen gösterir: sesi ve görünümü denemek için. */
    fun test(ctx: Context) {
        val st = Today.of(ctx)
        post(ctx, st.next, false, st.nextTime.toInstant().toEpochMilli(), System.currentTimeMillis(), test = true)
    }

    private fun post(ctx: Context, prayer: Int, pre: Boolean, time: Long, now: Long, test: Boolean = false) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        Sounds.ensureChannels(ctx)
        val at = java.time.Instant.ofEpochMilli(time).atZone(Today.ZONE)
        val hm = at.format(Today.HM)
        val city = Store.districtName(ctx).ifEmpty { Store.city(ctx).name }
        val ramadan = Today.isRamadan(ctx, at.toLocalDate())
        val minutes = ceil((time - now) / 60_000.0).toInt()
        val title = when {
            pre && ramadan && prayer == 0 -> "Sahurun bitmesine $minutes dakika kaldı"
            pre -> "${Today.label(prayer, ramadan)} $minutes dakika kaldı"
            ramadan && prayer == 4 -> "İftar vakti, hayırlı iftarlar"
            ramadan && prayer == 0 -> "İmsak vakti, oruç başladı"
            prayer == 1 -> "Güneş doğdu"
            else -> "${PrayerCalc.NAMES[prayer]} vakti girdi"
        }
        val channel = if (pre) Sounds.REMIND else Sounds.atTimeChannel(ctx)
        val builder = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (test) "Deneme · $title" else title)
            .setContentText(if (pre) "${PrayerCalc.NAMES[prayer]} $hm · $city" else "$hm · $city")
            .setColor(0xFF2E6CCB.toInt())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        // Hatırlatma, vakit girdikten bir süre sonra kendiliğinden kalkar.
        if (pre) builder.setTimeoutAfter(time - now + 20 * 60_000)
        // Aynı vaktin hatırlatması ile "girdi" bildirimi aynı kimliği paylaşır: biri diğerinin yerini alır.
        nm.notify(100 + prayer, builder.build())
    }
}

/** Sistemin uygulamayı arka planda engellemesine karşı tek dokunuşluk çözümler. */
object Reliability {
    fun batteryOk(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Sistemin kendi "pil kısıtlamasını kaldır" onay penceresini açar. */
    fun askBattery(a: Activity) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}")),
            )
        } catch (e: Exception) {
            openAppSettings(a)
        }
    }

    /** Ayrıca "otomatik başlatma" izni isteyen üreticiler. */
    val needsAutostart = Build.MANUFACTURER.lowercase() in
        setOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "huawei", "honor", "oneplus")

    private val AUTOSTART = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
    )

    /** Üreticinin otomatik başlatma sayfasını açar; yoksa uygulamanın ayar sayfasını. */
    fun openAutostart(a: Activity) {
        for ((pkg, cls) in AUTOSTART) {
            try {
                a.startActivity(Intent().setComponent(ComponentName(pkg, cls)))
                return
            } catch (e: Exception) {
                // Bu cihazda yok; sıradakini dene.
            }
        }
        openAppSettings(a)
    }

    private fun openAppSettings(a: Activity) {
        a.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")),
        )
    }
}
