package app.letostudio.gameemulator.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the single app-wide BillingClient (FR-001, FR-003) with pending-purchase
 * support for one-time products and prepaid plans (FR-004) and the library's
 * automatic service reconnection (FR-002, US5).
 */
@Singleton
class BillingClientWrapper @Inject constructor(
    @ApplicationContext context: Context,
) : PurchasesUpdatedListener {

    data class PurchasesUpdate(val result: BillingResult, val purchases: List<Purchase>)

    private val _purchaseUpdates = MutableSharedFlow<PurchasesUpdate>(extraBufferCapacity = 16)

    /** Every PurchasesUpdatedListener callback, including cancellations and errors (FR-009). */
    val purchaseUpdates: SharedFlow<PurchasesUpdate> = _purchaseUpdates.asSharedFlow()

    val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val connectionMutex = Mutex()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _purchaseUpdates.tryEmit(PurchasesUpdate(result, purchases.orEmpty()))
    }

    /**
     * Connects if needed and returns the setup result. Serialized behind a mutex so
     * concurrent callers never race startConnection (FR-003).
     */
    suspend fun ensureConnected(): BillingResult = connectionMutex.withLock {
        if (client.isReady) {
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build()
        }
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onBillingServiceDisconnected() {
                    // Auto-reconnection is enabled on the client; the next call will
                    // find isReady == false and reconnect through this path if needed.
                }
            })
        }
    }
}
