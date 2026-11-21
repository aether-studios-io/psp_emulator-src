package app.letostudio.gameemulator.ui.paywall

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.billing.BillingConfig
import app.letostudio.gameemulator.monetization.PlanKind
import app.letostudio.gameemulator.monetization.PlanOption
import app.letostudio.gameemulator.ui.components.RnBadge
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnIconButton
import app.letostudio.gameemulator.ui.components.RnIconButtonStyle
import app.letostudio.gameemulator.ui.components.RnIconTile
import app.letostudio.gameemulator.ui.components.RnNote
import app.letostudio.gameemulator.ui.components.RnPlanCard
import app.letostudio.gameemulator.ui.components.RnTextLink
import app.letostudio.gameemulator.ui.theme.RnTheme
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import app.letostudio.gameemulator.ui.components.RnGlowButton
import app.letostudio.gameemulator.ui.components.RnStoreBackdrop
import app.letostudio.gameemulator.ui.components.RnStoreStage
import app.letostudio.gameemulator.ui.components.RnStoreTitle
import app.letostudio.gameemulator.ui.components.StoreGlow
import app.letostudio.gameemulator.ui.components.at
import app.letostudio.gameemulator.ui.theme.RnStoreTheme
import app.letostudio.gameemulator.ui.theme.StoreColors

/** Benefits shipped in this build (screens.md S04: only promise what exists). */
private val MvpBenefits = listOf(
    R.drawable.ms_block to R.string.paywall_benefit_no_ads,
    R.drawable.ms_save to R.string.paywall_benefit_slots,
)

@Composable
fun RnPaywallRoute(onClose: () -> Unit, snackbar: SnackbarHostState, viewModel: RnPaywallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                PaywallEvent.Close -> onClose()
                PaywallEvent.Pending -> snackbar.showSnackbar(res.getString(R.string.paywall_purchase_pending))
                PaywallEvent.Failed -> snackbar.showSnackbar(res.getString(R.string.paywall_purchase_error))
                is PaywallEvent.OpenUrl -> context.openUrl(e.url)
            }
        }
    }
    BackHandler(onBack = viewModel::close)
    RnPaywallScreen(
        state = state,
        onClose = viewModel::close,
        onSelect = viewModel::select,
        onCta = { (context as? Activity)?.let(viewModel::purchase) },
        onRetry = viewModel::load,
        onTerms = { context.openUrl(BillingConfig.TERMS_OF_SERVICE_URL) },
        onPrivacy = { context.openUrl(BillingConfig.PRIVACY_POLICY_URL) },
        onCancelAnytime = viewModel::cancelAnytime,
    )
}

fun android.content.Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(this, R.string.settings_no_browser, android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RnPaywallScreen(
    state: PaywallUiState,
    onClose: () -> Unit,
    onSelect: (PlanKind) -> Unit,
    onCta: () -> Unit,
    onRetry: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onCancelAnytime: () -> Unit,
) = RnStoreTheme {
    Box(Modifier.fillMaxSize()) {
        RnStoreBackdrop(PaywallGlows)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(270.dp)) {
                RnStoreStage(390.dp, 270.dp, Modifier.fillMaxSize()) {
                    Cover(R.drawable.pw_cover_left, Modifier.at(34.dp, 74.dp).size(132.dp, 176.dp).graphicsLayer { rotationZ = -10f })
                    Cover(R.drawable.pw_cover_right, Modifier.align(Alignment.TopEnd).offset((-34).dp, 74.dp).size(132.dp, 176.dp).graphicsLayer { rotationZ = 10f })
                    Cover(
                        R.drawable.pw_cover_center,
                        Modifier.at(121.dp, 48.dp).size(148.dp, 198.dp),
                        stringResource(R.string.paywall_covers_cd),
                    )
                }
                RnIconButton(
                    R.drawable.ms_close,
                    stringResource(R.string.cd_close),
                    onClose,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    style = RnIconButtonStyle.Glass,
                )
                RnBadge(
                    stringResource(R.string.paywall_pro_badge),
                    Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 16.dp),
                    container = StoreColors.Highlight,
                    content = Color(0xFF04122B),
                )
            }
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RnStoreTitle(stringResource(R.string.paywall_title), stringResource(R.string.paywall_title_hl), Modifier.fillMaxWidth())
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvpBenefits.forEach { (icon, text) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RnIconTile(icon, size = 32.dp, container = StoreColors.Highlight.copy(alpha = 0.16f), tint = StoreColors.Highlight, iconSize = 20.dp)
                            Text(stringResource(text), style = RnTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium), color = Color.White)
                        }
                    }
                }
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = StoreColors.Highlight)
                    }
                    state.error || state.plans.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RnNote(stringResource(R.string.paywall_plans_error), icon = R.drawable.ms_error)
                        RnButton(stringResource(R.string.retry), onRetry, small = true, style = RnButtonStyle.Secondary)
                    }
                    else -> Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.plans.forEach { PlanRow(it, state.selected == it.kind, onSelect) }
                    }
                }
                val option = state.selectedOption
                RnGlowButton(
                    text = ctaLabel(option),
                    onClick = onCta,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = option != null,
                    loading = state.purchasing,
                )
                if (state.showLifetimeNote) RnNote(stringResource(R.string.paywall_lifetime_cancel_note))
                Text(
                    stringResource(R.string.paywall_subscription_terms),
                    style = RnTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
                    color = StoreColors.Fine,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FooterLink(stringResource(R.string.paywall_terms), onTerms)
                    Dot()
                    FooterLink(stringResource(R.string.paywall_privacy), onPrivacy)
                    Dot()
                    FooterLink(stringResource(R.string.paywall_cancel_anytime), onCancelAnytime)
                }
            }
        }
    }
}

