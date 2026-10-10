// Примерен клиент за apps-api (GGSHOP LIMITED). Копира се в androidMain на приложението
// (обикновен Android проект: app/src/main/java/<пакет>/)
// и се сменя само package. Без външни библиотеки: HttpURLConnection + org.json.
//
// Всички мрежови функции са БЛОКИРАЩИ: викай ги от Dispatchers.IO (или друга фонова нишка).
// Описание на протокола: D:\ANDROID APPS\VPS API\CLIENT.md
package com.weightkit.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class AppsApiClient(
    context: Context,
    /** Ключът на приложението от apps.json, напр. "stillear". */
    private val appId: String,
    /** BuildConfig.DEBUG: сървърът брои debug устройствата отделно. */
    private val debug: Boolean,
) {
    companion object {
        /** Основен адрес, после резервен (работи и без DNS). */
        val BASE_URLS = listOf(
            "https://apps.ggshoplimited.com",
            "https://apps.57.128.185.126.nip.io",
        )
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val PREFS = "apps_api"
    }

    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Какво казва сървърът за това устройство. Времената са в ms (UTC epoch). */
    data class Status(
        val serverTime: Long,
        val trialStart: Long,
        val trialDays: Int,
        val trialEnds: Long,
        val pro: Boolean,
        val productId: String?,
        /** ISO-8601 UTC или null (доживотен / няма Pro). */
        val proUntil: String?,
        /** Имало е покупка, но е върната/отнета. */
        val revoked: Boolean,
        /** Разлика между часовника на сървъра и на телефона (ms). */
        val clockOffset: Long,
    ) {
        /** Сега по часовника на сървъра (телефонът може да е с грешен час). */
        fun serverNow(): Long = System.currentTimeMillis() + clockOffset
        fun trialActive(): Boolean = serverNow() < trialEnds
        /** Оставащи дни, закръглени нагоре (последният ден показва 1, не 0). */
        fun trialDaysLeft(): Int {
            val left = (trialEnds - serverNow()).coerceAtLeast(0)
            return ((left + 86_399_999L) / 86_400_000L).toInt()
        }
        fun hasAccess(): Boolean = pro || trialActive()
    }

    sealed class PurchaseResult {
        /** Сървърът потвърди покупката при Google. */
        data class Ok(val productId: String, val proUntil: String?, val devicesUsed: Int, val devicesMax: Int) : PurchaseResult()
        /** Google казва, че покупката не важи (402) или заявката е грешна (400/404). НЕ отключвай Pro. */
        data class Rejected(val code: String, val message: String) : PurchaseResult()
        /** Няма връзка или Google не отговаря (503). Опитай пак по-късно; Play Billing вече е казал PURCHASED. */
        data class Unavailable(val reason: String) : PurchaseResult()
    }

    /**
     * sha256("<app>:" + ANDROID_ID) като hex. ANDROID_ID не се променя при „Изчисти данните“ и
     * преинсталиране (за същия ключ на подписване), затова пробният период не се нулира.
     * Самият ANDROID_ID не напуска телефона.
     */
    @SuppressLint("HardwareIds")
    fun deviceHash(): String {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        val id = if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
            // Много рядко (стари/повредени устройства): собствен идентификатор. Той се губи при „Изчисти данните“.
            prefs.getString("fallback_id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("fallback_id", it).apply()
            }
        } else androidId
        return sha256("$appId:$id")
    }

    /** При всяко пускане на приложението. null = сървърът не е достъпен (ползвай lastStatus()). */
    fun hello(): Status? {
        val body = JSONObject()
            .put("device", deviceHash())
            .put("platform", "android")
            .put("build", if (debug) "debug" else "release")
        val (code, json) = post("/v1/$appId/hello", body) ?: return null
        if (code != 200) return null
        val status = parseStatus(json)            // добавя _offset в json
        prefs.edit().putString("last_status", json.toString()).apply()
        return status
    }

    /** Последният отговор на hello() (за офлайн). null = никога не е имало връзка. */
    fun lastStatus(): Status? =
        prefs.getString("last_status", null)?.let { runCatching { parseStatus(JSONObject(it)) }.getOrNull() }

    /**
     * След успешна покупка (Purchase.PurchaseState.PURCHASED) и при всяко пускане за притежаваните покупки
     * (queryPurchasesAsync), ако сървърът още не казва pro=true за това устройство.
     */
    fun purchase(productId: String, purchaseToken: String): PurchaseResult {
        val body = JSONObject()
            .put("device", deviceHash())
            .put("platform", "android")
            .put("product_id", productId)
            .put("token", purchaseToken)
        val (code, json) = post("/v1/$appId/purchase", body)
            ?: return PurchaseResult.Unavailable("no_connection")
        return when (code) {
            200 -> {
                // След покупка обнови кеша при следващия hello(); тук само отбелязваме Pro.
                PurchaseResult.Ok(
                    productId = json.optString("product_id", productId),
                    proUntil = json.optStringOrNull("pro_until"),
                    devicesUsed = json.optInt("devices_used"),
                    devicesMax = json.optInt("devices_max"),
                )
            }
            400, 402, 404 -> PurchaseResult.Rejected(json.optString("error", "rejected"), json.optString("message"))
            else -> PurchaseResult.Unavailable(json.optString("error", "http_$code"))
        }
    }

    // ------------------------------------------------------------------ мрежа

    /** Пробва адресите поред. Следващият се пробва при мрежова грешка или 5xx от nginx/сървъра. */
    private fun post(path: String, body: JSONObject): Pair<Int, JSONObject>? {
        val order = preferredOrder()
        var last: Pair<Int, JSONObject>? = null
        for (base in order) {
            try {
                val r = postOnce(base + path, body)
                if (r.first < 500) {
                    prefs.edit().putString("base_url", base).apply()
                    return r
                }
                last = r
            } catch (_: IOException) {
                // DNS, TLS, timeout: следващият адрес
            }
        }
        return last
    }

    private fun preferredOrder(): List<String> {
        val good = prefs.getString("base_url", null)
        return if (good != null && good in BASE_URLS) listOf(good) + (BASE_URLS - good) else BASE_URLS
    }

    private fun postOnce(url: String, body: JSONObject): Pair<Int, JSONObject> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.readTimeout = READ_TIMEOUT_MS
            c.doOutput = true
            c.useCaches = false
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            return code to json
        } finally {
            c.disconnect()
        }
    }

    private fun parseStatus(j: JSONObject): Status {
        val serverTime = j.getLong("server_time")
        return Status(
            serverTime = serverTime,
            trialStart = j.getLong("trial_start"),
            trialDays = j.getInt("trial_days"),
            trialEnds = j.getLong("trial_ends"),
            pro = j.optBoolean("pro"),
            productId = j.optStringOrNull("product_id"),
            proUntil = j.optStringOrNull("pro_until"),
            revoked = j.optBoolean("revoked"),
            // Отместването се пази от момента на отговора; при кеширан статус е приблизително.
            clockOffset = j.optLong("_offset", serverTime - System.currentTimeMillis()).also {
                if (!j.has("_offset")) j.put("_offset", it)
            },
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).ifEmpty { null }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
