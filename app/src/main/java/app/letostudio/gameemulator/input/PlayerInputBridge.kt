package app.letostudio.gameemulator.input

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Lets the Player receive hardware gamepad events from the single Activity (Compose has no
 * generic-motion hook). MainActivity forwards key + generic motion events while a handler is
 * registered; the handler returns true when it consumed the event.
 */
object PlayerInputBridge {
    interface Handler {
        fun onKey(event: KeyEvent): Boolean
        fun onMotion(event: MotionEvent): Boolean
    }

    @Volatile var handler: Handler? = null
}
