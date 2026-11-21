package app.letostudio.gameemulator.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.components.RnDeviceShot
import app.letostudio.gameemulator.ui.components.RnGlowButton
import app.letostudio.gameemulator.ui.components.RnIcon
import app.letostudio.gameemulator.ui.components.RnMiniDpad
import app.letostudio.gameemulator.ui.components.RnMiniFace
import app.letostudio.gameemulator.ui.components.RnStoreBackdrop
import app.letostudio.gameemulator.ui.components.RnStoreBody
import app.letostudio.gameemulator.ui.components.RnStoreCard
import app.letostudio.gameemulator.ui.components.RnStoreChip
import app.letostudio.gameemulator.ui.components.RnStoreStage
import app.letostudio.gameemulator.ui.components.RnStoreTitle
import app.letostudio.gameemulator.ui.components.RnTextLink
import app.letostudio.gameemulator.ui.components.StoreGlow
import app.letostudio.gameemulator.ui.components.at
import app.letostudio.gameemulator.ui.theme.RnStoreTheme
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.ui.theme.StoreColors
import kotlinx.coroutines.launch

private data class Page(val title: Int, val highlight: Int, val body: Int, val glows: List<StoreGlow>)

private val Blue = Color(0xFF0070D0)
private val Navy = Color(0xFF004CA0)
private val Ember = Color(0xFFFF7A1A)

/** Set B (store style) copy and glows per page; art is in [Stage1]–[Stage3]. */
private val Pages = listOf(
    Page(R.string.onb1_title, R.string.onb1_title_hl, R.string.onb1_body, listOf(StoreGlow(40f, 170f, 320f, 260f, Blue, 0.75f), StoreGlow(200f, 420f, 200f, 180f, Ember, 0.35f))),
    Page(R.string.onb2_title, R.string.onb2_title_hl, R.string.onb2_body, listOf(StoreGlow(40f, 180f, 320f, 300f, Navy, 0.85f), StoreGlow(-60f, 460f, 200f, 180f, Ember, 0.32f))),
    Page(R.string.onb3_title, R.string.onb3_title_hl, R.string.onb3_body, listOf(StoreGlow(40f, 160f, 320f, 240f, Blue, 0.7f), StoreGlow(150f, 420f, 220f, 200f, Ember, 0.35f))),
)

@Composable
fun RnOnboardingRoute(onFinished: () -> Unit, viewModel: RnOnboardingViewModel = hiltViewModel()) = RnStoreTheme {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { viewModel.onPageShown(pager.currentPage) }
    Box(Modifier.fillMaxSize().background(RnTheme.colors.ground)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val page = Pages[index]
            Box(Modifier.fillMaxSize()) {
                RnStoreBackdrop(page.glows)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Spacer(Modifier.height(56.dp))
                    var titleLines by remember { mutableIntStateOf(2) }
                    RnStoreTitle(stringResource(page.title), stringResource(page.highlight), Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { titleLines = it }
                    Spacer(Modifier.height(18.dp))
                    // Design height (430) at most; shrinks only on short screens. Spare height
                    // goes below the body text, as in the design (footer pinned to the bottom).
                    RnStoreStage(390.dp, 430.dp, Modifier.fillMaxWidth().weight(1f, fill = false).height(430.dp)) {
                        when (index) {
                            0 -> Stage1()
                            1 -> Stage2()
                            else -> Stage3()
                        }
                    }
                    // Pages 2–3 run art to the stage's bottom edge; give the body air. A one-line
                    // title adds its missing line here so the body sits level on every page.
                    val titleLine = with(LocalDensity.current) { 36.sp.toDp() }
                    Spacer(Modifier.height(28.dp + titleLine * (2 - titleLines).coerceAtLeast(0)))
                    RnStoreBody(stringResource(page.body), Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    // room for the footer row (52 dp button + 32 dp bottom padding)
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
        RnTextLink(
            stringResource(R.string.onb_skip),
            { viewModel.onSkip(pager.currentPage, onFinished) },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            color = Color.White.copy(alpha = 0.72f),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).semantics { contentDescription = "Page ${pager.currentPage + 1} of 3" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(Pages.size) { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .animateContentSize()
                            .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) StoreColors.Highlight else Color.White.copy(alpha = 0.22f)),
                    )
                }
            }
            val last = pager.currentPage == Pages.lastIndex
            RnGlowButton(
                text = stringResource(if (last) R.string.onb_get_started else R.string.onb_next),
                onClick = {
                    if (last) viewModel.onGetStarted(onFinished) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                trailingIcon = if (last) null else R.drawable.ms_arrow_forward,
            )
        }
    }
}

