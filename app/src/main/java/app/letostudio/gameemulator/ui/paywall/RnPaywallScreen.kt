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
) {
    val c = RnTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(196.dp)) {
            Image(
                painterResource(R.drawable.paywall_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RnIconButton(R.drawable.ms_close, stringResource(R.string.cd_close), onClose, style = RnIconButtonStyle.Glass)
                Box(Modifier.weight(1f))
                RnBadge(
                    stringResource(R.string.paywall_pro_badge),
                    Modifier.padding(end = 8.dp),
                    container = Color(0xFF6AAEFF),
                    content = Color(0xFF04122B),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.paywall_title), style = RnTheme.type.display, color = c.ink)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MvpBenefits.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RnIconTile(icon, size = 32.dp, iconSize = 20.dp)
                        Text(stringResource(text), style = RnTheme.type.body.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.ink)
                    }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary)
                }
                state.error || state.plans.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RnNote(stringResource(R.string.paywall_plans_error), icon = R.drawable.ms_error)
                    RnButton(stringResource(R.string.retry), onRetry, small = true, style = RnButtonStyle.Secondary)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.plans.forEach { PlanRow(it, state.selected == it.kind, onSelect) }
                }
            }
            val option = state.selectedOption
            RnButton(
                text = ctaLabel(option),
                onClick = onCta,
                modifier = Modifier.fillMaxWidth(),
                enabled = option != null,
                loading = state.purchasing,
            )
            if (state.showLifetimeNote) RnNote(stringResource(R.string.paywall_lifetime_cancel_note))
            Text(
                stringResource(R.string.paywall_subscription_terms),
                style = RnTheme.type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Normal),
                color = c.inkMuted,
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

@Composable
private fun Dot() = Text("·", style = RnTheme.type.caption, color = RnTheme.colors.inkMuted)

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    RnTextLink(text, onClick, color = RnTheme.colors.inkMuted, underline = true)
}

@Composable
private fun PlanRow(option: PlanOption, selected: Boolean, onSelect: (PlanKind) -> Unit) {
    val p = option.plan
    when (option.kind) {
        PlanKind.YEARLY -> RnPlanCard(
            name = stringResource(R.string.plan_yearly),
            note = if (option.offersTrial) stringResource(R.string.plan_yearly_note_trial, p.formattedPrice) else stringResource(R.string.plan_yearly_note),
            price = p.formattedPrice,
            per = stringResource(R.string.plan_per_year),
            selected = selected,
            onClick = { onSelect(PlanKind.YEARLY) },
            badge = stringResource(R.string.plan_best_value),
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
        )
    }
}

@Composable
private fun ctaLabel(option: PlanOption?): String = stringResource(
    when (option?.kind) {
        PlanKind.YEARLY -> if (option.offersTrial) R.string.paywall_cta_trial else R.string.paywall_cta_yearly
        PlanKind.MONTHLY -> R.string.paywall_cta_monthly
        PlanKind.LIFETIME -> R.string.paywall_cta_lifetime
        null -> R.string.paywall_cta_trial
    },
)

@Suppress("unused")
private val PlanShape = RoundedCornerShape(16.dp)

@Suppress("unused")
private fun Modifier.iconSize() = size(24.dp)
