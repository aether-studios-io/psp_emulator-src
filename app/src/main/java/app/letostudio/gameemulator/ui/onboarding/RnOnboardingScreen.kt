package app.letostudio.gameemulator.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnIcon
import app.letostudio.gameemulator.ui.components.RnTextLink
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme
import kotlinx.coroutines.launch

private data class Page(val image: Int, val title: Int, val body: Int)

private val Pages = listOf(
    Page(R.drawable.ob1_photo, R.string.onb1_title, R.string.onb1_body),
    Page(R.drawable.ob2_photo, R.string.onb2_title, R.string.onb2_body),
    Page(R.drawable.ob3_photo, R.string.onb3_title, R.string.onb3_body),
)

@Composable
fun RnOnboardingRoute(onFinished: () -> Unit, viewModel: RnOnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { viewModel.onPageShown(pager.currentPage) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            RnTextLink(stringResource(R.string.onb_skip), { viewModel.onSkip(pager.currentPage, onFinished) }, color = RnTheme.colors.inkMuted)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = Pages[index]
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(28.dp))
                        .background(RnTheme.colors.tint),
                ) {
                    Image(painterResource(page.image), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    when (index) {
                        0 -> Overlay1()
                        1 -> Overlay2()
                        else -> Overlay3()
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(page.title), style = RnTheme.type.display, color = RnTheme.colors.ink)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(page.body), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
                Spacer(Modifier.height(16.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).semantics { contentDescription = "Page ${pager.currentPage + 1} of 3" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(Pages.size) { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .animateContentSize()
                            .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(RnRadius.pill))
                            .background(if (active) RnTheme.colors.primaryStrong else RnTheme.colors.line),
                    )
                }
            }
            val last = pager.currentPage == Pages.lastIndex
            RnButton(
                text = stringResource(if (last) R.string.onb_get_started else R.string.onb_next),
                onClick = {
                    if (last) viewModel.onGetStarted(onFinished) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                leadingIcon = null,
            )
        }
    }
}

@Composable
private fun BoxScope.Overlay1() {
    Row(
        Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(RnRadius.pill)).background(Color(0xFFFF9A4D)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RnIcon(R.drawable.ms_bolt_fill, tint = Color(0xFF1A1006), size = 18.dp)
        Text(stringResource(R.string.onb1_chip), style = RnTheme.type.label, color = Color(0xFF1A1006))
    }
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(RnRadius.xl))
            .background(Color(0xD9081430))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(RnRadius.xl))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF6AAEFF)), contentAlignment = Alignment.Center) {
            RnIcon(R.drawable.ms_play_arrow_fill, tint = Color(0xFF04122B), size = 24.dp)
        }
        Column {
            Text(stringResource(R.string.onb1_card_eyebrow).uppercase(), style = RnTheme.type.overline, color = Color(0xFFBFD8FF))
            Text(stringResource(R.string.onb1_card_meta), style = RnTheme.type.subtitle, color = Color.White)
        }
    }
}

@Composable
private fun BoxScope.Overlay2() {
    Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ISO", "CSO", "PBP").forEach {
            Text(
                it,
                style = RnTheme.type.label,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(RnRadius.pill)).background(Color(0xB3081430)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    Row(
        Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(RnRadius.pill)).background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RnIcon(R.drawable.ms_check_circle_fill, tint = Color(0xFF18703F), size = 20.dp)
        Text(stringResource(R.string.onb2_added), style = RnTheme.type.label, color = Color(0xFF0F1724))
    }
}

@Composable
private fun BoxScope.Overlay3() {
    Row(
        Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(RnRadius.pill)).background(Color(0xD9081430)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RnIcon(R.drawable.ms_sports_esports_fill, tint = Color(0xFF6AAEFF), size = 20.dp)
        Text(stringResource(R.string.onb3_pill), style = RnTheme.type.label, color = Color.White)
        Spacer(Modifier.width(2.dp))
    }
}