/** Landscape game shot with the app's pause button and pad over it, chip, continue card. */
@Composable
private fun BoxScope.Stage1() {
    RnDeviceShot(
        R.drawable.obb1_land,
        stringResource(R.string.onb1_shot_cd),
        frameRadius = 30.dp,
        modifier = Modifier.at(20.dp, 34.dp).size(350.dp, 190.dp).graphicsLayer { rotationZ = -4f },
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset((-16).dp, 14.dp).size(34.dp).clip(CircleShape).background(RnTheme.colors.glass),
            contentAlignment = Alignment.Center,
        ) { RnIcon(R.drawable.ms_pause_fill, tint = Color.White, size = 20.dp) }
        RnMiniDpad(0.44f, Modifier.at(12.dp, 114.dp))
        RnMiniFace(0.44f, Modifier.align(Alignment.TopEnd).offset((-12).dp, 110.dp))
    }
    RnStoreChip(
        stringResource(R.string.onb1_chip),
        R.drawable.ms_bolt_fill,
        container = StoreColors.Orange,
        content = StoreColors.OnOrange,
        modifier = Modifier.align(Alignment.TopEnd).offset((-22).dp, 238.dp),
    )
    RnStoreCard(
        stringResource(R.string.onb1_card_eyebrow),
        stringResource(R.string.onb1_card_meta),
        R.drawable.ms_play_arrow_fill,
        iconContainer = StoreColors.Highlight,
        iconTint = Color(0xFF04122B),
        iconShape = RoundedCornerShape(19.dp),
        modifier = Modifier.at(22.dp, 290.dp).width(298.dp),
    )
}

/** Phone showing the import result, fading into the ground, with format chip and scan card. */
@Composable
private fun BoxScope.Stage2() {
    // The phone runs past the stage and fades out over its last 90 dp (alpha mask, so the
    // glow behind shows through instead of a solid band).
    Box(
        Modifier
            .matchParentSize()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 1f - 90.dp.toPx() / size.height
                drawRect(Brush.verticalGradient(0f to Color.Black, fade to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    ) {
        RnDeviceShot(
            R.drawable.obb2_phone,
            stringResource(R.string.onb2_shot_cd),
            frameRadius = 38.dp,
            alignTop = true,
            modifier = Modifier.at(82.dp, 14.dp).size(226.dp, 470.dp),
        )
    }
    Box(Modifier.matchParentSize()) {
        RnStoreChip(
            stringResource(R.string.onb2_chip),
            R.drawable.ms_album,
            container = Color.White,
            content = StoreColors.CardInk,
            iconTint = Navy,
            modifier = Modifier.align(Alignment.TopEnd).offset((-14).dp, 40.dp),
        )
        RnStoreCard(
            stringResource(R.string.onb2_card_eyebrow),
            stringResource(R.string.onb2_added),
            R.drawable.ms_check_circle_fill,
            iconContainer = Color(0xFFE4F4EA),
            iconTint = Color(0xFF18703F),
            modifier = Modifier.at(14.dp, 300.dp),
        )
    }
}

/** Racing shot, "Gamepad ready" chip and a wireless gamepad in front. */
@Composable
private fun BoxScope.Stage3() {
    RnDeviceShot(
        R.drawable.obb3_land,
        stringResource(R.string.onb3_shot_cd),
        frameRadius = 30.dp,
        modifier = Modifier.at(20.dp, 24.dp).size(350.dp, 190.dp).graphicsLayer { rotationZ = 4f },
    )
    RnStoreChip(
        stringResource(R.string.onb3_pill),
        R.drawable.ms_bluetooth,
        container = Color.White,
        content = StoreColors.CardInk,
        iconTint = Navy,
        modifier = Modifier.align(Alignment.TopEnd).offset((-18).dp, 222.dp),
    )
    Image(
        painterResource(R.drawable.obb3_pad),
        contentDescription = stringResource(R.string.onb3_pad_cd),
        modifier = Modifier.at(50.dp, 236.dp).size(290.dp, 174.dp).graphicsLayer { rotationZ = -6f },
    )
}
