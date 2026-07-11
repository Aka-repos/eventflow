package com.app.eventflow.core.network

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Traduce respuestas problem+json y fallos de transporte a [AppError]. Única frontera de errores de red. */
class ProblemConverter(private val json: Json) {

    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is HttpException -> fromHttp(throwable)
        is IOException -> AppError.Network
        else -> AppError.Unknown()
    }

    private fun fromHttp(exception: HttpException): AppError {
        val problem = parseProblem(exception)
        val code = problem?.code.orEmpty()
        return when (exception.code()) {
            401 -> AppError.Auth
            403 -> AppError.Forbidden(code.ifEmpty { "forbidden" })
            404 -> AppError.NotFound
            409 -> AppError.Conflict(code.ifEmpty { "conflict" }, problem?.conflictVersion)
            422 -> if (problem?.errors != null) {
                AppError.Validation(problem.errors.associate { it.field to it.message })
            } else {
                AppError.Business(code.ifEmpty { "validation_error" }, problem?.detail)
            }
            402 -> AppError.Business(code.ifEmpty { "payment_failed" }, problem?.detail)
            429 -> AppError.RateLimited(problem?.retryAfterSeconds)
            in 400..499 -> AppError.Business(code.ifEmpty { "bad_request" }, problem?.detail)
            else -> AppError.Unknown(problem?.traceId)
        }
    }

    /** El `code` original viaja para casos que la UI distingue (p. ej. invalid_credentials vs token). */
    fun problemCode(throwable: Throwable): String? =
        (throwable as? HttpException)?.let { parseProblem(it)?.code }

    private fun parseProblem(exception: HttpException): ProblemDto? = runCatching {
        exception.response()?.errorBody()?.string()?.let { body ->
            json.decodeFromString<ProblemDto>(body)
        }
    }.getOrNull()
}
