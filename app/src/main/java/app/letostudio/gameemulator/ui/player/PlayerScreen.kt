package app.letostudio.gameemulator.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.input.GamepadManager
import app.letostudio.gameemulator.input.KeyMapper
import app.letostudio.gameemulator.input.PlayerInputBridge
import app.letostudio.gameemulator.ui.components.RnBadge
import app.letostudio.gameemulator.ui.components.RnDialog
import app.letostudio.gameemulator.ui.theme.RnThemeProvider
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** S09 Player — always dark, landscape, immersive, keep-screen-on. No ads of any kind. */
@Composable
fun PlayerRoute(onExit: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var retroView by remember { mutableStateOf<GLRetroView?>(null) }

    // Landscape + immersive + keep screen on while the player is up.
    DisposableEffect(activity) {
        val a = activity ?: return@DisposableEffect onDispose { }
        val previous = a.requestedOrientation
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(a.window, a.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            a.requestedOrientation = previous
        }
    }

    fun capture(view: GLRetroView, done: (Bitmap?) -> Unit) {
        if (view.width <= 0 || view.height <= 0) return done(null)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, bmp, { result -> done(if (result == PixelCopy.SUCCESS) bmp.scaleThumb() else null) }, Handler(Looper.getMainLooper()))
        }.onFailure { done(null) }
    }

    fun captureFull(view: GLRetroView, done: (Bitmap?) -> Unit) {
        if (view.width <= 0 || view.height <= 0) return done(null)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, bmp, { result -> done(if (result == PixelCopy.SUCCESS) bmp else null) }, Handler(Looper.getMainLooper()))
        }.onFailure { done(null) }
    }

    /** Auto-save then leave (with the I1 interstitial check once the save is on disk). */
    fun exitGame(reason: String) {
        val v = retroView
        if (v == null) {
            viewModel.onExitSaved(reason)
            return
        }
        capture(v) { thumb ->
            val bytes = runCatching { v.serializeState() }.getOrNull()
            viewModel.autoSave(bytes, thumb) { viewModel.onExitSaved(reason) }
        }
    }

    // Background → auto-save + pause (F07). Added after the view's own observer, so ON_PAUSE
    // reaches us first (downward events dispatch in reverse order) while GL is still alive.
    DisposableEffect(lifecycleOwner, retroView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> retroView?.let { v ->
                    val bytes = runCatching { v.serializeState() }.getOrNull()
                    viewModel.autoSave(bytes, null)
                    viewModel.pause()
                }
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                Lifecycle.Event.ON_START -> viewModel.onStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hardware gamepad → emulator (remapped), overlay hides on first pad input.
    DisposableEffect(retroView, state.mapping) {
        val v = retroView
        PlayerInputBridge.handler = if (v == null) null else object : PlayerInputBridge.Handler {
            override fun onKey(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
                if (!GamepadManager.isGamepadEvent(event)) return false
                event.device?.let { viewModel.onGamepadInput(it.vendorId, it.productId) }
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                    if (event.action == KeyEvent.ACTION_UP) viewModel.pause()
                    return true
                }
                v.sendKeyEvent(event.action, KeyMapper.translate(state.mapping, event.keyCode), 0)
                return true
            }

            override fun onMotion(event: MotionEvent): Boolean {
                if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
                event.device?.let { viewModel.onGamepadInput(it.vendorId, it.productId) }
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ), 0)
                v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0)
                return true
            }
        }
        onDispose { PlayerInputBridge.handler = null }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is PlayerEvent.Toast -> snackbar.showSnackbar(res.getString(e.res))
                PlayerEvent.Exit -> {
                    retroView = null
                    activity?.let { viewModel.exitAds(it) }
                    onExit()
                }
            }
        }
    }

    // Pause overlay / speed / audio follow state.
    LaunchedEffect(retroView, state.phase, state.speed, state.settings.soundOn) {
        val v = retroView ?: return@LaunchedEffect
        val running = state.phase == PlayerPhase.RUNNING || state.phase == PlayerPhase.LOADING
        v.audioEnabled = running && state.settings.soundOn
        v.frameSpeed = if (running) state.speed else 1
        if (state.phase == PlayerPhase.PAUSED) v.onPause() else if (state.phase == PlayerPhase.RUNNING) v.onResume()
    }

    BackHandler {
        when (state.phase) {
            PlayerPhase.RUNNING -> viewModel.pause()
            PlayerPhase.PAUSED -> viewModel.resume()
            PlayerPhase.ERROR -> onExit()
            else -> Unit
        }
    }

    RnThemeProvider(dark = true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(); viewModel.onScreenTouched() } } },
        ) {
            val launch = state.launch
            val game = state.game
            if (launch != null && game != null && state.phase != PlayerPhase.ERROR) {
                androidx.compose.runtime.key(launch.attempt) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GLRetroView(ctx, viewModel.coreData(game, launch)).also { v ->
                                lifecycleOwner.lifecycle.addObserver(v)
                                retroView = v
                                scope.launch {
                                    v.getGLRetroErrors().collect { code -> viewModel.onCoreError(code) }
                                }
                                scope.launch {
                                    var last = System.currentTimeMillis()
                                    var count = 0
                                    v.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().collect {
                                        viewModel.onFirstFrame(v)
                                        viewModel.onFrame()
                                        count++
                                        val now = System.currentTimeMillis()
                                        if (now - last >= 1000) {
                                            viewModel.onFps((count * 1000 / (now - last)).toInt())
                                            count = 0
                                            last = now
                                        }
                                    }
                                }
                            }
                        },
                        onRelease = { v ->
                            lifecycleOwner.lifecycle.removeObserver(v)
                            if (retroView === v) retroView = null
                        },
                    )
                }
            }

            if (state.phase == PlayerPhase.LOADING) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            if (state.phase == PlayerPhase.RUNNING && state.overlayVisible) {
                VirtualPad(
                    opacity = state.pad.opacity,
                    haptics = state.pad.haptics,
                    onKey = { code, down -> retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0) },
                    onStick = { x, y -> retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0) },
                    onPause = viewModel::pause,
                    pauseDescription = stringResource(R.string.cd_pause),
                )
            }
            if (state.settings.showFps && state.phase == PlayerPhase.RUNNING) {
                RnBadge("${state.fps} FPS", Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 120.dp, top = 18.dp))
            }

            if (state.phase == PlayerPhase.PAUSED && game != null) {
                PauseMenu(
                    state = state,
                    onResume = viewModel::resume,
                    onSave = { retroView?.let { v -> capture(v) { thumb -> viewModel.saveManual(runCatching { v.serializeState() }.getOrNull(), thumb) } } },
                    onLoad = { slot ->
                        scope.launch {
                            val bytes = viewModel.readSlot(slot)
                            val ok = bytes != null && retroView?.let { v -> runCatching { v.unserializeState(bytes) }.getOrDefault(false) } == true
                            snackbar.showSnackbar(res.getString(if (ok) R.string.toast_loaded else R.string.toast_load_failed))
                            if (ok) viewModel.resume()
                        }
                    },
                    onSpeed = viewModel::setSpeed,
                    onScreenshot = { retroView?.let { v -> captureFull(v, viewModel::saveScreenshot) } },
                    onExit = { exitGame("menu") },
                    onPadOpacity = viewModel::setPadOpacity,
                    onPadHaptics = viewModel::setPadHaptics,
                    onFixLag = { viewModel.fixLag { vars -> retroView?.updateVariables(*vars) } },
                    onResolution = { r -> viewModel.setResolution(r) { vars -> retroView?.updateVariables(*vars) } },
                    onShowFps = viewModel::setShowFps,
                )
            }

            if (state.phase == PlayerPhase.ERROR) {
                RnDialog(
                    title = stringResource(R.string.player_error_title),
                    text = stringResource(R.string.player_error_body),
                    confirm = stringResource(R.string.player_back_to_library),
                    onConfirm = onExit,
                    onDismiss = onExit,
                )
            }
            if (state.phase == PlayerPhase.EXITING) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
        }
    }
}

private fun Bitmap.scaleThumb(): Bitmap {
    val w = 320
    val h = (height * w / width.toFloat()).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, w, h, true)
}

@Suppress("unused")
private suspend fun awaitMain(block: (() -> Unit) -> Unit) = suspendCancellableCoroutine { c -> block { c.resume(Unit) } }

@Suppress("unused")
@Composable
private fun LoadingLabel() = Text(stringResource(R.string.player_loading), color = Color.White)
