package app.letostudio.gameemulator.debug

import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnNote
import app.letostudio.gameemulator.ui.components.RnTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val crashReporter: CrashReporter,
    private val crash: CrashContext,
) : ViewModel() {
    fun testCrash(): Nothing {
        crash.breadcrumb("debug", "test crash")
        throw RuntimeException("RetroNova test crash (debug screen)")
    }

    /** SIGSEGV to our own process: caught by the Crashlytics NDK signal handler. */
    fun testNativeCrash() {
        crash.breadcrumb("debug", "test native crash")
        Process.sendSignal(Process.myPid(), SIGSEGV)
    }

    fun testNonFatal() {
        crashReporter.recordNonFatal("debug", IllegalStateException("RetroNova test non-fatal"), mapOf("source" to "debug_screen"))
    }

    private companion object { const val SIGSEGV = 11 }
}

/** Hidden debug screen (analytics.md §6): Settings › Version tapped 7×. */
@Composable
fun DebugRoute(onBack: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.debug_title), large = false, onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RnNote(stringResource(R.string.debug_info))
            RnButton(stringResource(R.string.debug_test_crash), { viewModel.testCrash() }, Modifier.fillMaxWidth(), style = RnButtonStyle.Danger)
            RnButton(stringResource(R.string.debug_test_native), viewModel::testNativeCrash, Modifier.fillMaxWidth(), style = RnButtonStyle.Danger)
            RnButton(
                stringResource(R.string.debug_test_nonfatal),
                {
                    viewModel.testNonFatal()
                    Toast.makeText(context, R.string.debug_nonfatal_sent, Toast.LENGTH_SHORT).show()
                },
                Modifier.fillMaxWidth(),
                style = RnButtonStyle.Secondary,
            )
        }
    }
}
