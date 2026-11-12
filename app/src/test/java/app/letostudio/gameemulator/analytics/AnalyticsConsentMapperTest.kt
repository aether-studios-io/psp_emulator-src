package app.letostudio.gameemulator.analytics

import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The full truth table from contracts/consent-mapping.md. Highest-value test in the
 * feature — the mapper is the legally load-bearing logic, isolated as a pure function
 * exactly so this suite can be exhaustive.
 *
 * What this suite proves: the implementation matches the CONTRACT table — fail-closed
 * rows are closed, purpose derivation branches correctly, refactors cannot silently
 * change a decision. What it cannot prove: that the table's purpose numbers match
 * Google's semantics (contract's ⚠️ note) — only the granular DebugView pass can.
 */
class AnalyticsConsentMapperTest {

    private fun map(
        consentInfoUpdated: Boolean = true,
        formRequired: Boolean = true,
        tcf: String? = null,
    ): AnalyticsConsent = AnalyticsConsentMapper.map(
        ConsentInput(
            consentInfoUpdated = consentInfoUpdated,
            formRequired = formRequired,
            tcfPurposeConsents = tcf,
        ),
    )

    /** Purposes granted at the given (1-based) numbers, denied elsewhere; length 11. */
    private fun tcfGranting(vararg purposes: Int): String =
        buildString { repeat(11) { i -> append(if ((i + 1) in purposes) '1' else '0') } }

    private fun AnalyticsConsent.assertAll(status: ConsentStatus) {
        assertEquals(status, analyticsStorage)
        assertEquals(status, adStorage)
        assertEquals(status, adUserData)
        assertEquals(status, adPersonalization)
    }

    // --- Row 1: consent info update failed -> fail closed ---

    @Test
    fun `row1 update failed - all denied regardless of everything else`() {
        map(consentInfoUpdated = false, formRequired = false, tcf = tcfGranting(1, 3, 4, 7, 8, 9))
            .assertAll(ConsentStatus.DENIED)
    }

    // --- Row 2: no form required (non-EEA) -> all granted ---

    @Test
    fun `row2 no form required and no tc string - all granted`() {
        map(formRequired = false, tcf = null).assertAll(ConsentStatus.GRANTED)
    }

    // --- Row 3: form required, no response yet -> fail closed ---

    @Test
    fun `row3 form required without tc string - all denied`() {
        map(formRequired = true, tcf = null).assertAll(ConsentStatus.DENIED)
    }

    @Test
    fun `row3 blank tc string treated as absent`() {
        map(tcf = "").assertAll(ConsentStatus.DENIED)
        map(tcf = "   ").assertAll(ConsentStatus.DENIED)
    }

    @Test
    fun `malformed tc string treated as absent - fail closed, never partially granted`() {
        map(tcf = "1x110011011").assertAll(ConsentStatus.DENIED)
    }

    @Test
    fun `short tc string treated as absent - fail closed`() {
        // Purposes up to 9 are consulted; an 8-char string cannot answer for purpose 9.
        map(tcf = "11111111").assertAll(ConsentStatus.DENIED)
    }

    // --- Row 4: per-purpose derivation ---

    @Test
    fun `everything granted when all purposes granted`() {
        map(tcf = tcfGranting(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)).assertAll(ConsentStatus.GRANTED)
    }

    @Test
    fun `all purposes denied - all signals denied`() {
        map(tcf = tcfGranting()).assertAll(ConsentStatus.DENIED)
    }

    @Test
    fun `adStorage granted by purpose 1 alone`() {
        val consent = map(tcf = tcfGranting(1))
        assertEquals(ConsentStatus.GRANTED, consent.adStorage)
        assertEquals(ConsentStatus.DENIED, consent.analyticsStorage)
        assertEquals(ConsentStatus.DENIED, consent.adUserData)
        assertEquals(ConsentStatus.DENIED, consent.adPersonalization)
    }

    @Test
    fun `analyticsStorage needs purpose 1 AND a measurement purpose`() {
        // 8 or 9 without 1: denied. 1 with 8: granted. 1 with 9: granted.
        assertEquals(ConsentStatus.DENIED, map(tcf = tcfGranting(8, 9)).analyticsStorage)
        assertEquals(ConsentStatus.GRANTED, map(tcf = tcfGranting(1, 8)).analyticsStorage)
        assertEquals(ConsentStatus.GRANTED, map(tcf = tcfGranting(1, 9)).analyticsStorage)
    }

    @Test
    fun `adUserData needs purpose 1 AND purpose 7`() {
        assertEquals(ConsentStatus.DENIED, map(tcf = tcfGranting(7)).adUserData)
        assertEquals(ConsentStatus.DENIED, map(tcf = tcfGranting(1)).adUserData)
        assertEquals(ConsentStatus.GRANTED, map(tcf = tcfGranting(1, 7)).adUserData)
    }

    @Test
    fun `adPersonalization needs purposes 3 AND 4, independent of purpose 1`() {
        assertEquals(ConsentStatus.DENIED, map(tcf = tcfGranting(3)).adPersonalization)
        assertEquals(ConsentStatus.DENIED, map(tcf = tcfGranting(4)).adPersonalization)
        assertEquals(ConsentStatus.GRANTED, map(tcf = tcfGranting(3, 4)).adPersonalization)
    }

    /** The case that proves canRequestAds would have been the wrong proxy (contract). */
    @Test
    fun `ad purposes granted but measurement denied - ads granted, analytics denied`() {
        val consent = map(tcf = tcfGranting(1, 3, 4, 7))
        assertEquals(ConsentStatus.GRANTED, consent.adStorage)
        assertEquals(ConsentStatus.GRANTED, consent.adUserData)
        assertEquals(ConsentStatus.GRANTED, consent.adPersonalization)
        assertEquals(ConsentStatus.DENIED, consent.analyticsStorage)
    }

    /** The inverse: measurement granted, personalised ads refused. */
    @Test
    fun `measurement granted but personalisation refused - analytics granted, personalization denied`() {
        val consent = map(tcf = tcfGranting(1, 8, 9))
        assertEquals(ConsentStatus.GRANTED, consent.analyticsStorage)
        assertEquals(ConsentStatus.GRANTED, consent.adStorage)
        assertEquals(ConsentStatus.DENIED, consent.adPersonalization)
        assertEquals(ConsentStatus.DENIED, consent.adUserData)
    }
}
