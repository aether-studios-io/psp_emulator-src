package app.letostudio.gameemulator.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PadInfo(val id: Int, val name: String, val vendorId: Int, val productId: Int)

/**
 * Bluetooth/USB gamepad detection (F09) via InputManager. [connected] updates on attach /
 * detach; the Player additionally hides the on-screen overlay on the first pad input.
 */
@Singleton
class GamepadManager @Inject constructor(
    @ApplicationContext context: Context,
) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val _connected = MutableStateFlow(scan())
    val connected: StateFlow<List<PadInfo>> = _connected.asStateFlow()

    init {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
    }

    override fun onInputDeviceAdded(deviceId: Int) { _connected.value = scan() }
    override fun onInputDeviceRemoved(deviceId: Int) { _connected.value = scan() }
    override fun onInputDeviceChanged(deviceId: Int) { _connected.value = scan() }

    private fun scan(): List<PadInfo> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        val d = InputDevice.getDevice(id) ?: return@mapNotNull null
        if (!isGamepad(d)) return@mapNotNull null
        PadInfo(id, d.name.take(40), d.vendorId, d.productId)
    }

    companion object {
        fun isGamepad(d: InputDevice): Boolean {
            if (d.isVirtual) return false
            val s = d.sources
            return (s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        }

        fun isGamepadEvent(event: KeyEvent): Boolean {
            val src = event.source
            return (src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
                KeyEvent.isGamepadButton(event.keyCode)
        }
    }
}
