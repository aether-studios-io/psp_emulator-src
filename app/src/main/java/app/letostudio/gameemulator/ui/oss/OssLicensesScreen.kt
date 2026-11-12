package app.letostudio.gameemulator.ui.oss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.paywall.openUrl
import app.letostudio.gameemulator.ui.theme.RnTheme

/** Public GPL source mirror (spec §5d) — created only after the owner's OK. */
const val SOURCE_CODE_URL = "https://github.com/aether-studios-io/psp_emulator-src"

private data class Lib(val name: String, val license: String, val url: String)

private val Libraries = listOf(
    Lib("PPSSPP (emulation core)", "GPL-2.0-or-later", "https://github.com/hrydgard/ppsspp"),
    Lib("LibretroDroid", "GPL-3.0", "https://github.com/Swordfish90/LibretroDroid"),
    Lib("libretro-common", "MIT", "https://github.com/libretro/libretro-common"),
    Lib("FFmpeg (in the core)", "LGPL-2.1-or-later", "https://ffmpeg.org"),
    Lib("Oboe", "Apache-2.0", "https://github.com/google/oboe"),
    Lib("AndroidX / Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack"),
    Lib("Kotlin & kotlinx", "Apache-2.0", "https://kotlinlang.org"),
    Lib("Dagger Hilt", "Apache-2.0", "https://dagger.dev/hilt"),
    Lib("Coil", "Apache-2.0", "https://coil-kt.github.io/coil"),
    Lib("OkHttp / Retrofit", "Apache-2.0", "https://square.github.io/okhttp"),
    Lib("Be Vietnam Pro font", "SIL OFL 1.1", "https://github.com/bettergui/BeVietnamPro"),
    Lib("Material Symbols", "Apache-2.0", "https://github.com/google/material-design-icons"),
)

@Composable
fun OssLicensesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var showFull by rememberSaveable { mutableStateOf(false) }
    val fullText = remember(showFull) {
        if (!showFull) "" else runCatching {
            context.assets.open("licenses/LICENSE.txt").bufferedReader().readText() + "\n\n" +
                context.assets.open("licenses/THIRD_PARTY_NOTICES.md").bufferedReader().readText()
        }.getOrDefault("")
    }
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.oss_title), large = false, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.oss_intro), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
            RnCardGroup {
                RnListRow(
                    stringResource(R.string.oss_source),
                    subtitle = stringResource(R.string.oss_source_sub),
                    icon = R.drawable.ms_code,
                    showChevron = true,
                    onClick = { context.openUrl(SOURCE_CODE_URL) },
                )
            }
            Text(stringResource(R.string.oss_credit_core), style = RnTheme.type.label, color = RnTheme.colors.ink)
            RnCardGroup {
                Libraries.forEachIndexed { i, lib ->
                    if (i > 0) RnDivider()
                    RnListRow(lib.name, subtitle = lib.license, onClick = { context.openUrl(lib.url) })
                }
            }
            RnCardGroup {
                RnListRow(stringResource(R.string.oss_full_text), icon = R.drawable.ms_description, showChevron = !showFull, onClick = { showFull = !showFull })
            }
            if (showFull) {
                Text(fullText, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp), color = RnTheme.colors.inkMuted)
            }
        }
    }
}