private val PaywallGlows = listOf(
    StoreGlow(30f, 40f, 330f, 230f, Color(0xFF0070D0), 0.8f),
    StoreGlow(230f, 180f, 160f, 140f, Color(0xFFFF7A1A), 0.4f),
)

/** Tilted game cover in the hero. */
@Composable
private fun Cover(@DrawableRes image: Int, modifier: Modifier, contentDescription: String? = null) {
    val shape = RoundedCornerShape(20.dp)
    Image(
        painterResource(image),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.18f)), shape),
    )
}

@Composable
private fun Dot() = Text("·", style = RnTheme.type.caption, color = StoreColors.Fine)

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    RnTextLink(text, onClick, color = StoreColors.Fine, underline = true)
}

@Composable
private fun PlanRow(option: PlanOption, selected: Boolean, onSelect: (PlanKind) -> Unit) {
    val p = option.plan
    when (option.kind) {
        PlanKind.WEEKLY -> RnPlanCard(
            name = stringResource(R.string.plan_weekly),
            note = if (option.offersTrial) stringResource(R.string.plan_weekly_note_trial, p.formattedPrice) else stringResource(R.string.plan_weekly_note),
            price = p.formattedPrice,
            per = stringResource(R.string.plan_per_week),
            selected = selected,
            onClick = { onSelect(PlanKind.WEEKLY) },
            badge = if (option.offersTrial) stringResource(R.string.plan_free_trial_badge) else null,
        )
        PlanKind.MONTHLY -> RnPlanCard(
            name = stringResource(R.string.plan_monthly),
            note = stringResource(R.string.plan_monthly_note),
            price = p.formattedPrice,
            per = stringResource(R.string.plan_per_month),
            selected = selected,
            onClick = { onSelect(PlanKind.MONTHLY) },
        )
        PlanKind.LIFETIME -> RnPlanCard(
            name = stringResource(R.string.plan_lifetime),
            note = stringResource(R.string.plan_lifetime_note),
            price = p.formattedPrice,
            per = stringResource(R.string.plan_one_time),
            selected = selected,
            onClick = { onSelect(PlanKind.LIFETIME) },
            badge = stringResource(R.string.plan_best_value),
            badgeContainer = StoreColors.Orange,
            badgeContent = StoreColors.OnOrange,
        )
    }
}

@Composable
private fun ctaLabel(option: PlanOption?): String = stringResource(
    when (option?.kind) {
        PlanKind.WEEKLY -> if (option.offersTrial) R.string.paywall_cta_trial else R.string.paywall_cta_weekly
        PlanKind.MONTHLY -> R.string.paywall_cta_monthly
        PlanKind.LIFETIME -> R.string.paywall_cta_lifetime
        null -> R.string.paywall_cta_trial
    },
)
