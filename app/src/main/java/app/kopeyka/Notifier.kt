package app.kopeyka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CHANNEL = "inbox"
    private const val CHANNEL_PAY = "payments"

    /** Текст без суммы и магазина, чтобы ничего лишнего не показывалось на заблокированном экране. */
    fun notifyNew(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Новые операции", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Копейка")
            .setContentText("Найдена новая операция. Откройте, чтобы проверить.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        nm.notify(1001, n)
    }

    /** Напоминание о платеже. На заблокированном экране показывается общий текст без названия и суммы. */
    fun notifyReminder(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_PAY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PAY, "Напоминания о платежах", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pub = NotificationCompat.Builder(ctx, CHANNEL_PAY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Копейка")
            .setContentText("Напоминание о платеже")
            .build()
        val n = NotificationCompat.Builder(ctx, CHANNEL_PAY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(pub)
            .build()
        nm.notify(2000 + id, n)
    }
}
