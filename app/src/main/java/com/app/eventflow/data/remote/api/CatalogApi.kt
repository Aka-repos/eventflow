package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.catalog.CategoryDto
import com.app.eventflow.data.remote.dto.catalog.EventDetailDto
import com.app.eventflow.data.remote.dto.catalog.EventSummaryDto
import com.app.eventflow.data.remote.dto.catalog.EventsPageDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Métodos = operationId del OpenAPI (tags catalog y me/favoritos). */
interface CatalogApi {

    @GET("events")
    suspend fun listEvents(
        @Query("q") q: String? = null,
        @Query("categoryId") categoryId: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): EventsPageDto

    @GET("events/{eventId}")
    suspend fun getEvent(@Path("eventId") eventId: String): DataEnvelope<EventDetailDto>

    @GET("categories")
    suspend fun listCategories(): DataEnvelope<List<CategoryDto>>

    @GET("me/favorites")
    suspend fun listFavorites(): DataEnvelope<List<EventSummaryDto>>

    @PUT("me/favorites/{eventId}")
    suspend fun addFavorite(@Path("eventId") eventId: String): Response<Unit>

    @DELETE("me/favorites/{eventId}")
    suspend fun removeFavorite(@Path("eventId") eventId: String): Response<Unit>
}
