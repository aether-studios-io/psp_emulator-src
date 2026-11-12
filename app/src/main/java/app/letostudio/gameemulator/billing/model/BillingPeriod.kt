package app.letostudio.gameemulator.billing.model

/** Billing cadence of a paywall plan (data-model.md). */
enum class BillingPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY,
    LIFETIME;

    companion object {
        /** Maps an ISO-8601 billing period from Play pricing phases, if recognizable. */
        fun fromIso(isoPeriod: String?): BillingPeriod? = when (isoPeriod) {
            "P1W", "P7D" -> WEEKLY
            "P1M", "P30D" -> MONTHLY
            "P1Y", "P12M", "P365D" -> YEARLY
            else -> null
        }
    }
}
