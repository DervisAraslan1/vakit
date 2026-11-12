package dev.vakit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.Duration
import kotlin.math.min

/** Widget boyutları. Her biri ayrı bir sağlayıcıdır ki ana ekranda ayrı ayrı seçilebilsin. */
enum class Kind(
    val title: String,
    val layout: Int,
    /** Ana ekran boyut bildirmezse kullanılacak dp ölçüleri. */
    val w: Int,
    val h: Int,
    val provider: Class<out AppWidgetProvider>,
    val scene: MosqueArt.Scene,
    /** Gündüz temasında yazı lacivert mi (açık gökyüzü üstünde) yoksa hep beyaz mı. */
    val navyByDay: Boolean,
) {
    LARGE("4×2", R.layout.widget, 320, 150, VakitWidget::class.java, MosqueArt.Scene.WIDE, true),
    WIDE("4×1", R.layout.widget_4x1, 320, 70, Widget4x1::class.java, MosqueArt.Scene.PLAIN, false),
    SQUARE("2×2", R.layout.widget_2x2, 155, 155, Widget2x2::class.java, MosqueArt.Scene.SQUARE, true),
    SMALL("2×1", R.layout.widget_2x1, 155, 70, Widget2x1::class.java, MosqueArt.Scene.ORB, false),
    TINY("1×1", R.layout.widget_1x1, 70, 70, Widget1x1::class.java, MosqueArt.Scene.TINY, false),
}

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.updateAll(ctx)

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        Widgets.updateAll(ctx)
}

class VakitWidget : BaseWidget()
class Widget4x1 : BaseWidget()
class Widget2x2 : BaseWidget()
class Widget2x1 : BaseWidget()
class Widget1x1 : BaseWidget()

object Widgets {
    const val ACTION_TICK = "dev.vakit.widget.TICK"

    private val NAME_IDS = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5)
    private val TIME_IDS = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5)

    private const val HIGHLIGHT = 0xFFFFD772.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val NAVY = 0xFF102A52.toInt()

    fun build(ctx: Context, kind: Kind, widthDp: Int, heightDp: Int, clickable: Boolean = true): RemoteViews {
        val st = Today.of(ctx)
        val views = RemoteViews(ctx.packageName, kind.layout)

        // Arka plan bitmap'i widget'ın gerçek boyutunda, genişlik 1000 px ile sınırlı.
        val density = ctx.resources.displayMetrics.density
        val eff = density * min(1f, 1000f / (widthDp * density))
        views.setImageViewBitmap(
            R.id.bg,
            MosqueArt.widget(
                (widthDp * eff).toInt().coerceAtLeast(1), (heightDp * eff).toInt().coerceAtLeast(1),
                Store.mosque(ctx), st.night, eff, kind.scene,
            ),
        )

        val main = if (st.night || !kind.navyByDay) WHITE else NAVY
        val soft = (main and 0x00FFFFFF) or (0xB3 shl 24)

        // 1×1'e geri sayım sığmaz: vakit adı ve saati gösterilir.
        views.setTextColor(R.id.label, main)
        views.setTextViewText(
            R.id.label,
            when {
                kind != Kind.TINY -> "${st.nextLabel} kalan"
                st.ramadan && st.next == 4 -> "İftar"
                st.ramadan && st.next == 0 -> "Sahur"
                else -> PrayerCalc.NAMES[st.next]
            },
        )

        if (kind == Kind.LARGE) {
            views.setTextColor(R.id.date, soft)
            views.setTextViewText(R.id.date, st.now.format(Today.DATE))
        }

        if (kind != Kind.TINY) {
            // Geri sayımı sistem kendisi saniye saniye işletir; bizim yenilememiz gerekmez.
            val remaining = Duration.between(st.now, st.nextTime).toMillis()
            views.setTextColor(R.id.countdown, main)
            views.setChronometerCountDown(R.id.countdown, true)
            views.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, null, true)
        }

        when (kind) {
            Kind.LARGE, Kind.SQUARE -> {
                views.setTextColor(R.id.target, soft)
                views.setTextViewText(R.id.target, st.targetText)
            }
            Kind.TINY -> {
                views.setTextColor(R.id.target, main)
                views.setTextViewText(R.id.target, st.nextTime.format(Today.HM))
            }
            else -> {}
        }

        if (kind == Kind.LARGE || kind == Kind.WIDE) {
            for (i in 0..5) {
                val on = i == st.current
                views.setTextViewText(NAME_IDS[i], PrayerCalc.NAMES[i])
                views.setTextViewText(TIME_IDS[i], st.times[i].format(Today.HM))
                views.setTextColor(NAME_IDS[i], if (on) HIGHLIGHT else 0xCCFFFFFF.toInt())
                views.setTextColor(TIME_IDS[i], if (on) HIGHLIGHT else WHITE)
            }
        }

        if (clickable) {
            views.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return views
    }

    /** Ana ekrandaki bütün widget'ları yeniler ve bir sonraki yenileme alarmını kurar. */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        var any = false
        for (kind in Kind.entries) {
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, kind.provider))) {
                val opts = mgr.getAppWidgetOptions(id)
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: kind.w
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: kind.h
                mgr.updateAppWidget(id, build(ctx, kind, w, h))
                any = true
            }
        }

        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, VakitReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!any) {
            am.cancel(pi)
            return
        }
        setExact(am, Today.of(ctx).refreshAt.toInstant().toEpochMilli() + 500, pi)
    }

    fun setExact(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
