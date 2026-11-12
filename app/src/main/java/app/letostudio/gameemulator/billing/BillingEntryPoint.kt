package app.letostudio.gameemulator.billing

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-scoped accessor for AppNavHost (same pattern as AdsEntryPoint), used to
 * premium-gate navigation into paid features.
 *
 * Exposes the repository rather than EntitlementManager on purpose:
 * [BillingRepository.currentPremiumStatus] wraps the Room read in the IO
 * dispatcher, and the nav host reads it from a Main-dispatched compose scope.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillingEntryPoint {
    fun billingRepository(): BillingRepository
}
