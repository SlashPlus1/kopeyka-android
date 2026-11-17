package app.kopeyka

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val body = "$title $text".trim()
        if (body.isEmpty()) return
        val sender = sbn.packageName + " " + title
        val v = Rules.evaluate(this, "push", sender, body)
        if (v.ok || v.hasAmount) Diag.add(this, "push", sender, body, v)
        if (v.ok && Inbox.add(this, body, "push")) Notifier.notifyNew(this)
    }
}
