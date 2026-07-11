package com.app.eventflow.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Toda llamada Retrofit de repositorio pasa por aquí: IO dispatcher + mapeo a AppResult. */
suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    converter: ProblemConverter,
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(withContext(dispatcher) { block() })
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(converter.fromThrowable(throwable))
}
