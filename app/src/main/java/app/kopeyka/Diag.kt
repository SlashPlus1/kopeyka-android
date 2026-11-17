package app.kopeyka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Журнал последних сообщений, которые увидело приложение: отправитель, результат и причина.
 * Текст сохраняется только у сообщений с суммой в рублях. Журнал лежит на телефоне.
 */
object Diag {
    private const val PREFS = "kopeyka_diag"
    private const val KEY = "log"
    private const val MAX = 40

    @Synchronized
    fun add(ctx: Context, src: String, sender: String, text: String, v: Verdict) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val o = JSONObject()
        o.put("ts", System.currentTimeMillis())
        o.put("src", src)
        o.put("sender", sender.take(60))
        o.put("ok", v.ok)
        o.put("reason", v.reason)
        o.put("preview", if (v.hasAmount) text.take(100) else "")
        arr.put(o)
        val out = JSONArray()
        val start = maxOf(0, arr.length() - MAX)
        for (i in start until arr.length()) out.put(arr.get(i))
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    @Synchronized
    fun readAll(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"

    @Synchronized
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
