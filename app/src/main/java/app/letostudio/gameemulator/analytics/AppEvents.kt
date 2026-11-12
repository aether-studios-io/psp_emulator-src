package app.letostudio.gameemulator.analytics

/**
 * RetroNova event names (analytics.md §3). The template's own taxonomy ([AnalyticsEvents]:
 * ad_impression, paywall_view/dismiss…) stays in place; RetroNova events live here so the
 * two sets cannot collide. snake_case, ≤ 40 chars, no reserved prefixes — AppEventsTest.
 */
object AppEvents {
    const val SCREEN_VIEW = "screen_view"

    // Onboarding
    const val TUTORIAL_BEGIN = "tutorial_begin"
    const val ONBOARDING_STEP = "onboarding_step"
    const val ONBOARDING_SKIP = "onboarding_skip"
    const val TUTORIAL_COMPLETE = "tutorial_complete"

    // Library & import
    const val IMPORT_FOLDER_PICK = "import_folder_pick"
    const val IMPORT_SCAN_START = "import_scan_start"
    const val IMPORT_SCAN_SUCCESS = "import_scan_success"
    const val IMPORT_SCAN_FAIL = "import_scan_fail"
    const val IMPORT_FILE_ADD = "import_file_add"
    const val FIRST_GAME_ADDED = "first_game_added"
    const val LIBRARY_FILTER = "library_filter"
    const val LIBRARY_SEARCH = "library_search"
    const val GAME_FAVORITE = "game_favorite"

    // Play session
    const val GAME_START = "game_start"
    const val FIRST_GAME_START = "first_game_start"
    const val GAME_START_FAIL = "game_start_fail"
    const val GAME_SESSION_END = "game_session_end"
    const val SAVE_STATE = "save_state"
    const val LOAD_STATE = "load_state"
    const val SAVE_SLOT_LIMIT = "save_slot_limit"
    const val FAST_FORWARD = "fast_forward"
    const val SCREENSHOT_TAKE = "screenshot_take"
    const val GAMEPAD_CONNECTED = "gamepad_connected"

    // Differentiators
    const val SMART_SETUP_RUN = "smart_setup_run"
    const val FIX_LAG_TAP = "fix_lag_tap"
    const val FIX_LAG_SHEET_OPEN = "fix_lag_sheet_open"
    const val CONTROLS_LAYOUT_SAVE = "controls_layout_save"
    const val CHEAT_ADD = "cheat_add"
    const val CHEAT_TOGGLE = "cheat_toggle"
    const val DRIVE_BACKUP_START = "drive_backup_start"
    const val DRIVE_BACKUP_SUCCESS = "drive_backup_success"
    const val DRIVE_BACKUP_FAIL = "drive_backup_fail"
    const val HOMEBREW_OPEN = "homebrew_open"
    const val HOMEBREW_GUIDE_OPEN = "homebrew_guide_open"
    const val SHARE = "share"

    // Monetization
    const val PAYWALL_VIEW = "paywall_view"
    const val PAYWALL_PLAN_SELECT = "paywall_plan_select"
    const val PAYWALL_CTA_TAP = "paywall_cta_tap"
    const val PAYWALL_CLOSE = "paywall_close"
    const val PAYWALL_LIFETIME_NOTE_VIEW = "paywall_lifetime_note_view"
    const val PURCHASE_SUCCESS = "purchase_success"
    const val PURCHASE_FAIL = "purchase_fail"
    const val PURCHASE_CANCEL = "purchase_cancel"
    const val MANAGE_SUBSCRIPTION_TAP = "manage_subscription_tap"
    const val CHANGE_PLAN_START = "change_plan_start"
    const val CHANGE_PLAN_SUCCESS = "change_plan_success"
    const val REWARDED_START = "rewarded_start"
    const val REWARDED_EARNED = "rewarded_earned"
    const val INTERSTITIAL_SHOW = "interstitial_show"
    const val INTERSTITIAL_SKIP = "interstitial_skip"

    // Settings & technical
    const val THEME_CHANGE = "theme_change"
    const val SETTING_CHANGE = "setting_change"
    const val RATE_TAP = "rate_tap"
    const val CONTACT_US_TAP = "contact_us_tap"
    const val PRIVACY_OPTIONS_OPEN = "privacy_options_open"
    const val OSS_LICENSES_OPEN = "oss_licenses_open"
    const val PERMISSION_DENIED = "permission_denied"
    const val AUDIO_ERROR = "audio_error"
    const val GL_ERROR = "gl_error"

