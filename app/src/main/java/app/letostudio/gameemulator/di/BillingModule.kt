package app.letostudio.gameemulator.di

import android.content.Context
import androidx.room.Room
import app.letostudio.gameemulator.billing.data.BillingDatabase
import app.letostudio.gameemulator.billing.data.EntitlementDao
import app.letostudio.gameemulator.billing.data.PurchaseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Billing persistence wiring. BillingClientWrapper, BillingRepository and
 * EntitlementManager are @Singleton constructor-injected — no providers needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingDatabase(@ApplicationContext context: Context): BillingDatabase =
        Room.databaseBuilder(context, BillingDatabase::class.java, "billing.db").build()

    @Provides
    fun providePurchaseDao(db: BillingDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideEntitlementDao(db: BillingDatabase): EntitlementDao = db.entitlementDao()
}
