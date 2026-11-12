package app.letostudio.gameemulator.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.domain.model.ThemeSetting
import app.letostudio.gameemulator.monetization.ProPlan
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnNativeAd
import app.letostudio.gameemulator.ui.components.RnOverline
import app.letostudio.gameemulator.ui.components.RnSegmented
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.paywall.openUrl
import app.letostudio.gameemulator.ui.theme.RnTheme
import com.google.android.gms.ads.nativead.NativeAd

@Composable
fun RnSettingsRoute(
    onUpgrade: () -> Unit,
    onGamesFolder: () -> Unit,
    onEmulator: () -> Unit,
    onControls: () -> Unit,
    onOss: () -> Unit,
    onDebug: () -> Unit,
    debugAllowed: Boolean,
    snackbar: SnackbarHostState,
    viewModel: RnSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ad by viewModel.ad.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SettingsEvent.OpenUrl -> context.openUrl(e.url)
                is SettingsEvent.Email -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(e.to))
                        putExtra(Intent.EXTRA_SUBJECT, res.getString(e.subjectRes))
                        putExtra(Intent.EXTRA_TEXT, e.body)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(res.getString(R.string.settings_no_email_app, e.to))
                    }
                }
                SettingsEvent.Rate -> {
                    val pkg = context.packageName
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                    } catch (_: ActivityNotFoundException) {
                        context.openUrl("https://play.google.com/store/apps/details?id=$pkg")
                    }
                }
                SettingsEvent.DebugUnlocked -> Toast.makeText(context, R.string.settings_debug_unlocked, Toast.LENGTH_SHORT).show()
                SettingsEvent.OpenDebug -> onDebug()
                SettingsEvent.SwitchFailed -> snackbar.showSnackbar(res.getString(R.string.paywall_purchase_error))
            }
        }
    }
    RnSettingsScreen(
        state = state,
        ad = ad,
        onUpgrade = onUpgrade,
        onSwitch = { (context as? Activity)?.let(viewModel::switchPlan) },
        onManage = viewModel::manageSubscription,
        onGamesFolder = onGamesFolder,
        onEmulator = onEmulator,
        onControls = onControls,
        onTheme = viewModel::setTheme,
        onRate = viewModel::rate,
        onContact = viewModel::contact,
        onPrivacy = { viewModel.openPrivacy() },
        onTerms = { viewModel.openTerms() },
        onGdpr = { (context as? Activity)?.let(viewModel::privacyOptions) },
        onOss = { viewModel.onOssOpened(); onOss() },
        onVersion = { viewModel.onVersionTap(debugAllowed) },
    )
}

