package com.app.eventflow.domain.usecase.catalog

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.EventsPage
import com.app.eventflow.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchEventsUseCase @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke(query: EventQuery): AppResult<EventsPage> = repository.searchEvents(query)
}

class GetEventDetailUseCase @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke(eventId: String): AppResult<EventDetail> = repository.getEventDetail(eventId)
}

class GetCategoriesUseCase @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke(): AppResult<List<Category>> = repository.getCategories()
}

class ObserveFavoritesUseCase @Inject constructor(private val repository: CatalogRepository) {
    operator fun invoke(): Flow<List<EventSummary>> = repository.observeFavorites()
}

class RefreshFavoritesUseCase @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refreshFavorites()
}

class ToggleFavoriteUseCase @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke(event: EventSummary, favorite: Boolean): AppResult<Unit> =
        repository.toggleFavorite(event, favorite)
}
