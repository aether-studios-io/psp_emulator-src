package app.letostudio.gameemulator.util

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

fun Throwable.toAppError(): AppError = when (this) {
    is AppException -> error
    is HttpException -> AppError.Http(code())
    is SerializationException -> AppError.Parse
    is IOException -> AppError.Network
    else -> AppError.Unknown(this)
}

/**
 * The repository-boundary catch: wraps [block], mapping any failure to [AppError]
 * inside a failed [Result]. CancellationException is always rethrown (tech_stacks.md §10).
 */
suspend fun <T> runCatchingAppError(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(AppException(e.toAppError()))
}

/** The [AppError] of a failed result, or null when successful. */
fun <T> Result<T>.appErrorOrNull(): AppError? = exceptionOrNull()?.toAppError()
