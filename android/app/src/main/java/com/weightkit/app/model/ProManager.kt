package com.weightkit.app.model

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.weightkit.app.BuildConfig
import java.util.concurrent.Executors
import com.weightkit.app.debug.DebugHooks
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * One-time "Pro" in-app product (non-consumable, never consumed) + 7-day free trial
 * + 24 h Pro from a rewarded ad. Create a one-time product with this ID in Play Console.
 *
 * apps-api (shared GGSHOP server, see AppsApiClient.kt / CLIENT.md) is the source of truth:
 *  • hello() on launch and on resume → trial start/end by the server clock (survives "Clear data",
 *    reinstall and clock changes) and pro=true for devices with a verified purchase.
 *  • Every PURCHASED Play purchase is sent to purchase(): Ok → Pro + acknowledge; Rejected → no Pro, no
 *    acknowledge; Unavailable → Pro for now, sent again on the next launch.
 *  • Offline: the last server answer; never answered: the local rules below.
 *
 * Local fallback (no server answer yet):
 *  • Trial start = earliest of the saved first-launch time and PackageInfo.firstInstallTime.
 *    "Clear data" wipes SharedPreferences but NOT firstInstallTime, so it cannot restart the trial.
 *    On uninstall/reinstall Android Auto Backup restores the prefs (allowBackup=true).
 *  • Clock rollback: [now] never goes below the latest time the app has seen and keeps advancing
 *    with the monotonic clock while the device date is set back (see [Entitlement]).
 *  • Purchase state is always re-read from Play (refunds remove Pro); pending purchases don't count.
 */
class ProManager(ctx: Context, firstLaunch: Long, clock: Clock = SystemTimeClock) : PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_ID = "weightkit_pro"
        /** Key in apps-api (apps.json). Never change it after release. */
        const val APPS_API_ID = "weightkit"
        const val TRIAL_DAYS = Entitlement.TRIAL_DAYS
        const val REWARD_HOURS = Entitlement.REWARD_HOURS
    }

    private val prefs = ctx.getSharedPreferences("weightkit", Context.MODE_PRIVATE)
    private val installTime: Long = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime + DebugHooks.installOffsetMs()
    }.getOrDefault(firstLaunch)
    private val ent = Entitlement(PrefsStore(prefs), clock, firstLaunch, installTime)

    // ---- apps-api ----
    /** Off in debug UI tests that fake the clock or the store (they test the local rules). */
    private val serverEnabled: Boolean = DebugHooks.serverEnabled(ctx)
    private val api = AppsApiClient(ctx, APPS_API_ID, BuildConfig.DEBUG)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    init {
        if (serverEnabled) api.lastStatus()?.let { applyStatus(it, fresh = false) }
    }

    private fun applyStatus(st: AppsApiClient.Status, fresh: Boolean) =
        ent.applyServer(st.serverTime, st.trialStart, st.trialEnds, st.clockOffset, st.pro, fresh)

    /** POST hello on a background thread; the answer replaces the cached one. */
    fun hello() {
        if (!serverEnabled) return
        io.execute {
            val st = api.hello() ?: return@execute
            main.post { applyStatus(st, fresh = true) }
        }
    }

    /** Days left in the trial (server clock when known). */
    val trialDaysLeft: Int
        get() = (((trialEnd - now).coerceAtLeast(0) + 86_399_999L) / 86_400_000L).toInt()

    val trialStart: Long get() = ent.trialStart
    val trialEnd: Long get() = ent.trialEnd

    /** Current time that cannot be moved backwards by changing the device clock. */
    val now: Long get() = ent.now

    /** Call on launch / resume. */
    fun touchClock() = ent.touchClock()

    val rewardUntil: Long get() = ent.rewardUntil

    fun grantReward() = ent.grantReward()

    val purchased: Boolean get() = ent.purchased
    var price by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set

    private var details: ProductDetails? = null
    /** Debug UI tests only (always null in release). */
    private val fake: BillingOverride? = DebugHooks.billing(ctx)

    private val client: BillingClient = BillingClient.newBuilder(ctx.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // reconnects by itself, so refunds are still picked up after the Play service restarts
        .enableAutoServiceReconnection()
        .build()

    val inTrial: Boolean get() = ent.inTrial
    val hasReward: Boolean get() = ent.hasReward
    val hasPro: Boolean get() = ent.hasPro

    fun connect() {
        if (fake != null) { price = fake.price; refresh(); return }
        if (client.isReady) { queryProduct(); refresh(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    refresh()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetailsResult.productDetailsList.firstOrNull()
                price = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    /**
     * Queries can overlap (launch, resume, restore). Only the newest one, or a purchase that arrived
     * after it started, may set the state — a stale result must not undo a refund.
     */
    private val generation = java.util.concurrent.atomic.AtomicInteger()

    /** Re-reads owned purchases (also used for "Restore purchase"). */
    fun refresh() {
        hello()
        if (fake != null) { ent.updatePurchased(PurchaseRules.ownsPro(fake.purchases(), PRODUCT_ID)); return }
        if (!client.isReady) return
        busy = true
        val gen = generation.incrementAndGet()
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            busy = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK && gen == generation.get()) {
                handle(purchases, fromQuery = true)
            }
        }
    }

    fun buy(activity: Activity) {
        error = false
        if (fake != null) { fake.buy(); refresh(); return }
        val d = details
        if (d == null) {
            error = true
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            ).build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fromQuery = false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> error = true
        }
    }

    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
    }

    private fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        val paid = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // PENDING purchases are ignored until Play reports PURCHASED.
        if (!fromQuery && paid.isNotEmpty()) generation.incrementAndGet()

        if (!serverEnabled) {
            paid.forEach(::acknowledge)
            val owned = PurchaseRules.ownsPro(
                purchases.map { PurchaseInfo(it.products, it.purchaseState == Purchase.PurchaseState.PURCHASED) }, PRODUCT_ID,
            )
            if (owned || fromQuery) ent.updatePurchased(owned)
            return
        }

        if (paid.isEmpty()) {
            if (fromQuery) ent.updatePurchased(false)
            return
        }
        // The server already confirmed this device: no need to resend on every launch.
        if (fromQuery && ent.serverPro) {
            paid.forEach(::acknowledge)
            ent.updatePurchased(true)
            return
        }
        val gen = generation.get()
        io.execute {
            val results = paid.map { it to api.purchase(PRODUCT_ID, it.purchaseToken) }
            main.post {
                if (fromQuery && gen != generation.get()) return@post // a newer purchase/query won
                var ok = false
                var temporary = false
                for ((p, r) in results) when (r) {
                    is AppsApiClient.PurchaseResult.Ok -> { ok = true; acknowledge(p) } // ack only after the server said OK
                    is AppsApiClient.PurchaseResult.Unavailable -> temporary = true      // Play said PURCHASED; retry next launch
                    is AppsApiClient.PurchaseResult.Rejected -> {}                       // no Pro, no acknowledge
                }
                ent.updatePurchased(ok || temporary)
                if (!fromQuery && !ok && !temporary) error = true
                if (ok) hello()
            }
        }
    }
}

object ProHolder {
    @Volatile private var pm: ProManager? = null
    fun get(ctx: Context, firstLaunch: Long): ProManager = pm ?: synchronized(this) {
        pm ?: ProManager(ctx.applicationContext, firstLaunch, DebugHooks.clock(ctx)).also { pm = it }
    }
}
