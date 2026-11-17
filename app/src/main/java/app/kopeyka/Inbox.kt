package app.kopeyka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Очередь найденных в СМС и уведомлениях сообщений о тратах.
 * Лежит в закрытом хранилище приложения на телефоне.
 * Список "seen" помнит уже обработанные сообщения, чтобы поиск по старым СМС не добавлял их повторно.
 */
object Inbox {
    private const val PREFS = "kopeyka_inbox"
    private const val KEY = "items"
    private const val SEEN = "seen"
    private const val MAX_ITEMS = 200
    private const val MAX_SEEN = 3000

    private fun seenKey(text: String, ts: Long): String =
        text.hashCode().toString() + "|" + (ts / 86_400_000L).toString()

    @Synchronized
    fun add(
        ctx: Context,
        text: String,
        source: String,
        ts: Long = System.currentTimeMillis(),
        fromScan: Boolean = false
    ): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = try {
            JSONArray(prefs.getString(SEEN, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val k = seenKey(text, ts)
        var known = false
        for (i in 0 until seen.length()) {
            if (seen.optString(i) == k) {
                known = true
                break
            }
        }
        if (fromScan && known) return false

        val arr = try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        if (!fromScan) {
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("text") == text && now - o.optLong("ts") < 120_000L) return false
            }
        }
        val item = JSONObject()
        item.put("id", java.util.UUID.randomUUID().toString())
        item.put("text", text)
        item.put("src", source)
        item.put("ts", ts)
        arr.put(item)

        val out = JSONArray()
        val start = maxOf(0, arr.length() - MAX_ITEMS)
        for (i in start until arr.length()) out.put(arr.get(i))

        if (!known) seen.put(k)
        val seenOut = JSONArray()
        val sStart = maxOf(0, seen.length() - MAX_SEEN)
        for (i in sStart until seen.length()) seenOut.put(seen.get(i))

        prefs.edit().putString(KEY, out.toString()).putString(SEEN, seenOut.toString()).apply()
        return true
    }

    @Synchronized
    fun readAll(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
    }

    @Synchronized
    fun remove(ctx: Context, idsJson: String) {
        val ids = HashSet<String>()
        try {
            val a = JSONArray(idsJson)
            for (i in 0 until a.length()) ids.add(a.getString(i))
        } catch (e: Exception) {
            return
        }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!ids.contains(o.optString("id"))) out.put(o)
        }
        prefs.edit().putString(KEY, out.toString()).apply()
    }
}
