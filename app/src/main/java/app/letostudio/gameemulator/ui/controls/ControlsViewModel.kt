package app.letostudio.gameemulator.ui.controls

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.input.GamepadManager
import app.letostudio.gameemulator.input.KeyMapper
import app.letostudio.gameemulator.input.PadButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ControlsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val keyMapper: KeyMapper,
    private val analytics: Analytics,
    gamepads: GamepadManager,
) : ViewModel() {
    val pad = settingsStore.padPrefs
    val mapping = keyMapper.mapping
    val pads = gamepads.connected

    init { analytics.screen(AppScreens.CONTROLS_EDITOR) }

    fun setOpacity(v: Float) {
        viewModelScope.launch { settingsStore.setPadOpacity(v) }
    }

    fun logOpacity(v: Float) =
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_opacity", AppParams.VALUE to (v * 100).toInt().toString()))

    fun setSize(v: Float) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_size", AppParams.VALUE to (v * 100).toInt().toString()))
        viewModelScope.launch { settingsStore.setPadSize(v) }
    }

    fun setHaptics(on: Boolean) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_haptics", AppParams.VALUE to on.toString()))
        viewModelScope.launch { settingsStore.setPadHaptics(on) }
    }

    fun assign(button: PadButton, keyCode: Int) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_remap", AppParams.VALUE to button.name.lowercase()))
        viewModelScope.launch { keyMapper.assign(button, keyCode) }
    }

    fun reset() = viewModelScope.launch { keyMapper.reset() }
}

