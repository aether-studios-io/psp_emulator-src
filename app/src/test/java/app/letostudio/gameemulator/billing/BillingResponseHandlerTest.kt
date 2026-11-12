package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.model.BillingError
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-020..FR-022 (SC-002): every BillingResponseCode maps to a typed error with the
 * right retry classification, and withRetry applies the research.md backoff strategy.
 */
class BillingResponseHandlerTest {

    private val handler = BillingResponseHandler()

    private fun result(code: Int, debug: String = ""): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(debug).build()

    // --- error mapping -----------------------------------------------------------------

    @Test
    fun `retriable codes map to retriable errors`() {
        val retriable = mapOf(
            BillingResponseCode.SERVICE_DISCONNECTED to BillingError.ServiceDisconnected,
            BillingResponseCode.SERVICE_UNAVAILABLE to BillingError.ServiceUnavailable,
            BillingError.SERVICE_TIMEOUT_CODE to BillingError.ServiceTimeout,
            BillingResponseCode.NETWORK_ERROR to BillingError.NetworkError,
        )
        retriable.forEach { (code, expected) ->
            val error = handler.toError(result(code))
            assertEquals(expected, error)
            assertTrue("code $code must be retriable", error.isRetriable)
        }
        // ERROR (6) is retriable but carries the debug message.
        val generic = handler.toError(result(BillingResponseCode.ERROR, "boom"))
        assertTrue(generic is BillingError.GenericError)
        assertTrue(generic.isRetriable)
    }

    @Test
    fun `non-retriable codes map to non-retriable errors`() {
        val nonRetriable = mapOf(
            BillingResponseCode.BILLING_UNAVAILABLE to BillingError.BillingUnavailable::class,
            BillingResponseCode.FEATURE_NOT_SUPPORTED to BillingError.FeatureNotSupported::class,
            BillingResponseCode.DEVELOPER_ERROR to BillingError.DeveloperError::class,
            BillingResponseCode.ITEM_UNAVAILABLE to BillingError.ItemUnavailable::class,
            BillingResponseCode.ITEM_ALREADY_OWNED to BillingError.ItemAlreadyOwned::class,
            BillingResponseCode.ITEM_NOT_OWNED to BillingError.ItemNotOwned::class,
        )
        nonRetriable.forEach { (code, expectedClass) ->
            val error = handler.toError(result(code))
            assertEquals(expectedClass, error::class)
            assertFalse("code $code must not be retriable", error.isRetriable)
        }
    }

    @Test
    fun `unknown code maps to Unknown and is not retried`() {
        val error = handler.toError(result(99, "??"))
        assertTrue(error is BillingError.Unknown)
        assertFalse(error.isRetriable)
        assertEquals(99, error.responseCode)
    }

    @Test
    fun `developer error keeps the debug message for logging but not for the user`() {
        val error = handler.toError(result(BillingResponseCode.DEVELOPER_ERROR, "bad params"))
        assertEquals("bad params", (error as BillingError.DeveloperError).debugMessage)
    }

    // --- retry strategy ----------------------------------------------------------------

    @Test
    fun `retriable error is retried up to 4 attempts for background operations`() = runTest {
        var attempts = 0
        val outcome = handler.withRetry {
            attempts++
            BillingResponseHandler.BillingCall<Unit>(result(BillingResponseCode.NETWORK_ERROR), null)
        }
        assertEquals(BillingResponseHandler.MAX_ATTEMPTS_BACKGROUND, attempts)
        assertFalse(outcome.isOk)
    }

    @Test
    fun `user-initiated operations retry at most once`() = runTest {
        var attempts = 0
        handler.withRetry(userInitiated = true) {
            attempts++
            BillingResponseHandler.BillingCall<Unit>(result(BillingResponseCode.SERVICE_UNAVAILABLE), null)
        }
        assertEquals(BillingResponseHandler.MAX_ATTEMPTS_USER, attempts)
    }

    @Test
    fun `non-retriable error returns after a single attempt`() = runTest {
        var attempts = 0
        handler.withRetry {
            attempts++
            BillingResponseHandler.BillingCall<Unit>(result(BillingResponseCode.BILLING_UNAVAILABLE), null)
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `user cancellation is never retried`() = runTest {
        var attempts = 0
        handler.withRetry {
            attempts++
            BillingResponseHandler.BillingCall<Unit>(result(BillingResponseCode.USER_CANCELED), null)
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `success stops retrying and returns the payload`() = runTest {
        var attempts = 0
        val outcome = handler.withRetry {
            attempts++
            if (attempts < 3) {
                BillingResponseHandler.BillingCall(result(BillingResponseCode.NETWORK_ERROR), null)
            } else {
                BillingResponseHandler.BillingCall(result(BillingResponseCode.OK), "payload")
            }
        }
        assertEquals(3, attempts)
        assertTrue(outcome.isOk)
        assertEquals("payload", outcome.value)
    }
}
