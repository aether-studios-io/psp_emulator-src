package app.letostudio.gameemulator.util

/**
 * App-wide error taxonomy (tech_stacks.md §10). Repositories are the only exception
 * boundary: they map every failure to one of these and never let raw exceptions
 * reach ViewModels. Never surface raw messages from `cause` to the user.
 *
 * TEMPLATE: add cases the app can actually render differently — an error type with no
 * distinct message is just [Unknown] with extra steps.
 */
sealed interface AppError {
    /** No network, timeout, unknown host. */
    data object Network : AppError

    /** Non-2xx HTTP response. */
    data class Http(val code: Int) : AppError

    /** Malformed response body. */
    data object Parse : AppError

    /** Authentication failure or expired credentials. */
    data object Auth : AppError

    /** Anything unclassified. */
    data class Unknown(val cause: Throwable) : AppError
}

/** Carrier that lets [AppError] travel inside [kotlin.Result]'s Throwable failure slot. */
class AppException(val error: AppError) : Exception(error::class.simpleName)
