package app.kopeyka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Verdict(val ok: Boolean, val reason: String, val hasAmount: Boolean)

/**
 * Правила чтения сообщений. Сообщение принимается, если в нём есть сумма в рублях
 * и оно подходит под одно из включённых правил: источник (СМС или уведомление),
 * отправитель, нужные слова, слова-исключения.
 */
object Rules {
    private const val PREFS = "kopeyka_rules"
    private const val KEY = "rules"

    private val amountRe =
        Regex("""\d[\d\s ]*([.,]\d{1,2})?\s*(₽|руб|р(?![а-яё])|rub|rur)|(rub|rur|₽)\s*\d""")

    private fun rule(
        id: String, name: String, src: String,
        senders: List<String>, words: List<String>, skip: List<String>
    ): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("src", src)
        o.put("senders", JSONArray(senders))
        o.put("words", JSONArray(words))
        o.put("skip", JSONArray(skip))
        o.put("on", true)
        return o
    }

    private fun defaults(): JSONArray {
        val words = listOf(
            "покупка", "оплата", "списание", "списан", "зачисление", "зачислен",
            "поступление", "поступил", "пополнение", "перевод", "снятие", "выдача",
            "pokupka", "oplata", "spisanie", "zachislenie", "postuplenie",
            "purchase", "payment", "debit", "credit"
        )
        val skip = listOf(
            "пароль", "код подтверждения", "никому не сообщайте", "скидк", "предложен", "реклам"
        )
        val apps = listOf(
            "sber", "tinkoff", "tbank", "vtb", "alfa", "raiff", "gazprom",
            "sovcom", "pochta", "rshb", "uralsib", "otkritie", "mkb"
        )
        val a = JSONArray()
        a.put(rule("sms-banks", "СМС банков", "sms", emptyList(), words, skip))
        a.put(rule("push-banks", "Уведомления банков", "push", apps, words, skip))
        return a
    }

    fun read(ctx: Context): String {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (s.isNullOrBlank()) return defaults().toString()
        return s
    }

    fun write(ctx: Context, json: String): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (json.isBlank()) {
            p.edit().remove(KEY).apply()
            return true
        }
        return try {
            JSONArray(json)
            p.edit().putString(KEY, json).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun load(ctx: Context): JSONArray =
        try { JSONArray(read(ctx)) } catch (e: Exception) { defaults() }

    private fun strs(a: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val v = a.optString(i, "").trim().lowercase()
            if (v.isNotEmpty()) out.add(v)
        }
        return out
    }

    /** src: "sms" или "push"; sender: номер или имя отправителя, для уведомлений пакет приложения и заголовок. */
    fun evaluate(ctx: Context, src: String, sender: String, text: String): Verdict {
        val t = text.lowercase()
        if (!amountRe.containsMatchIn(t)) {
            return Verdict(false, "в тексте нет суммы в рублях", false)
        }
        val s = sender.lowercase()
        val rules = load(ctx)
        var enabled = 0
        var senderOk = false
        var skipWord: String? = null
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (!r.optBoolean("on", true)) continue
            val rs = r.optString("src", "any")
            if (rs != "any" && rs != src) continue
            enabled++
            val senders = strs(r.optJSONArray("senders"))
            if (senders.isNotEmpty() && senders.none { s.contains(it) }) continue
            senderOk = true
            val hit = strs(r.optJSONArray("skip")).firstOrNull { t.contains(it) }
            if (hit != null) {
                skipWord = hit
                continue
            }
            val words = strs(r.optJSONArray("words"))
            if (words.isNotEmpty() && words.none { t.contains(it) }) continue
            return Verdict(true, "правило «" + r.optString("name") + "»", true)
        }
        if (enabled == 0) return Verdict(false, "нет включённых правил для этого источника", true)
        if (!senderOk) return Verdict(false, "отправитель не указан ни в одном правиле", true)
        if (skipWord != null) return Verdict(false, "исключено словом «" + skipWord + "»", true)
        return Verdict(false, "в тексте нет слов из правил", true)
    }
}
