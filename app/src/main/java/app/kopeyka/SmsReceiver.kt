package app.kopeyka

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts[0].originatingAddress ?: ""
        val body = parts.joinToString("") { it.messageBody ?: "" }
        if (body.isEmpty()) return
        val v = Rules.evaluate(context, "sms", sender, body)
        Diag.add(context, "sms", sender, body, v)
        if (v.ok && Inbox.add(context, body, "sms")) Notifier.notifyNew(context)
    }
}
