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
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Glass = Color(0x8F080C14)
private val Edge = Color(0x47FFFFFF)

/**
 * On-screen controls (F08), positions from styles/app.css `.rn-pad-*` (844×390 landscape
 * artboard). Each control is its own pointer target, so several can be held at once.
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
) {
    val view = LocalView.current
    val press: (Int, Boolean) -> Unit = { code, down ->
        if (down && haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onKey(code, down)
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().alpha(opacity)) {
        // Shoulders
        PadButton("L", KeyEvent.KEYCODE_BUTTON_L1, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 14.dp).size(84.dp, 32.dp))
        PadButton("R", KeyEvent.KEYCODE_BUTTON_R1, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 14.dp).size(84.dp, 32.dp))
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

        // D-pad 132×132 at (36, 70)
        Box(Modifier.align(Alignment.CenterStart).padding(start = 36.dp).offset(y = (-20).dp).size(132.dp)) {
            PadButton("▲", KeyEvent.KEYCODE_DPAD_UP, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.TopCenter).size(44.dp), icon = R.drawable.ms_arrow_drop_up)
            PadButton("▼", KeyEvent.KEYCODE_DPAD_DOWN, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.BottomCenter).size(44.dp), icon = R.drawable.ms_arrow_drop_down)
            PadButton("◀", KeyEvent.KEYCODE_DPAD_LEFT, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.CenterStart).size(44.dp), icon = R.drawable.ms_arrow_left)
            PadButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT, press, RoundedCornerShape(10.dp), Modifier.align(Alignment.CenterEnd).size(44.dp), icon = R.drawable.ms_arrow_right)
        }
        // Analog stick 76 at (150, bottom 18)
        Stick(onStick, Modifier.align(Alignment.BottomStart).padding(start = 150.dp, bottom = 18.dp))

        // Face buttons 144×144 at (right 36, 64)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 36.dp).offset(y = (-20).dp).size(144.dp)) {
            PadButton("Y", KeyEvent.KEYCODE_BUTTON_Y, press, CircleShape, Modifier.align(Alignment.TopCenter).size(50.dp))
            PadButton("A", KeyEvent.KEYCODE_BUTTON_A, press, CircleShape, Modifier.align(Alignment.BottomCenter).size(50.dp), fill = Color(0xD90070D1), edge = Color(0x80FFFFFF))
            PadButton("X", KeyEvent.KEYCODE_BUTTON_X, press, CircleShape, Modifier.align(Alignment.CenterStart).size(50.dp))
            PadButton("B", KeyEvent.KEYCODE_BUTTON_B, press, CircleShape, Modifier.align(Alignment.CenterEnd).size(50.dp))
        }
        // SELECT / START bottom centre
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, press, RoundedCornerShape(999.dp), Modifier.size(72.dp, 28.dp), small = true)
            PadButton("START", KeyEvent.KEYCODE_BUTTON_START, press, RoundedCornerShape(999.dp), Modifier.size(72.dp, 28.dp), small = true)
        }
    }
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
    icon: Int? = null,
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
        if (icon != null) {
            RnIcon(icon, tint = Color.White, size = 32.dp)
        } else {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (small) 12.sp else 16.sp, letterSpacing = if (small) 0.6.sp else 0.sp)
        }
    }
}

@Composable
private fun BoxScope.Stick(onStick: (Float, Float) -> Unit, modifier: Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val sizeDp = 76.dp
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
                .size(36.dp)
                .background(Color(0x8CFFFFFF), CircleShape),
        )
    }
}
