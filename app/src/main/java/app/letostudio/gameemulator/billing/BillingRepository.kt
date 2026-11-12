package app.letostudio.gameemulator.billing

import android.app.Activity
import android.content.Context
import android.provider.Settings
import app.letostudio.gameemulator.billing.data.PurchaseDao
import app.letostudio.gameemulator.billing.data.PurchaseEntity
import app.letostudio.gameemulator.billing.model.BillingError
import app.letostudio.gameemulator.billing.model.BillingException
import app.letostudio.gameemulator.billing.model.BillingPeriod
import app.letostudio.gameemulator.billing.model.PurchaseResult
import app.letostudio.gameemulator.di.IoDispatcher
import app.letostudio.gameemulator.domain.model.PremiumStatus
import app.letostudio.gameemulator.domain.model.SubscriptionPlan
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * All Google Play Billing operations behind the repository boundary (tech_stacks.md §10):
 * product discovery (FR-005..FR-007), the purchase flow + 3-step pipeline (FR-008..FR-013),
 * restoration/reconciliation (FR-014, FR-015) and retry handling (FR-020..FR-024).
 * ViewModels only see [SubscriptionPlan], [PremiumStatus], [PurchaseResult] and Result.
 */
@Singleton
class BillingRepository @Inject constructor(
    private val clientWrapper: BillingClientWrapper,
    private val responseHandler: BillingResponseHandler,
    private val purchaseDao: PurchaseDao,
    private val entitlementManager: EntitlementManager,
    private val storeRemoteConfig: StoreRemoteConfig,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)

    /** Purchase pipeline outcomes for the UI (snackbars, paywall auto-dismiss). */
    val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    val premiumStatus: Flow<PremiumStatus> = entitlementManager.premiumStatus

    /** Never cached across paywall displays — refreshed by [loadPaywallPlans] (FR-005). */
    private val productDetailsCache = ConcurrentHashMap<String, ProductDetails>()

    /** Guards against concurrent billing flows for the same tap (US5 edge case). */
    private val purchaseInFlight = AtomicBoolean(false)

    // Obfuscated, deterministic per-device identifier for Play fraud detection (FR-026).
    private val obfuscatedAccountId: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
        MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(64)
    }

    init {
        scope.launch {
            clientWrapper.purchaseUpdates.collect { handlePurchasesUpdate(it) }
        }
    }

    suspend fun currentPremiumStatus(): PremiumStatus =
        withContext(ioDispatcher) { entitlementManager.currentPremiumStatus() }

    suspend fun hasSubscriptionHistory(): Boolean =
        withContext(ioDispatcher) { entitlementManager.hasSubscriptionHistory() }

    /**
     * Queries fresh plan details from Google Play for one purchase screen (FR-005, FR-006).
     * The product list, its order and the "Best value" product come from the screen's
     * store in Remote Config `subscription_stores_config` ([StoreRemoteConfig]); a blank
     * or unreadable value, or a missing store, falls back to the [BillingConfig] catalogue.
     * Product-level failures are tolerated: whatever Play returns is shown (FR-007).
     *
     * @param storeName [StoreConfig.PAYWALL] (onboarding / gated paywall) or [StoreConfig.STORE].
     */
    suspend fun loadPaywallPlans(
        storeName: String = StoreConfig.PAYWALL,
    ): Result<List<SubscriptionPlan>> = withContext(ioDispatcher) {
        val connection = clientWrapper.ensureConnected()
        if (connection.responseCode != BillingResponseCode.OK) {
            return@withContext Result.failure(BillingException(responseHandler.toError(connection)))
        }

        val store = storeFor(storeName)
        val productIds = store.items.distinct()
        // A known catalogue ID is queried under its own type; an ID only Remote Config
        // knows is queried under both, and Play returns it under the type it really has.
        val subsIds = productIds.filter { it !in BillingConfig.INAPP_PRODUCT_IDS }
        val inappIds = productIds.filter { it !in BillingConfig.SUBS_PRODUCT_IDS }

        val plans = mutableListOf<SubscriptionPlan>()

        if (subsIds.isNotEmpty()) {
            val call = queryProductDetailsWithRetry(subsIds, ProductType.SUBS)
            if (!call.isOk) {
                return@withContext Result.failure(BillingException(responseHandler.toError(call.result)))
            }
            call.value?.productDetailsList.orEmpty().forEach { details ->
                productDetailsCache[details.productId] = details
                BillingConfig.recordSubscription(details.productId)
                subscriptionPlanOf(details, store)?.let { plans += it }
            }
        }

        if (inappIds.isNotEmpty()) {
            val call = queryProductDetailsWithRetry(inappIds, ProductType.INAPP)
            if (!call.isOk) {
                return@withContext Result.failure(BillingException(responseHandler.toError(call.result)))
            }
            call.value?.productDetailsList.orEmpty().forEach { details ->
                productDetailsCache[details.productId] = details
                lifetimePlanOf(details, store)?.let { plans += it }
            }
        }

        // Trial eligibility (the IPTV app's rule): a user who has bought ANY plan before
        // cannot start a trial on another plan, so every trial-carrying plan is flagged
        // from the local purchase history. Play enforces the real rule at checkout and,
        // with "New customer acquisition" eligibility on the offer, withholds the free
        // phase from returning subscribers itself; nothing is hidden here, only the
        // trial copy and the order change.
        val trialEligible = !hasPurchasedBefore()
        val visiblePlans = selectPaywallPlans(plans.map { it.copy(isTrialEligible = trialEligible) }, store.items)

        if (visiblePlans.isEmpty()) {
            Result.failure(BillingException(BillingError.ItemUnavailable))
        } else {
            Result.success(visiblePlans)
        }
    }

    /**
     * Launches the Google Play purchase dialog (FR-008). Returns null when the flow
     * was launched (the outcome arrives via [purchaseResults]) or the launch error.
     * Subscription switches attach SubscriptionUpdateParams with the configured
     * replacement mode (FR-016).
     */
    suspend fun launchPurchase(activity: Activity, plan: SubscriptionPlan): BillingError? {
        if (!purchaseInFlight.compareAndSet(false, true)) return null

        val connection = clientWrapper.ensureConnected()
        if (connection.responseCode != BillingResponseCode.OK) {
            purchaseInFlight.set(false)
            return responseHandler.toError(connection)
        }

        val details = productDetailsCache[plan.productId]
        if (details == null) {
            purchaseInFlight.set(false)
            return BillingError.DeveloperError("Product details not loaded before purchase")
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { plan.offerToken?.let { setOfferToken(it) } }
            .build()

        val paramsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId)
            .setObfuscatedProfileId(obfuscatedAccountId)

        if (plan.billingPeriod != BillingPeriod.LIFETIME) {
            activeSubscriptionToken()?.let { oldToken ->
                paramsBuilder.setSubscriptionUpdateParams(
                    BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(oldToken)
                        .setSubscriptionReplacementMode(BillingConfig.SUBSCRIPTION_REPLACEMENT_MODE)
                        .build(),
                )
            }
        }

        val result = clientWrapper.client.launchBillingFlow(activity, paramsBuilder.build())
        if (result.responseCode != BillingResponseCode.OK) {
            purchaseInFlight.set(false)
            return responseHandler.toError(result)
        }
        return null
    }

    /**
     * Restores and reconciles purchases against Google Play (FR-014, US3): called on
     * app launch and every foreground resume to catch cross-device purchases, and
     * completes any unacknowledged purchase found (FR-015).
     */
    suspend fun refreshPurchases(): Result<Unit> = withContext(ioDispatcher) {
        val connection = clientWrapper.ensureConnected()
        if (connection.responseCode != BillingResponseCode.OK) {
            return@withContext Result.failure(BillingException(responseHandler.toError(connection)))
        }

        val purchases = mutableListOf<Purchase>()
        for (type in queryTypes()) {
            val call = responseHandler.withRetry { queryPurchases(type) }
            if (!call.isOk) {
                return@withContext Result.failure(BillingException(responseHandler.toError(call.result)))
            }
            purchases += call.value.orEmpty().also { recordTypes(type, it) }
        }

        entitlementManager.reconcile(purchases)
        purchases.forEach { processPurchase(it, emitResult = false) }
        Result.success(Unit)
    }

    // --- purchase pipeline -------------------------------------------------------------

    private suspend fun handlePurchasesUpdate(update: BillingClientWrapper.PurchasesUpdate) {
        purchaseInFlight.set(false)
        when (update.result.responseCode) {
            BillingResponseCode.OK ->
                update.purchases.forEach { processPurchase(it, emitResult = true) }

            BillingResponseCode.USER_CANCELED ->
                _purchaseResults.emit(PurchaseResult.Cancelled)

            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // FR-023: verify actual ownership with a fresh query, then sync.
                refreshPurchases()
                _purchaseResults.emit(PurchaseResult.AlreadyOwned)
            }

            else -> {
                val error = responseHandler.toError(update.result)
                _purchaseResults.emit(
                    if (error.isRetriable) PurchaseResult.NetworkError(error) else PurchaseResult.Error(error),
                )
            }
        }
    }

    /** 3-step pipeline (FR-010): update entitlement → notify → acknowledge/consume. */
    private suspend fun processPurchase(purchase: Purchase, emitResult: Boolean) {
        upsertPurchase(purchase)
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                entitlementManager.grant(purchase)
                if (emitResult) _purchaseResults.emit(PurchaseResult.Success(purchase))
                val consumable = purchase.products.any { BillingConfig.isConsumable(it) }
                when {
                    consumable -> consume(purchase)
                    !purchase.isAcknowledged -> acknowledge(purchase)
                }
            }

            Purchase.PurchaseState.PENDING -> {
                // FR-013: no entitlement while pending; Room keeps the pending record.
                if (emitResult) _purchaseResults.emit(PurchaseResult.Pending(purchase))
            }

            else -> Unit
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val call = responseHandler.withRetry {
            val connection = clientWrapper.ensureConnected()
            if (connection.responseCode != BillingResponseCode.OK) {
                return@withRetry BillingResponseHandler.BillingCall<Unit>(connection, null)
            }
            val result = suspendCancellableCoroutine<BillingResult> { continuation ->
                clientWrapper.client.acknowledgePurchase(params) { acknowledgeResult ->
                    if (continuation.isActive) continuation.resume(acknowledgeResult)
                }
            }
            BillingResponseHandler.BillingCall(result, null)
        }
        when {
            call.isOk ->
                purchaseDao.markAcknowledged(purchase.purchaseToken, System.currentTimeMillis())

            // FR-024: verify ownership before ever retrying an acknowledge.
            call.result.responseCode == BillingResponseCode.ITEM_NOT_OWNED -> reconcileFromPlay()
        }
    }

    private suspend fun consume(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val call = responseHandler.withRetry {
            val connection = clientWrapper.ensureConnected()
            if (connection.responseCode != BillingResponseCode.OK) {
                return@withRetry BillingResponseHandler.BillingCall<Unit>(connection, null)
            }
            val result = suspendCancellableCoroutine<BillingResult> { continuation ->
                clientWrapper.client.consumeAsync(params) { consumeResult, _ ->
                    if (continuation.isActive) continuation.resume(consumeResult)
                }
            }
            BillingResponseHandler.BillingCall(result, null)
        }
        when {
            call.isOk -> {
                purchaseDao.markConsumed(purchase.purchaseToken, System.currentTimeMillis())
                // Consumable entitlements are single-use (FR-012): credit granted, row cleared.
                purchase.products
                    .filter { BillingConfig.isConsumable(it) }
                    .forEach { entitlementManager.revokeConsumed(it) }
            }

            call.result.responseCode == BillingResponseCode.ITEM_NOT_OWNED -> reconcileFromPlay()
        }
    }

    /** Ownership re-sync without the acknowledge pass — avoids acknowledge↔refresh loops. */
    private suspend fun reconcileFromPlay() {
        val purchases = mutableListOf<Purchase>()
        for (type in queryTypes()) {
            val call = responseHandler.withRetry { queryPurchases(type) }
            if (!call.isOk) return
            purchases += call.value.orEmpty().also { recordTypes(type, it) }
        }
        entitlementManager.reconcile(purchases)
    }

    private suspend fun upsertPurchase(purchase: Purchase) {
        val existing = purchaseDao.getByToken(purchase.purchaseToken)
        val now = System.currentTimeMillis()
        purchaseDao.upsert(
            PurchaseEntity(
                purchaseToken = purchase.purchaseToken,
                orderId = purchase.orderId,
                productIds = purchase.products.joinToString(","),
                purchaseState = purchase.purchaseState,
                isAcknowledged = purchase.isAcknowledged || existing?.isAcknowledged == true,
                isConsumed = existing?.isConsumed == true,
                purchaseTime = purchase.purchaseTime,
                quantity = purchase.quantity,
                obfuscatedAccountId = purchase.accountIdentifiers?.obfuscatedAccountId,
                isAutoRenewing = purchase.isAutoRenewing,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    // --- Play service calls ---------------------------------------------------------------

    /**
     * Both types, always: Remote Config may sell a product the in-code catalogue does
     * not list, and its purchases must still restore. An empty query costs nothing.
     */
    private fun queryTypes(): List<String> = listOf(ProductType.SUBS, ProductType.INAPP)

    /** Remembers which products Play reported as subscriptions, for entitlement typing. */
    private fun recordTypes(type: String, purchases: List<Purchase>) {
        if (type != ProductType.SUBS) return
        purchases.forEach { purchase -> purchase.products.forEach(BillingConfig::recordSubscription) }
    }

    /** The named store from Remote Config, else the in-code catalogue's store of that name. */
    private fun storeFor(storeName: String): StoreConfig {
        val fallback = StoreConfigParser.defaultConfiguration()
        return storeRemoteConfig.storeConfiguration().storeNamed(storeName)
            ?: fallback.storeNamed(storeName)
            ?: fallback.stores.first()
    }

    private suspend fun queryProductDetailsWithRetry(
        productIds: List<String>,
        productType: String,
    ): BillingResponseHandler.BillingCall<QueryProductDetailsResult> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(productType)
                        .build()
                },
            )
            .build()
        return responseHandler.withRetry {
            suspendCancellableCoroutine { continuation ->
                clientWrapper.client.queryProductDetailsAsync(params) { result, detailsResult ->
                    if (continuation.isActive) {
                        continuation.resume(BillingResponseHandler.BillingCall(result, detailsResult))
                    }
                }
            }
        }
    }

    private suspend fun queryPurchases(productType: String): BillingResponseHandler.BillingCall<List<Purchase>> {
        val connection = clientWrapper.ensureConnected()
        if (connection.responseCode != BillingResponseCode.OK) {
            return BillingResponseHandler.BillingCall(connection, null)
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(productType).build()
        return suspendCancellableCoroutine { continuation ->
            clientWrapper.client.queryPurchasesAsync(params) { result, purchases ->
                if (continuation.isActive) {
                    continuation.resume(BillingResponseHandler.BillingCall(result, purchases))
                }
            }
        }
    }

    /**
     * Whether this install has ever completed a purchase: the local Room history (kept
     * across expiry, so it survives a lapsed subscription) plus what Play reports right
     * now (covers a fresh install by a returning subscriber). Billing 8 removed
     * queryPurchaseHistory, so this is the best available signal; a failed Play query
     * counts as "no purchase" so an error never hides the trial from a new user.
     */
    private suspend fun hasPurchasedBefore(): Boolean {
        if (purchaseDao.getAll().any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) return true
        return queryTypes().any { type ->
            queryPurchases(type).value.orEmpty()
                .any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        }
    }

    private suspend fun activeSubscriptionToken(): String? {
        if (BillingConfig.SUBS_PRODUCT_IDS.isEmpty()) return null
        val call = queryPurchases(ProductType.SUBS)
        return call.value.orEmpty()
            .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            ?.purchaseToken
    }

    // --- ProductDetails → SubscriptionPlan mapping -----------------------------------------

    /** One paywall card per subscription product. */
    private fun subscriptionPlanOf(details: ProductDetails, store: StoreConfig): SubscriptionPlan? {
        // Cheapest first phase = best available offer (free trials naturally win).
        val bestOffer = details.subscriptionOfferDetails.orEmpty().minByOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE
        } ?: return null
        val phases = bestOffer.pricingPhases.pricingPhaseList
        // The last phase is the recurring base price (earlier phases are trial/intro offers).
        val basePhase = phases.lastOrNull() ?: return null
        return SubscriptionPlan(
            productId = details.productId,
            basePlanId = bestOffer.basePlanId,
            offerToken = bestOffer.offerToken,
            name = details.name,
            billingPeriod = BillingConfig.periodOf(details.productId)
                ?: BillingPeriod.fromIso(basePhase.billingPeriod)
                ?: BillingPeriod.MONTHLY,
            formattedPrice = basePhase.formattedPrice,
            priceMicros = basePhase.priceAmountMicros,
            currencyCode = basePhase.priceCurrencyCode,
            isBestOffer = store.isPromoted(details.productId),
            hasFreeTrial = phases.any { it.priceAmountMicros == 0L },
        )
    }

    companion object {
        /**
         * Paywall order rule (the IPTV app's): every plan Play returned is listed, only
         * the order changes.
         * - A usable free trial is on offer ([SubscriptionPlan.offersTrial]): cheapest
         *   first, so the trial leads.
         * - Otherwise (no trial configured, Play withheld the free phase, or this user
         *   already purchased): lead with the best-value plan; the trial product stays
         *   on the list as a plain plan.
         */
        fun selectPaywallPlans(
            plans: List<SubscriptionPlan>,
            order: List<String> = StoreConfigParser.defaultConfiguration().stores.first().items,
        ): List<SubscriptionPlan> {
            fun position(productId: String) =
                order.indexOf(productId).let { if (it == -1) Int.MAX_VALUE else it }
            val unlistedLast = compareBy<SubscriptionPlan> { position(it.productId) == Int.MAX_VALUE }
            return if (plans.any { it.offersTrial }) {
                plans.sortedWith(unlistedLast.thenBy { position(it.productId) })
            } else {
                plans.sortedWith(unlistedLast.thenByDescending { position(it.productId) })
            }
        }
    }

    private fun lifetimePlanOf(details: ProductDetails, store: StoreConfig): SubscriptionPlan? {
        val offer = details.oneTimePurchaseOfferDetails ?: return null
        return SubscriptionPlan(
            productId = details.productId,
            basePlanId = null,
            offerToken = null,
            name = details.name,
            billingPeriod = BillingPeriod.LIFETIME,
            formattedPrice = offer.formattedPrice,
            priceMicros = offer.priceAmountMicros,
            currencyCode = offer.priceCurrencyCode,
            isBestOffer = store.isPromoted(details.productId),
        )
    }
}
