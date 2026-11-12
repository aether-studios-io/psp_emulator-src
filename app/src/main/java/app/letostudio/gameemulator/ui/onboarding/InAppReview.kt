package app.letostudio.gameemulator.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import app.letostudio.gameemulator.BuildConfig
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager

/**
 * Asks Play to show the in-app review dialog, ported from iptv v1's onboarding.
 *
 * Play — not this call — decides whether anything appears: the flow is quota-limited
 * per user and silently no-ops for sideloaded builds, so callers must treat it as
 * fire-and-forget. Nothing may be gated on it, and the user must never be prompted to
 * rate beforehand (Play's In-App Review policy).
 *
 * Debug builds get [FakeReviewManager], which succeeds without drawing anything at all
 * (launchReviewFlow only bumps an internal counter) — so a debug run proves the trigger
 * point fires and nothing more. The real sheet needs a Play-installed build.
 */
fun requestInAppReview(context: Context) {
    // Play needs the hosting Activity; without one there is nothing to launch into.
    val activity = context.findActivity() ?: return
    val manager = if (BuildConfig.DEBUG) {
        FakeReviewManager(context)
    } else {
        ReviewManagerFactory.create(context)
    }
    manager.requestReviewFlow().addOnCompleteListener { task ->
        // A failed request is normal (no quota, no Play account) — stay silent.
        if (task.isSuccessful) {
            manager.launchReviewFlow(activity, task.result)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
