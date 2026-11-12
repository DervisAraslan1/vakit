package dev.vakit.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Bildirim kanalları ve "vakit girdi" sesi. Android'de bir kanalın sesi sonradan
 * değiştirilemez; ses değişince kanal yeni sürüm numarasıyla yeniden kurulur.
 */
object Sounds {
    /** Vakitten önceki hatırlatmalar: telefonun varsayılan bildirim sesi. */
    const val REMIND = "hatirlatma"

    fun atTimeChannel(ctx: Context) = "vakit_${Store.channelVersion(ctx)}"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("vakitler") // 1.x sürümündeki tek kanal
        nm.createNotificationChannel(
            NotificationChannel(REMIND, "Vakit hatırlatmaları", NotificationManager.IMPORTANCE_HIGH),
        )
        val id = atTimeChannel(ctx)
        if (nm.getNotificationChannel(id) != null) return
        nm.notificationChannels.filter { it.id.startsWith("vakit_") }.forEach { nm.deleteNotificationChannel(it.id) }
        val channel = NotificationChannel(id, "Vakit girdi", NotificationManager.IMPORTANCE_HIGH)
        when (Store.soundMode(ctx)) {
            "silent" -> channel.setSound(null, null)
            "custom" -> channel.setSound(
                Uri.parse(Store.soundUri(ctx)),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            else -> {} // telefonun varsayılan bildirim sesi
        }
        nm.createNotificationChannel(channel)
    }

    /** mode: "default", "silent" ya da "custom" (uri ve ad ile). */
    fun set(ctx: Context, mode: String, uri: Uri? = null, name: String = "") {
        Store.setSound(ctx, mode, uri?.toString().orEmpty(), name)
        Store.bumpChannel(ctx)
        ensureChannels(ctx)
    }

    fun title(ctx: Context, uri: Uri): String =
        runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull() ?: "Seçilen ses"

    /**
     * Seçilen ses dosyasını (ör. indirilmiş bir ezan) telefonun Bildirimler klasörüne
     * kopyalar. Dosya yöneticisinden gelen adresi bildirim sistemi okuyamaz; kopyası okuyabilir.
     * Android 10 ve üstü gerekir. Ağır iş: ana iş parçacığında çağrılmaz.
     */
    fun importFile(ctx: Context, src: Uri): Pair<Uri, String> {
        check(Build.VERSION.SDK_INT >= 29)
        val cr = ctx.contentResolver
        val name = cr.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "vakit-sesi.mp3"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, cr.getType(src) ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/Vakit")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val dst = cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Ses dosyası kaydedilemedi")
        cr.openInputStream(src).use { input ->
            cr.openOutputStream(dst).use { output -> input!!.copyTo(output!!) }
        }
        cr.update(dst, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return dst to name.substringBeforeLast('.')
    }
}
