package app.letostudio.gameemulator.input

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyMapStore: DataStore<Preferences> by preferencesDataStore(name = "retronova_keymap")

/** Emulated buttons, identified by the Android keycode LibretroDroid expects. */
enum class PadButton(val keyCode: Int, val label: String) {
    A(KeyEvent.KEYCODE_BUTTON_A, "A"),
    B(KeyEvent.KEYCODE_BUTTON_B, "B"),
    X(KeyEvent.KEYCODE_BUTTON_X, "X"),
    Y(KeyEvent.KEYCODE_BUTTON_Y, "Y"),
    L(KeyEvent.KEYCODE_BUTTON_L1, "L"),
    R(KeyEvent.KEYCODE_BUTTON_R1, "R"),
    START(KeyEvent.KEYCODE_BUTTON_START, "START"),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT"),
    UP(KeyEvent.KEYCODE_DPAD_UP, "Up"),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "Down"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "Left"),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "Right"),
}

/**
 * Gamepad remapping (F09): physical keycode → emulated [PadButton]. Default = identity.
 * Stored as "src:dst,src:dst".
 */
@Singleton
class KeyMapper @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("mapping")

    val mapping: Flow<Map<Int, Int>> = context.keyMapStore.data.map { decode(it[key]) }

    suspend fun assign(button: PadButton, physicalKeyCode: Int) {
        context.keyMapStore.edit { prefs ->
            val m = decode(prefs[key]).toMutableMap()
            m.entries.removeAll { it.value == button.keyCode }
            m[physicalKeyCode] = button.keyCode
            prefs[key] = encode(m)
        }
    }

    suspend fun reset() = context.keyMapStore.edit { it.remove(key) }

    companion object {
        fun translate(mapping: Map<Int, Int>, keyCode: Int): Int = mapping[keyCode] ?: keyCode

        fun decode(raw: String?): Map<Int, Int> = raw.orEmpty().split(',').mapNotNull { pair ->
            val (a, b) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val src = a.toIntOrNull() ?: return@mapNotNull null
            val dst = b.toIntOrNull() ?: return@mapNotNull null
            src to dst
        }.toMap()

        fun encode(m: Map<Int, Int>): String = m.entries.joinToString(",") { "${it.key}:${it.value}" }

        /** Which physical key currently drives [button] (for the remap list). */
        fun physicalFor(mapping: Map<Int, Int>, button: PadButton): Int =
            mapping.entries.firstOrNull { it.value == button.keyCode }?.key ?: button.keyCode
    }
}
