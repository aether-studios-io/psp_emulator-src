package app.letostudio.gameemulator.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No stacking: one full-screen ad at a time, whichever format claims first. */
class FullScreenAdStateTest {

    @Test
    fun `second claim fails while the first ad is showing`() {
        val state = FullScreenAdState()
        assertTrue(state.tryClaim())
        assertFalse(state.tryClaim())
        assertTrue(state.isShowing.value)
    }

    @Test
    fun `slot is free again after the ad finishes`() {
        val state = FullScreenAdState()
        state.tryClaim()
        state.onFinished()
        assertFalse(state.isShowing.value)
        assertTrue(state.tryClaim())
    }

    @Test
    fun `insets stay frozen while the ad shows and after it, until the bars are back`() {
        val state = FullScreenAdState()
        assertFalse(state.holdsInsets(statusBarVisible = true))
        state.tryClaim()
        assertTrue(state.holdsInsets(statusBarVisible = false))
        assertTrue(state.holdsInsets(statusBarVisible = true)) // still showing
        state.onFinished()
        // GMA's activity is still animating out with the bars hidden: keep holding.
        assertTrue(state.holdsInsets(statusBarVisible = false))
        // Bars back: release, and stay released.
        assertFalse(state.holdsInsets(statusBarVisible = true))
        assertFalse(state.holdsInsets(statusBarVisible = false))
    }
}
