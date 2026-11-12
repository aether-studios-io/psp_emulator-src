package app.letostudio.gameemulator.ads

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * True while an SDK full-screen ad (interstitial / app-open) is on screen. Two jobs:
 *
 * 1. No stacking: the slot is CLAIMED synchronously ([tryClaim]) before any suspension
 *    in the show path, so an interstitial and an app-open ad can never both pass their
 *    gates and show on top of each other. All callers run on Main, so claim is atomic.
 * 2. Steady insets, below.
 *
 * GMA's AdActivity is TRANSLUCENT, so MainActivity stays visible underneath it, and the
 * ad hides the system bars — which re-dispatches zero bar insets to OUR window. Every
 * inset-padded layout (bottom bar, top bars, paywall) then jumps while the ad animates
 * in, and jumps back as it animates out. MainActivity observes this flag and freezes
 * its insets for the duration (see installFullScreenAdInsetsFreeze).
 *
 * Native full-screen is NOT tracked: it is a Compose screen in our own window.
 */
@Singleton
class FullScreenAdState @Inject constructor() {

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    /**
     * Takes the full-screen slot; false when another ad already holds it (show nothing).
     * Every successful claim must end in [onFinished] — from the dismiss/failed-to-show
     * callback, or directly when the caller bails out before `show()`.
     */
    fun tryClaim(): Boolean {
        if (_isShowing.value) return false
        _isShowing.value = true
        return true
    }

    /** Call on dismiss AND on failed-to-show. */
    /**
     * Uptime until which insets stay frozen after the ad finished (see [holdsInsets]).
     * Set in the same call that clears [isShowing], so there is no gap where a
     * zero-bar insets dispatch can slip through (seen on a Pixel 6a in crochet, built from this template: one frame
     * with the bars' insets gone right after an app-open dismiss).
     */
    @Volatile
    private var holdInsetsUntilMs = 0L

    fun onFinished() {
        holdInsetsUntilMs = SystemClock.uptimeMillis() + INSETS_HOLD_AFTER_AD_MS
        _isShowing.value = false
    }

    /**
     * True while the window's insets must stay frozen: an ad is up, or it just finished
     * and the system bars are not back yet (GMA reports the dismiss while its activity
     * still animates out with the bars hidden), for at most [INSETS_HOLD_AFTER_AD_MS].
     */
    fun holdsInsets(statusBarVisible: Boolean): Boolean {
        if (_isShowing.value) return true
        if (statusBarVisible) {
            holdInsetsUntilMs = 0L
            return false
        }
        return SystemClock.uptimeMillis() < holdInsetsUntilMs
    }

    companion object {
        const val INSETS_HOLD_AFTER_AD_MS = 1_500L
    }
}
