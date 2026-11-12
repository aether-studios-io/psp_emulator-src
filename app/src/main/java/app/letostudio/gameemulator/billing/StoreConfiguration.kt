package app.letostudio.gameemulator.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Which products each purchase screen lists, read from Firebase Remote Config key
 * [StoreConfigParser.KEY] (`subscription_stores_config`) — the IPTV app's format, the
 * standard for every app since 2026-11-11 (Jira epic KAN-150):
 *
 * ```json
 * [{"name":"paywall","type":"paywall","enabled":true,
 *   "items":["<applicationId>.weeklytrial","<applicationId>.monthly1","<applicationId>.yearly1"],
 *   "promoted":"yearly1","trial_items":["<applicationId>.weeklytrial"]},
 *  {"name":"store","type":"store","enabled":true, "items":[...], "promoted":"yearly1","trial_items":[...]}]
 * ```
 *
 * - `items` = product IDs in display order for that screen (subscriptions and one-time
 *   products may be mixed; Play decides which ones the user's country can buy).
 * - `promoted` = suffix (or full ID) of the product that gets the "Best value" badge.
 * - `trial_items` = products that carry a trial offer. Informational only: the trial
 *   badge, copy and CTA still come from [app.letostudio.gameemulator.domain.model.SubscriptionPlan.offersTrial]
 *   (Play free phase AND the local no-purchase hint), per the billing trial rule.
 */
@Serializable
data class StoreConfiguration(val stores: List<StoreConfig>) {

    /** The enabled store with this name, or null. */
    fun storeNamed(name: String): StoreConfig? = stores.firstOrNull { it.name == name && it.enabled }

    /** Every product ID any enabled store lists, without duplicates. */
    fun allProductIds(): List<String> = stores.filter { it.enabled }.flatMap { it.items }.distinct()
}

@Serializable
data class StoreConfig(
    val name: String,
    val items: List<String>,
    val promoted: String,
    @SerialName("trial_items")
    val trialItems: List<String> = emptyList(),
    val type: String,
    val enabled: Boolean,
) {
    /** "Best value" badge: the promoted value is a product ID or its suffix (e.g. "yearly1"). */
    fun isPromoted(productId: String): Boolean =
        promoted.isNotEmpty() && (productId == promoted || productId.endsWith(promoted))

    /** Display position on this screen; products the store does not list sort last. */
    fun orderOf(productId: String): Int = items.indexOf(productId).let { if (it == -1) Int.MAX_VALUE else it }

    companion object {
        const val PAYWALL = "paywall"
        const val STORE = "store"
    }
}

/**
 * Parses the Remote Config value the IPTV way: a blank or unreadable value falls back to
 * [defaultConfiguration] (the catalogue in [BillingConfig]). Pure Kotlin so it is
 * unit-tested on the JVM.
 */
object StoreConfigParser {

    /** Remote Config key holding the JSON array of stores. */
    const val KEY = "subscription_stores_config"

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StoreConfig.serializer())

    /** The configured stores, or null when [raw] is blank or not a readable store array. */
    fun parse(raw: String?): StoreConfiguration? {
        if (raw.isNullOrBlank()) return null
        return try {
            StoreConfiguration(json.decodeFromString(listSerializer, raw))
        } catch (e: IllegalArgumentException) {
            // SerializationException extends IllegalArgumentException: malformed JSON,
            // a missing field or a wrong type all land here.
            null
        }
    }

    /** What the app uses: the remote value when it parses, else the in-code catalogue. */
    fun resolve(raw: String?): StoreConfiguration = parse(raw) ?: defaultConfiguration()

    /** The in-code catalogue: both screens list [BillingConfig]'s products in its order. */
    fun defaultConfiguration(): StoreConfiguration {
        val items = BillingConfig.SUBS_PRODUCT_IDS + BillingConfig.INAPP_PRODUCT_IDS
        val trialItems = listOf(BillingConfig.PRODUCT_WEEKLY_TRIAL).filter { it in items }
        fun store(name: String) = StoreConfig(
            name = name,
            items = items,
            promoted = BillingConfig.BEST_VALUE_PRODUCT_ID,
            trialItems = trialItems,
            type = name,
            enabled = true,
        )
        return StoreConfiguration(listOf(store(StoreConfig.PAYWALL), store(StoreConfig.STORE)))
    }

    /** [defaultConfiguration] as the JSON the console expects — the Remote Config in-code default. */
    fun defaultJson(): String = json.encodeToString(listSerializer, defaultConfiguration().stores)
}