@Composable
fun RnSettingsScreen(
    state: SettingsUiState,
    ad: NativeAd?,
    onUpgrade: () -> Unit,
    onSwitch: () -> Unit,
    onManage: () -> Unit,
    onGamesFolder: () -> Unit,
    onEmulator: () -> Unit,
    onControls: () -> Unit,
    onTheme: (ThemeSetting) -> Unit,
    onRate: () -> Unit,
    onContact: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onGdpr: () -> Unit,
    onOss: () -> Unit,
    onVersion: () -> Unit,
) {
    val pro = state.pro
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.settings_title))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Plan card
            RnCardGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RnOverline(stringResource(R.string.settings_your_plan))
                        val planName = when (pro.plan) {
                            ProPlan.NONE -> stringResource(R.string.settings_plan_free)
                            ProPlan.MONTHLY -> stringResource(R.string.settings_plan_premium_named, stringResource(R.string.plan_monthly))
                            ProPlan.YEARLY -> stringResource(R.string.settings_plan_premium_named, stringResource(R.string.plan_yearly))
                            ProPlan.LIFETIME -> stringResource(R.string.settings_plan_premium_named, stringResource(R.string.plan_lifetime))
                            ProPlan.SUBSCRIPTION -> stringResource(R.string.settings_plan_premium)
                        }
                        Text(planName, style = RnTheme.type.title, color = RnTheme.colors.ink)
                    }
                    when (pro.plan) {
                        ProPlan.NONE -> RnButton(stringResource(R.string.settings_upgrade), onUpgrade, small = true)
                        ProPlan.MONTHLY -> RnButton(stringResource(R.string.settings_switch_yearly), onSwitch, small = true, loading = state.switching)
                        ProPlan.YEARLY -> RnButton(stringResource(R.string.settings_switch_monthly), onSwitch, small = true, loading = state.switching)
                        ProPlan.LIFETIME, ProPlan.SUBSCRIPTION -> Unit
                    }
                }
                if (pro.isPro && pro.plan != ProPlan.LIFETIME) {
                    RnDivider()
                    RnListRow(
                        stringResource(R.string.settings_manage_sub),
                        subtitle = stringResource(R.string.settings_manage_sub_sub),
                        icon = R.drawable.ms_credit_card,
                        showChevron = true,
                        onClick = onManage,
                    )
                }
            }

            RnOverline(stringResource(R.string.settings_group_games), Modifier.padding(top = 8.dp, start = 4.dp))
            RnCardGroup {
                RnListRow(
                    stringResource(R.string.settings_games_folder),
                    subtitle = state.folderName ?: stringResource(R.string.settings_games_folder_none),
                    icon = R.drawable.ms_folder,
                    showChevron = true,
                    onClick = onGamesFolder,
                )
                RnDivider()
                RnListRow(
                    stringResource(R.string.settings_emulator),
                    subtitle = emuSummary(state),
                    icon = R.drawable.ms_tune,
                    showChevron = true,
                    onClick = onEmulator,
                )
                RnDivider()
                RnListRow(
                    stringResource(R.string.settings_controls),
                    subtitle = state.padName?.let { stringResource(R.string.settings_controls_pad, it) } ?: stringResource(R.string.settings_controls_none),
                    icon = R.drawable.ms_sports_esports,
                    showChevron = true,
                    onClick = onControls,
                )
            }

            RnOverline(stringResource(R.string.settings_group_appearance), Modifier.padding(top = 8.dp, start = 4.dp))
            RnSegmented(
                options = listOf(
                    ThemeSetting.LIGHT to stringResource(R.string.theme_light),
                    ThemeSetting.DARK to stringResource(R.string.theme_dark),
                    ThemeSetting.SYSTEM to stringResource(R.string.theme_system),
                ),
                selected = state.theme,
                onSelect = onTheme,
                modifier = Modifier.fillMaxWidth(),
            )

            RnOverline(stringResource(R.string.settings_group_support), Modifier.padding(top = 8.dp, start = 4.dp))
            RnCardGroup {
                RnListRow(stringResource(R.string.settings_rate), icon = R.drawable.ms_star_rate, showChevron = true, onClick = onRate)
                RnDivider()
                RnListRow(stringResource(R.string.settings_contact), icon = R.drawable.ms_mail, showChevron = true, onClick = onContact)
                RnDivider()
                RnListRow(stringResource(R.string.settings_privacy), icon = R.drawable.ms_policy, iconTint = null, showChevron = true, onClick = onPrivacy)
                RnDivider()
                RnListRow(stringResource(R.string.settings_terms), icon = R.drawable.ms_description, showChevron = true, onClick = onTerms)
                if (state.privacyOptionsRequired) {
                    RnDivider()
                    RnListRow(stringResource(R.string.settings_gdpr), icon = R.drawable.ms_shield, showChevron = true, onClick = onGdpr)
                }
                RnDivider()
                RnListRow(
                    stringResource(R.string.settings_oss),
                    subtitle = stringResource(R.string.settings_oss_sub),
                    icon = R.drawable.ms_code,
                    showChevron = true,
                    onClick = onOss,
                )
                RnDivider()
                RnListRow(stringResource(R.string.settings_version), icon = R.drawable.ms_info, trailingText = state.version, onClick = onVersion)
            }
            // N4 Native Small at the bottom — Free only.
            if (!pro.isPro) RnNativeAd(ad, medium = false, modifier = Modifier.padding(top = 8.dp))
            if (BuildConfig.DEBUG && BuildConfig.ADMOB_IDS_ARE_TEST) Unit
        }
    }
}

@Composable
private fun emuSummary(state: SettingsUiState): String {
    val e = state.emu
    val smart = if (e.smartSetupOn) stringResource(R.string.emu_smart_on) else stringResource(R.string.emu_smart_off)
    return "$smart · ${e.resolution}× · " + stringResource(R.string.emu_backend_gles)
}
