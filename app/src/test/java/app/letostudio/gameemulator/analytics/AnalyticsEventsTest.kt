package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.analytics.AnalyticsEvents.ErrorKind
import app.letostudio.gameemulator.util.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GA4 limits + taxonomy closure (contracts/analytics-events.md). GA4 truncates or
 * drops violations silently server-side — this suite is the only feedback that exists.
 */
class AnalyticsEventsTest {

    private val nameRule = Regex("[a-z][a-z0-9_]*")

    @Test
    fun `event names respect GA4 limits and naming rules`() {
        AnalyticsEvents.ALL_EVENT_NAMES.forEach { name ->
            assertTrue("'$name' too long", name.length <= AnalyticsConfig.MAX_EVENT_NAME_LENGTH)
            assertTrue("'$name' breaks naming rules", name.matches(nameRule))
        }
        // The set is closed and duplicate-free.
        assertEquals(
            AnalyticsEvents.ALL_EVENT_NAMES.size,
            AnalyticsEvents.ALL_EVENT_NAMES.toSet().size,
        )
    }

    @Test
    fun `parameter names respect GA4 limits and naming rules`() {
        AnalyticsEvents.ALL_PARAM_NAMES.forEach { name ->
            assertTrue("'$name' too long", name.length <= AnalyticsConfig.MAX_PARAM_NAME_LENGTH)
            assertTrue("'$name' breaks naming rules", name.matches(nameRule))
        }
        assertEquals(
            AnalyticsEvents.ALL_PARAM_NAMES.size,
            AnalyticsEvents.ALL_PARAM_NAMES.toSet().size,
        )
    }

    /**
     * Every enumerated wire value is snake_case and within the value limit — and every
     * parameter-typed value in the taxonomy IS one of these enums, a boolean, or a
     * bounded number. No free-text parameter type exists (FR-015/SC-005): the manager's
     * helper signatures only accept these types, so this sweep plus the signatures is
     * the structural no-PII guarantee.
     */
    @Test
    fun `every enum wire value is bounded and snake_case`() {
        val allWires =
            ErrorKind.entries.map { it.wire } +
                AnalyticsEvents.PaywallTrigger.entries.map { it.wire } +
                AnalyticsEvents.AdFormat.entries.map { it.wire } +
                listOf(AnalyticsEvents.RESULT_SUCCESS, AnalyticsEvents.RESULT_ERROR)
        allWires.forEach { wire ->
            assertTrue("'$wire' too long", wire.length <= AnalyticsConfig.MAX_PARAM_VALUE_LENGTH)
            assertTrue("'$wire' not snake_case", wire.matches(nameRule))
        }
    }

    /** ErrorKind covers every AppError branch — the mapping cannot drift. */
    @Test
    fun `error kind maps every AppError variant`() {
        assertEquals(ErrorKind.NETWORK, ErrorKind.from(AppError.Network))
        assertEquals(ErrorKind.HTTP, ErrorKind.from(AppError.Http(500)))
        assertEquals(ErrorKind.PARSE, ErrorKind.from(AppError.Parse))
        assertEquals(ErrorKind.AUTH, ErrorKind.from(AppError.Auth))
        assertEquals(ErrorKind.UNKNOWN, ErrorKind.from(AppError.Unknown(RuntimeException())))
    }

    @Test
    fun `item count buckets are lower bounds from the contract`() {
        assertEquals(0L, AnalyticsEvents.bucketCount(0))
        assertEquals(1L, AnalyticsEvents.bucketCount(1))
        assertEquals(1L, AnalyticsEvents.bucketCount(9))
        assertEquals(10L, AnalyticsEvents.bucketCount(10))
        assertEquals(10L, AnalyticsEvents.bucketCount(99))
        assertEquals(100L, AnalyticsEvents.bucketCount(542))
        assertEquals(1_000L, AnalyticsEvents.bucketCount(9_999))
        assertEquals(10_000L, AnalyticsEvents.bucketCount(123_456))
    }

    @Test
    fun `query length buckets are lower bounds from the contract`() {
        assertEquals(1L, AnalyticsEvents.bucketQueryLength(1))
        assertEquals(2L, AnalyticsEvents.bucketQueryLength(2))
        assertEquals(3L, AnalyticsEvents.bucketQueryLength(4))
        assertEquals(5L, AnalyticsEvents.bucketQueryLength(9))
        assertEquals(10L, AnalyticsEvents.bucketQueryLength(64))
    }
}
