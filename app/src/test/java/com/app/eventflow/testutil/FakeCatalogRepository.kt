package com.app.eventflow.testutil

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.EventsPage
import com.app.eventflow.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake determinista (kotlin/testing.md: fakes sobre mocks). */
class FakeCatalogRepository : CatalogRepository {

    var pages: MutableList<EventsPage> = mutableListOf(EventsPage(listOf(anEventSummary()), null))
    var searchError: AppError? = null
    var detail: EventDetail? = null
    var detailError: AppError? = null
    var categories: List<Category> = listOf(Category(1, "Conciertos", null))
    var toggleError: AppError? = null
    val favorites = MutableStateFlow<List<EventSummary>>(emptyList())
    var refreshError: AppError? = null
    val searchQueries = mutableListOf<EventQuery>()
    val toggles = mutableListOf<Pair<String, Boolean>>()

    override suspend fun searchEvents(query: EventQuery): AppResult<EventsPage> {
        searchQueries += query
        searchError?.let { return AppResult.Failure(it) }
        val index = if (query.cursor == null) 0 else minOf(pages.size - 1, 1)
        return AppResult.Success(pages[index])
    }

    override suspend fun getEventDetail(eventId: String): AppResult<EventDetail> {
        detailError?.let { return AppResult.Failure(it) }
        return detail?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)
    }

    override suspend fun getCategories(): AppResult<List<Category>> = AppResult.Success(categories)

    override fun observeFavorites(): Flow<List<EventSummary>> = favorites

    override suspend fun refreshFavorites(): AppResult<Unit> =
        refreshError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)

    override suspend fun toggleFavorite(event: EventSummary, favorite: Boolean): AppResult<Unit> {
        toggles += event.id to favorite
        toggleError?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }
}
