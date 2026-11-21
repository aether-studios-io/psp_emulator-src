package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.model.BillingPeriod
import app.letostudio.gameemulator.domain.model.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KAN-151: the purchase screens read their product lists from Remote Config
 * `subscription_stores_config` (the IPTV format, a JSON array of stores). A blank or
 * unreadable value falls back to the [BillingConfig] catalogue, so the paywall never
 * goes empty because of the console.
 */
class StoreConfigParserTest {

    private val catalogue = BillingConfig.SUBS_PRODUCT_IDS + BillingConfig.INAPP_PRODUCT_IDS

    private val validJson = """
        [
          {"name":"paywall","type":"paywall","enabled":true,
           "items":["a.weekly3","a.monthly1","a.yearly1"],
           "promoted":"yearly1","trial_items":[]},
          {"name":"store","type":"store","enabled":true,
           "items":["a.weeklytrial","a.monthly1","a.yearly1","a.lifetime1"],
           "promoted":"lifetime1","trial_items":["a.weeklytrial"],"extra_field":42}
        ]
    """.trimIndent()

    @Test
    fun `valid JSON is used as written`() {
        val config = StoreConfigParser.resolve(validJson)
        val paywall = config.storeNamed(StoreConfig.PAYWALL)!!
        val store = config.storeNamed(StoreConfig.STORE)!!
        assertEquals(listOf("a.weekly3", "a.monthly1", "a.yearly1"), paywall.items)
        assertEquals(listOf("a.weeklytrial", "a.monthly1", "a.yearly1", "a.lifetime1"), store.items)
        assertEquals(listOf("a.weeklytrial"), store.trialItems)
        assertTrue(paywall.isPromoted("a.yearly1"))
        assertFalse(paywall.isPromoted("a.monthly1"))
        assertTrue(store.isPromoted("a.lifetime1"))
        assertEquals(
            listOf("a.weekly3", "a.monthly1", "a.yearly1", "a.weeklytrial", "a.lifetime1"),
            config.allProductIds(),
        )
    }

    @Test
    fun `blank or missing value falls back to the catalogue`() {
        for (raw in listOf(null, "", "   ")) {
            assertNull(StoreConfigParser.parse(raw))
            assertEquals(StoreConfigParser.defaultConfiguration(), StoreConfigParser.resolve(raw))
        }
    }

    @Test
    fun `broken JSON falls back to the catalogue`() {
        val broken = listOf(
            "[{\"name\":\"paywall\"",                       // truncated
            "{\"stores\":[]}",                               // object instead of the IPTV array
            "[{\"name\":\"paywall\",\"items\":\"a.monthly1\"}]", // wrong type for items
            "not json at all",
        )
        for (raw in broken) {
            assertNull(raw, StoreConfigParser.parse(raw))
            assertEquals(raw, StoreConfigParser.defaultConfiguration(), StoreConfigParser.resolve(raw))
        }
    }

    @Test
    fun `fallback paywall and store list the whole catalogue with the best value promoted`() {
        val fallback = StoreConfigParser.defaultConfiguration()
        for (name in listOf(StoreConfig.PAYWALL, StoreConfig.STORE)) {
            val store = fallback.storeNamed(name)!!
            assertEquals(catalogue, store.items)
            assertTrue(store.isPromoted(BillingConfig.BEST_VALUE_PRODUCT_ID))
            assertEquals(listOf(BillingConfig.PRODUCT_WEEKLY_TRIAL), store.trialItems)
        }
    }

    @Test
    fun `the in-code default JSON round-trips to the fallback`() {
        assertEquals(StoreConfigParser.defaultConfiguration(), StoreConfigParser.parse(StoreConfigParser.defaultJson()))
    }

    @Test
    fun `a disabled store is not used`() {
        val json = """[{"name":"paywall","type":"paywall","enabled":false,"items":["a.x"],"promoted":"x"}]"""
        assertNull(StoreConfigParser.resolve(json).storeNamed(StoreConfig.PAYWALL))
    }

    private fun plan(productId: String, period: BillingPeriod, hasFreeTrial: Boolean = false) = SubscriptionPlan(
        productId = productId,
        basePlanId = "base",
        offerToken = "token",
        name = productId,
        billingPeriod = period,
        formattedPrice = "$1.99",
        priceMicros = 1_990_000,
        currencyCode = "USD",
        hasFreeTrial = hasFreeTrial,
    )

    @Test
    fun `empty or broken Remote Config gives the paywall the catalogue order`() {
        val order = StoreConfigParser.resolve("").storeNamed(StoreConfig.PAYWALL)!!.items
        assertEquals(listOf(BillingConfig.PRO_WEEKLY, BillingConfig.PRO_MONTHLY, BillingConfig.PRO_LIFETIME), order)
        val weekly = plan(BillingConfig.PRO_WEEKLY, BillingPeriod.WEEKLY, hasFreeTrial = true)
        val monthly = plan(BillingConfig.PRO_MONTHLY, BillingPeriod.MONTHLY)
        val lifetime = plan(BillingConfig.PRO_LIFETIME, BillingPeriod.LIFETIME)
        assertEquals(
            listOf(BillingConfig.PRO_WEEKLY, BillingConfig.PRO_MONTHLY, BillingConfig.PRO_LIFETIME),
            BillingRepository.selectPaywallPlans(listOf(lifetime, monthly, weekly), order).map { it.productId },
        )
    }

    @Test
    fun `remote order drives the paywall and every returned plan stays listed`() {
        val order = StoreConfigParser.resolve(validJson).storeNamed(StoreConfig.PAYWALL)!!.items
        val weekly = plan("a.weekly3", BillingPeriod.WEEKLY)
        val monthly = plan("a.monthly1", BillingPeriod.MONTHLY)
        val yearly = plan("a.yearly1", BillingPeriod.YEARLY)
        val unlisted = plan("a.other", BillingPeriod.MONTHLY)
        // No usable trial: best value (last in the store) leads, unlisted products sort last.
        assertEquals(
            listOf("a.yearly1", "a.monthly1", "a.weekly3", "a.other"),
            BillingRepository.selectPaywallPlans(listOf(unlisted, weekly, monthly, yearly), order).map { it.productId },
        )
    }
}
