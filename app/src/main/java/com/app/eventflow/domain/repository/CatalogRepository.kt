package com.app.eventflow.domain.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.EventsPage
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {

    suspend fun searchEvents(query: EventQuery): AppResult<EventsPage>

    suspend fun getEventDetail(eventId: String): AppResult<EventDetail>

    suspend fun getCategories(): AppResult<List<Category>>

    /** Favoritos cacheados en Room (usables offline — api/09). */
    fun observeFavorites(): Flow<List<EventSummary>>

    /** Drena la cola offline de favoritos y resincroniza desde el backend. */
    suspend fun refreshFavorites(): AppResult<Unit>

    /**
     * Optimista y encolable offline (única mutación con cola según api/09):
     * aplica localmente, registra la operación pendiente e intenta sincronizar.
     */
    suspend fun toggleFavorite(event: EventSummary, favorite: Boolean): AppResult<Unit>
}
