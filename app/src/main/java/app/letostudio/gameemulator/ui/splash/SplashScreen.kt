package app.letostudio.gameemulator.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R

/**
 * Phase-two cold-start splash: rendered as the very first Compose frame, which is the
 * moment the SYSTEM splash (Theme.App.Starting) dismisses. Background and icon
 * mirror the system splash exactly so the handoff is invisible; this phase then adds
 * what the system splash cannot host — the ads notice and a progress spinner.
 *
 * Shown until MainActivity's start gates resolve AND the minimum hold elapses (the
 * notice must be readable, not a one-frame flash). Google's app-open ads guidance uses
 * this same loading-screen-with-notice pattern.
 *
 * Colors are fixed to the DARK tokens whatever the in-app theme, matching the system
 * splash (a resource theme cannot read the DataStore theme setting) — a theme-correct
 * splash would mismatch its own phase one, which is worse than a consistent dark one.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    // Mirrors MainActivity's SPLASH_MIN_DURATION_MS so the bar completes exactly as
    // the splash hands off to the NavHost.
    progressDurationMillis: Int = 1_500,
) {
    // Smooth DETERMINATE fill over the known splash duration — an indeterminate bar
    // shuttles back and forth, which reads as jitter on a screen this sparse.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = progressDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground),
    ) {
        // The launcher icon rebuilt as a CIRCLE, layer by layer: adaptive background
        // (vector) under adaptive foreground (mipmap webp), clipped round. The composed
        // icon (R.mipmap.ic_launcher) cannot be used directly — on API 26+ it resolves
        // to an AdaptiveIconDrawable, which painterResource cannot load.
        //
        // Both layers are drawn at the adaptive MASK zoom (108/72): the canvas is 108dp
        // but a launcher mask only reveals the central 72dp, so without the zoom the
        // circle would show the icon smaller, with padding, unlike the launcher/system
        // splash. Changing ic_launcher_background/foreground updates this automatically.
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .align(Alignment.Center),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(AdaptiveMaskZoom),
            )
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(AdaptiveMaskZoom),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.splash_ads_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = SplashInk,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress.value },
                color = MaterialTheme.colorScheme.primary,
                trackColor = SplashTrack,
                // No M3 stop dot / progress-track gap — a plain continuous fill.
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .width(160.dp)
                    .clip(CircleShape),
            )
        }
    }
}

/** Token background/onBackground (dark) — hardcoded like themes.xml, see class doc. */
private val SplashBackground = Color(0xFF002470) // RetroNova primaryDeep
private val SplashInk = Color(0xFFFFFFFF)

/** Indeterminate-bar track: ink at low alpha so it reads on the dark background. */
private val SplashTrack = Color(0x40FFFFFF)

/** Adaptive canvas is 108dp, a launcher mask reveals the central 72dp — see icon Box. */
private const val AdaptiveMaskZoom = 108f / 72f

@Preview(name = "Splash", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SplashScreenPreview() {
    SplashScreen()
}
