package app.letostudio.gameemulator.analytics

import java.security.MessageDigest

/** RetroNova param names (analytics.md §3). */
object AppParams {
    const val SCREEN_NAME = "screen_name"
    const val SCREEN_CLASS = "screen_class"
    const val STEP = "step"
    const val IS_CHANGE = "is_change"
    const val TRIGGER = "trigger"
    const val ADDED = "added"
    const val SKIPPED = "skipped"
    const val TOTAL_FILES = "total_files"
    const val DURATION_MS = "duration_ms"
    const val REASON = "reason"
    const val FORMAT = "format"
    const val SECONDS_SINCE_INSTALL = "seconds_since_install"
    const val FILTER = "filter"
    const val RESULTS = "results"
    const val ON = "on"
    const val GAME_HASH = "game_hash"
    const val SOURCE = "source"
    const val BACKEND = "backend"
    const val RESOLUTION = "resolution"
    const val DURATION_SEC = "duration_sec"
    const val FPS_AVG = "fps_avg"
    const val FIX_LAG_STEPS = "fix_lag_steps"
    const val GAMEPAD = "gamepad"
    const val EXIT = "exit"
    const val SLOT_TYPE = "slot_type"
    const val SLOT_INDEX = "slot_index"
    const val FROM = "from"
    const val TO = "to"
    const val SPEED = "speed"
    const val VENDOR_ID = "vendor_id"
    const val PRODUCT_ID = "product_id"
    const val TIER = "tier"
    const val SCOPE = "scope"
    const val PRESET = "preset"
    const val CONTROLS_MOVED = "controls_moved"
    const val METHOD = "method"
    const val ACTIVE_COUNT = "active_count"
    const val ITEM_ID = "item_id"
    const val CONTENT_TYPE = "content_type"
    const val PLAN = "plan"
    const val TRIAL = "trial"
    const val PLAN_SELECTED = "plan_selected"
    const val CURRENT_PLAN = "current_plan"
    const val CODE = "code"
    const val PLACEMENT = "placement"
    const val MODE = "mode"
    const val KEY = "key"
    const val VALUE = "value"
    const val KIND = "kind"
    const val API = "api"
}

/**
 * `game_hash` (analytics.md §1): first 12 hex chars of SHA-256 of the disc ID from PARAM.SFO,
 * or of "size:<bytes>" + the first 64 KB when there is no disc ID. Never the title.
 */
object GameHash {
    fun ofDiscId(discId: String): String = sha12(discId.trim().uppercase().toByteArray())

    fun ofContent(sizeBytes: Long, head: ByteArray): String =
        sha12("size:$sizeBytes".toByteArray() + head.copyOf(minOf(head.size, 64 * 1024)))

    private fun sha12(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
}
