package app.letostudio.gameemulator.util

import androidx.annotation.StringRes
import app.letostudio.gameemulator.R

/** ViewModels map AppError → string resources for UiState (tech_stacks.md §10). */
@StringRes
fun AppError.toUserMessageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    is AppError.Http -> R.string.error_http
    AppError.Parse -> R.string.error_parse
    AppError.Auth -> R.string.error_auth
    is AppError.Unknown -> R.string.error_unknown
}
