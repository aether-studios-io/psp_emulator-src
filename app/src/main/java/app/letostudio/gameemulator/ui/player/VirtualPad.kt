package app.letostudio.gameemulator.ui.player

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.components.RnIcon
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Glass = Color(0x8F080C14)
private val Edge = Color(0x47FFFFFF)

/**
 * On-screen controls (F08), laid out on the 844×390 landscape artboard and multiplied by
 * [scale] (the "controls size" setting). The D-pad and the face buttons are each one touch
 * area: a touch anywhere in the cluster drives the nearest button (D-pad: 8-way), so the gaps
 * between buttons are not dead zones and a thumb can slide from one button to the next.
 */
@Composable
fun VirtualPad(
    opacity: Float,
    haptics: Boolean,
    onKey: (keyCode: Int, pressed: Boolean) -> Unit,
    onStick: (x: Float, y: Float) -> Unit,
    onPause: () -> Unit,
    pauseDescription: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val view = LocalView.current
    val press: (Int, Boolean) -> Unit = { code, down ->
        if (down && haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onKey(code, down)
    }
    val k = scale
    Box(modifier.fillMaxSize().navigationBarsPadding().alpha(opacity)) {
        // Shoulders
        PadButton("L", KeyEvent.KEYCODE_BUTTON_L1, press, RoundedCornerShape(12.dp), Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 12.dp).size(92.dp * k, 44.dp * k))
        PadButton("R", KeyEvent.KEYCODE_BUTTON_R1, press, RoundedCornerShape(12.dp), Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 12.dp).size(92.dp * k, 44.dp * k))
        // Pause (glass, top centre)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(44.dp)
                .background(Glass, CircleShape)
                .border(1.5.dp, Edge, CircleShape)
                .semantics { contentDescription = pauseDescription }
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); onPause() } },
            contentAlignment = Alignment.Center,
        ) { RnIcon(R.drawable.ms_pause_fill, tint = Color.White) }

        DPad(press, Modifier.align(Alignment.CenterStart).padding(start = 28.dp).offset(y = (-16).dp).size(DPAD_DP.dp * k))
        Stick(onStick, k, Modifier.align(Alignment.BottomStart).padding(start = 150.dp * k, bottom = 14.dp))
        FaceButtons(press, Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).offset(y = (-16).dp).size(FACE_DP.dp * k))

        // SELECT / START bottom centre
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, press, RoundedCornerShape(999.dp), Modifier.size(80.dp * k, 36.dp * k), small = true)
            PadButton("START", KeyEvent.KEYCODE_BUTTON_START, press, RoundedCornerShape(999.dp), Modifier.size(80.dp * k, 36.dp * k), small = true)
        }
    }
}

private const val DPAD_DP = 150
private const val DPAD_KEY_DP = 52
private const val FACE_DP = 170
private const val FACE_KEY_DP = 60

/** Touch geometry for the clustered controls; pure so it is unit-tested. */
internal object PadGeometry {
    private val faceKeys = intArrayOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X)
    private const val RAD = FACE_KEY_DP / 2f / FACE_DP
    private val faceX = floatArrayOf(0.5f, 1 - RAD, 0.5f, RAD)
    private val faceY = floatArrayOf(RAD, 0.5f, 1 - RAD, 0.5f)
    private const val DEAD = 0.1f
    private const val TAN_22 = 0.4142f

    /** The face key nearest to (fx, fy), given as fractions of the cluster side. */
    fun face(fx: Float, fy: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in faceKeys.indices) {
            val d = (fx - faceX[i]) * (fx - faceX[i]) + (fy - faceY[i]) * (fy - faceY[i])
            if (d < bestD) { bestD = d; best = i }
        }
        return faceKeys[best]
    }

    /**
     * 8-way D-pad for (fx, fy) as fractions of the pad side. A direction is on within 67.5°
     * of its axis, so diagonals press two keys; the centre is a small dead zone.
     */
    fun dpad(fx: Float, fy: Float): Set<Int> {
        val dx = fx - 0.5f
        val dy = fy - 0.5f
        if (dx * dx + dy * dy < DEAD * DEAD) return emptySet()
        val keys = mutableSetOf<Int>()
        if (dx > TAN_22 * abs(dy)) keys += KeyEvent.KEYCODE_DPAD_RIGHT
        if (-dx > TAN_22 * abs(dy)) keys += KeyEvent.KEYCODE_DPAD_LEFT
        if (dy > TAN_22 * abs(dx)) keys += KeyEvent.KEYCODE_DPAD_DOWN
        if (-dy > TAN_22 * abs(dx)) keys += KeyEvent.KEYCODE_DPAD_UP
        return keys
    }
}

/**
 * One touch area for several keys: every pressed pointer maps to keys via [keysAt] (fractions
 * of the area's size), and the union is diffed so only changes are sent. Held keys are
 * released if the area leaves composition mid-press.
 */
private fun Modifier.clusterInput(
    keysAt: (Float, Float) -> Set<Int>,
    onKey: () -> (Int, Boolean) -> Unit,
    onHeld: (Set<Int>) -> Unit,
): Modifier = pointerInput(Unit) {
    var held = emptySet<Int>()
    try {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                val now = mutableSetOf<Int>()
                e.changes.forEach { c ->
                    if (c.pressed) now += keysAt(c.position.x / size.width, c.position.y / size.height)
                    c.consume()
                }
                if (now != held) {
                    val send = onKey()
                    (held - now).forEach { send(it, false) }
                    (now - held).forEach { send(it, true) }
                    held = now
                    onHeld(now)
                }
            }
        }
    } finally {
        val send = onKey()
        held.forEach { send(it, false) }
    }
}

