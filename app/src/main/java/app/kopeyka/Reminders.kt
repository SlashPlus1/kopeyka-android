package app.kopeyka

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray

/**
 * Напоминания о платежах. Страница присылает готовый список (время, заголовок, текст),
 * приложение ставит по нему будильники. Список хранится на телефоне, чтобы пережить перезагрузку.
 * Используются неточные будильники: разрешение на точные будильники не нужно, срабатывание может сдвинуться на несколько минут.
 */
object Reminders {
    private const val PREFS = "kopeyka_rem"
    private const val KEY = "items"
    private const val MAX = 60

    fun apply(ctx: Context, json: String) {
        cancelAll(ctx)
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = JSONArray()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            if (out.length() >= MAX) break
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("ts") > now) out.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply()
        scheduleAll(ctx)
    }

    /** После перезагрузки телефона будильники сбрасываются, ставим их заново из сохранённого списка. */
    fun reschedule(ctx: Context) {
        scheduleAll(ctx)
    }

    private fun stored(ctx: Context): JSONArray {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            JSONArray(s)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val n = stored(ctx).length()
        for (i in 0 until n) {
            val pi = PendingIntent.getBroadcast(
                ctx, i, Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val arr = stored(ctx)
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ts = o.optLong("ts")
            if (ts <= now) continue
            val intent = Intent(ctx, ReminderReceiver::class.java)
            intent.putExtra("id", i)
            intent.putExtra("title", o.optString("title"))
            intent.putExtra("text", o.optString("text"))
            val pi = PendingIntent.getBroadcast(
                ctx, i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        Notifier.notifyReminder(context, intent.getIntExtra("id", 0), title, text)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.reschedule(context)
    }
}
