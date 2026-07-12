package com.app.eventflow.data.repository

import com.app.eventflow.core.di.IoDispatcher
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.map
import com.app.eventflow.core.network.safeApiCall
import com.app.eventflow.data.local.CatalogDao
import com.app.eventflow.data.local.PendingFavoriteOpEntity
import com.app.eventflow.data.mapper.toDomain
import com.app.eventflow.data.mapper.toFavoriteEntity
import com.app.eventflow.data.remote.api.CatalogApi
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.EventsPage
import com.app.eventflow.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val api: CatalogApi,
    private val dao: CatalogDao,
    private val converter: ProblemConverter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : CatalogRepository {

    override suspend fun searchEvents(query: EventQuery): AppResult<EventsPage> =
        safeApiCall(dispatcher, converter) {
            api.listEvents(
                q = query.q?.takeIf { it.isNotBlank() },
                categoryId = query.categoryId,
                sort = if (query.sortDescending) "-startsAt" else null,
                cursor = query.cursor,
                limit = query.limit,
            )
        }.map { page ->
            EventsPage(page.data.map { it.toDomain() }, page.meta.nextCursor)
        }

    override suspend fun getEventDetail(eventId: String): AppResult<EventDetail> =
        safeApiCall(dispatcher, converter) { api.getEvent(eventId) }.map { it.data.toDomain() }

    override suspend fun getCategories(): AppResult<List<Category>> =
        safeApiCall(dispatcher, converter) { api.listCategories() }.map { env -> env.data.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<EventSummary>> =
        dao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshFavorites(): AppResult<Unit> {
        flushPendingOps()
        return safeApiCall(dispatcher, converter) { api.listFavorites() }.map { env ->
            val now = System.currentTimeMillis()
            dao.clearFavorites()
            dao.upsertFavorites(env.data.mapIndexed { index, dto ->
                // savedAt decreciente preserva el orden del backend (más reciente primero)
                dto.toDomain().toFavoriteEntity(savedAt = now - index)
            })
        }
    }

    override suspend fun toggleFavorite(event: EventSummary, favorite: Boolean): AppResult<Unit> {
        // 1. Optimista: el estado local cambia YA (funciona offline)
        if (favorite) {
            dao.upsertFavorite(event.toFavoriteEntity(System.currentTimeMillis()))
        } else {
            dao.deleteFavorite(event.id)
        }
        // 2. Cola durable + intento de sincronización inmediato
        dao.enqueueOp(PendingFavoriteOpEntity(event.id, favorite, System.currentTimeMillis()))
        val result = pushFavorite(event.id, favorite)
        return when {
            result is AppResult.Success -> {
                dao.clearOp(event.id)
                AppResult.Success(Unit)
            }
            // Sin red: queda encolado, la UI mantiene el estado optimista (api/09)
            result is AppResult.Failure && result.error is AppError.Network -> AppResult.Success(Unit)
            else -> {
                // Error de negocio (p. ej. 404): revertir local y descartar la operación
                dao.clearOp(event.id)
                if (favorite) dao.deleteFavorite(event.id) else dao.upsertFavorite(
                    event.toFavoriteEntity(System.currentTimeMillis()),
                )
                result as AppResult.Failure
            }
        }
    }

    private suspend fun flushPendingOps() {
        for (op in dao.pendingOps()) {
            val result = pushFavorite(op.eventId, op.add)
            when {
                result is AppResult.Success -> dao.clearOp(op.eventId)
                result is AppResult.Failure && result.error is AppError.Network -> return
                else -> dao.clearOp(op.eventId) // negocio: la op ya no aplica
            }
        }
    }

    private suspend fun pushFavorite(eventId: String, add: Boolean): AppResult<Unit> =
        safeApiCall(dispatcher, converter) {
            val response = if (add) api.addFavorite(eventId) else api.removeFavorite(eventId)
            if (!response.isSuccessful) {
                throw retrofit2.HttpException(response)
            }
        }
}