    val ALL: List<String> = listOf(
        SCREEN_VIEW, TUTORIAL_BEGIN, ONBOARDING_STEP, ONBOARDING_SKIP, TUTORIAL_COMPLETE,
        IMPORT_FOLDER_PICK, IMPORT_SCAN_START, IMPORT_SCAN_SUCCESS, IMPORT_SCAN_FAIL, IMPORT_FILE_ADD,
        FIRST_GAME_ADDED, LIBRARY_FILTER, LIBRARY_SEARCH, GAME_FAVORITE, GAME_START, FIRST_GAME_START,
        GAME_START_FAIL, GAME_SESSION_END, SAVE_STATE, LOAD_STATE, SAVE_SLOT_LIMIT, FAST_FORWARD,
        SCREENSHOT_TAKE, GAMEPAD_CONNECTED, SMART_SETUP_RUN, FIX_LAG_TAP, FIX_LAG_SHEET_OPEN,
        CONTROLS_LAYOUT_SAVE, CHEAT_ADD, CHEAT_TOGGLE, DRIVE_BACKUP_START, DRIVE_BACKUP_SUCCESS,
        DRIVE_BACKUP_FAIL, HOMEBREW_OPEN, HOMEBREW_GUIDE_OPEN, SHARE, PAYWALL_VIEW, PAYWALL_PLAN_SELECT,
        PAYWALL_CTA_TAP, PAYWALL_CLOSE, PAYWALL_LIFETIME_NOTE_VIEW, PURCHASE_SUCCESS, PURCHASE_FAIL,
        PURCHASE_CANCEL, MANAGE_SUBSCRIPTION_TAP, CHANGE_PLAN_START, CHANGE_PLAN_SUCCESS,
        REWARDED_START, REWARDED_EARNED, INTERSTITIAL_SHOW, INTERSTITIAL_SKIP, THEME_CHANGE,
        SETTING_CHANGE, RATE_TAP, CONTACT_US_TAP, PRIVACY_OPTIONS_OPEN, OSS_LICENSES_OPEN,
        PERMISSION_DENIED, AUDIO_ERROR, GL_ERROR,
    )
}

/** `screen_name` values (analytics.md §2). */
object AppScreens {
    const val ONBOARDING_1 = "onboarding_1"
    const val ONBOARDING_2 = "onboarding_2"
    const val ONBOARDING_3 = "onboarding_3"
    const val PAYWALL = "paywall"
    const val LIBRARY = "library"
    const val IMPORT = "import"
    const val GAME_DETAIL = "game_detail"
    const val HOMEBREW = "homebrew"
    const val PLAYER = "player"
    const val PAUSE_MENU = "pause_menu"
    const val CHEATS = "cheats"
    const val CONTROLS_EDITOR = "controls_editor"
    const val SETTINGS = "settings"
    const val EMULATOR_SETTINGS = "emulator_settings"
    const val FIX_LAG_SHEET = "fix_lag_sheet"
    const val HOMEBREW_GUIDE = "homebrew_guide"
    const val OSS_LICENSES = "oss_licenses"
    const val DEBUG = "debug"
}

/** User property names (analytics.md §4). */
object AppUserProps {
    const val IS_PRO = "is_pro"
    const val PRO_PLAN = "pro_plan"
    const val THEME_MODE = "theme_mode"
    const val GAMES_BUCKET = "games_bucket"
    const val SAVES_BUCKET = "saves_bucket"
    const val DEVICE_TIER = "device_tier"
    const val USES_GAMEPAD = "uses_gamepad"
    const val CHEATS_ACTIVE = "cheats_active"

    fun gamesBucket(count: Int) = when {
        count <= 0 -> "0"
        count <= 3 -> "1-3"
        count <= 10 -> "4-10"
        count <= 30 -> "11-30"
        else -> "31+"
    }

    fun savesBucket(count: Int) = when {
        count <= 0 -> "0"
        count <= 5 -> "1-5"
        count <= 20 -> "6-20"
        else -> "21+"
    }
}
