package com.app.eventflow.core.network

/**
 * Error de aplicación tipado (contrato docs/api/09 §1). La UI enruta por [code], jamás por texto.
 */
sealed interface AppError {
    data object Network : AppError
    data object Auth : AppError
    data class Forbidden(val code: String) : AppError
    data object NotFound : AppError
    data class Conflict(val code: String, val conflictVersion: Int? = null) : AppError
    data class Business(val code: String, val detail: String?) : AppError
    data class Validation(val fields: Map<String, String>) : AppError
    data class RateLimited(val retryAfterSeconds: Int?) : AppError
    data class Unknown(val traceId: String? = null) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}
