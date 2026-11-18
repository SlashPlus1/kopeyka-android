package app.kopeyka

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationManagerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var recognizer: SpeechRecognizer? = null
    private var voiceAfterPermission = false
    private var pendingScanDays = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.setSupportZoom(false)

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            // Никаких переходов наружу: приложение показывает только свои файлы.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = true
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onResume() {
        super.onResume()
        js("window.__resume&&window.__resume()")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.__back&&window.__back())===true") { result ->
            if (result != "true") finish()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        web.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && voiceAfterPermission) {
            voiceAfterPermission = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginVoice()
            } else {
                sendVoice("error", "no_permission")
            }
        }
        if (requestCode == REQ_READ) {
            val days = pendingScanDays
            pendingScanDays = 0
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && days > 0) {
                beginScan(days)
            } else {
                sendScan("{\"err\":\"no_permission\"}")
            }
        }
        js("window.__resume&&window.__resume()")
    }

    private fun sendScan(json: String) {
        js("window.onScan&&window.onScan(" + JSONObject.quote(json) + ")")
    }

    private fun beginScan(days: Int) {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            pendingScanDays = days
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQ_READ)
            return
        }
        Thread {
            sendScan(scanSms(days))
        }.start()
    }

    /** Просматривает входящие СМС за последние дни и добавляет подходящие под правила. */
    private fun scanSms(days: Int): String {
        val since = System.currentTimeMillis() - days * 86_400_000L
        var checked = 0
        var added = 0
        try {
            val c = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("address", "body", "date"),
                "date > ?",
                arrayOf(since.toString()),
                "date DESC"
            ) ?: return "{\"err\":\"read_failed\"}"
            c.use {
                val ia = it.getColumnIndexOrThrow("address")
                val ib = it.getColumnIndexOrThrow("body")
                val id = it.getColumnIndexOrThrow("date")
                while (it.moveToNext() && checked < 1500) {
                    checked++
                    val body = it.getString(ib) ?: continue
                    val addr = it.getString(ia) ?: ""
                    val date = it.getLong(id)
                    val v = Rules.evaluate(this, "sms", addr, body)
                    if (v.ok && Inbox.add(this, body, "sms", date, true)) added++
                }
            }
        } catch (e: Exception) {
            return "{\"err\":\"read_failed\"}"
        }
        return "{\"checked\":" + checked + ",\"added\":" + added + "}"
    }

    private fun js(code: String) {
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun sendVoice(kind: String, text: String) {
        val payload = JSONObject.quote(text)
        js("window.onVoice&&window.onVoice('$kind',$payload)")
    }

    private fun beginVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendVoice("error", "unavailable")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = sendVoice("ready", "")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) = sendVoice("error", error.toString())
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                sendVoice("final", text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                sendVoice("partial", text)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(i)
    }

    /** Методы, которые страница вызывает как Android.имя(...). Страница грузится только из файлов приложения. */
    inner class Bridge {
        @JavascriptInterface
        fun getInbox(): String = Inbox.readAll(this@MainActivity)

        @JavascriptInterface
        fun ackInbox(idsJson: String) {
            Inbox.remove(this@MainActivity, idsJson)
        }

        @JavascriptInterface
        fun startVoice() {
            runOnUiThread { beginVoice() }
        }

        @JavascriptInterface
        fun stopVoice() {
            runOnUiThread { recognizer?.stopListening() }
        }

        @JavascriptInterface
        fun requestSmsAccess() {
            runOnUiThread {
                val perms = ArrayList<String>()
                perms.add(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                requestPermissions(perms.toTypedArray(), REQ_SMS)
            }
        }

        @JavascriptInterface
        fun openNotificationAccess() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        @JavascriptInterface
        fun getRules(): String = Rules.read(this@MainActivity)

        @JavascriptInterface
        fun setRules(json: String): Boolean = Rules.write(this@MainActivity, json)

        @JavascriptInterface
        fun testRules(src: String, sender: String, text: String): String {
            val v = Rules.evaluate(this@MainActivity, src, sender, text)
            val o = JSONObject()
            o.put("ok", v.ok)
            o.put("reason", v.reason)
            return o.toString()
        }

        @JavascriptInterface
        fun getLog(): String = Diag.readAll(this@MainActivity)

        @JavascriptInterface
        fun clearLog() {
            Diag.clear(this@MainActivity)
        }

        @JavascriptInterface
        fun scanInbox(days: Int) {
            runOnUiThread { beginScan(days) }
        }

        @JavascriptInterface
        fun setReminders(json: String) {
            Reminders.apply(this@MainActivity, json)
        }

        @JavascriptInterface
        fun requestPostNotifications() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                }
            }
        }

        @JavascriptInterface
        fun status(): String {
            val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            val notif = NotificationManagerCompat
                .getEnabledListenerPackages(this@MainActivity)
                .contains(this@MainActivity.packageName)
            val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val o = JSONObject()
            o.put("sms", sms)
            o.put("notif", notif)
            o.put("mic", mic)
            o.put("postNotif", Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            o.put("read", checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
            return o.toString()
        }
    }

    companion object {
        private const val REQ_SMS = 10
        private const val REQ_MIC = 11
        private const val REQ_READ = 12
        private const val REQ_NOTIF = 13
    }
}