@Composable
private fun DPad(onKey: (Int, Boolean) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(onKey)
    var held by remember { mutableStateOf(emptySet<Int>()) }
    Box(
        modifier
            .semantics { contentDescription = "D-pad" }
            .clusterInput(PadGeometry::dpad, { latest }, { held = it }),
    ) {
        val frac = DPAD_KEY_DP / DPAD_DP.toFloat()
        val shape = RoundedCornerShape(12.dp)
        KeyFace(KeyEvent.KEYCODE_DPAD_UP in held, shape, Modifier.align(Alignment.TopCenter).fillMaxSize(frac)) { ArrowIcon(R.drawable.ms_arrow_drop_up) }
        KeyFace(KeyEvent.KEYCODE_DPAD_DOWN in held, shape, Modifier.align(Alignment.BottomCenter).fillMaxSize(frac)) { ArrowIcon(R.drawable.ms_arrow_drop_down) }
        KeyFace(KeyEvent.KEYCODE_DPAD_LEFT in held, shape, Modifier.align(Alignment.CenterStart).fillMaxSize(frac)) { ArrowIcon(R.drawable.ms_arrow_left) }
        KeyFace(KeyEvent.KEYCODE_DPAD_RIGHT in held, shape, Modifier.align(Alignment.CenterEnd).fillMaxSize(frac)) { ArrowIcon(R.drawable.ms_arrow_right) }
    }
}

@Composable
private fun ArrowIcon(icon: Int) = RnIcon(icon, tint = Color.White, size = 34.dp)

@Composable
private fun FaceButtons(onKey: (Int, Boolean) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(onKey)
    var held by remember { mutableStateOf(emptySet<Int>()) }
    Box(
        modifier
            .semantics { contentDescription = "Face buttons" }
            .clusterInput({ x, y -> setOf(PadGeometry.face(x, y)) }, { latest }, { held = it }),
    ) {
        val frac = FACE_KEY_DP / FACE_DP.toFloat()
        FaceKey("Y", KeyEvent.KEYCODE_BUTTON_Y in held, Modifier.align(Alignment.TopCenter).fillMaxSize(frac))
        FaceKey("A", KeyEvent.KEYCODE_BUTTON_A in held, Modifier.align(Alignment.BottomCenter).fillMaxSize(frac), fill = Color(0xD90070D1), edge = Color(0x80FFFFFF))
        FaceKey("X", KeyEvent.KEYCODE_BUTTON_X in held, Modifier.align(Alignment.CenterStart).fillMaxSize(frac))
        FaceKey("B", KeyEvent.KEYCODE_BUTTON_B in held, Modifier.align(Alignment.CenterEnd).fillMaxSize(frac))
    }
}

@Composable
private fun FaceKey(label: String, down: Boolean, modifier: Modifier, fill: Color = Glass, edge: Color = Edge) {
    KeyFace(down, CircleShape, modifier, fill, edge) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Visual only: input comes from the enclosing cluster. */
@Composable
private fun KeyFace(down: Boolean, shape: Shape, modifier: Modifier, fill: Color = Glass, edge: Color = Edge, content: @Composable () -> Unit) {
    Box(
        modifier.background(if (down) fill.copy(alpha = 1f) else fill, shape).border(1.5.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PadButton(
    label: String,
    keyCode: Int,
    onKey: (Int, Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier,
    fill: Color = Glass,
    edge: Color = Edge,
    small: Boolean = false,
) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (down) fill.copy(alpha = 1f) else fill, shape)
            .border(1.5.dp, edge, shape)
            .semantics { contentDescription = label }
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).also { it.consume() }
                    down = true
                    onKey(keyCode, true)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach(PointerInputChange::consume)
                    } while (event.changes.any { it.pressed })
                    down = false
                    onKey(keyCode, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (small) 12.sp else 16.sp, letterSpacing = if (small) 0.6.sp else 0.sp)
    }
}

@Composable
private fun BoxScope.Stick(onStick: (Float, Float) -> Unit, scale: Float, modifier: Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val sizeDp = 84.dp * scale
    Box(
        modifier
            .size(sizeDp)
            .background(Glass, CircleShape)
            .border(1.5.dp, Edge, CircleShape)
            .semantics { contentDescription = "Analog stick" }
            .pointerInput(Unit) {
                val radius = size.width / 2f
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(radius, radius)
                    fun update(p: Offset) {
                        var d = p - center
                        val len = sqrt(d.x * d.x + d.y * d.y)
                        if (len > radius) d = d * (radius / len)
                        knob = d
                        onStick(d.x / radius, d.y / radius)
                    }
                    update(first.position)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        e.changes.firstOrNull { it.pressed }?.let { update(it.position) }
                    } while (e.changes.any { it.pressed })
                    knob = Offset.Zero
                    onStick(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(40.dp * scale)
                .background(Color(0x8CFFFFFF), CircleShape),
        )
    }
}
