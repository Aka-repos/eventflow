package com.app.eventflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Favorito cacheado como resumen de evento (usable offline — api/09). */
@Entity(tableName = "favorite_event")
data class FavoriteEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val status: String,
    val coverUrl: String?,
    val categoryId: Int,
    val categoryName: String,
    val categoryIcon: String?,
    val priceAmount: String?,
    val priceCurrency: String?,
    val savedAt: Long,
)

/**
 * Cola offline de favoritos (la ÚNICA mutación encolable según la matriz api/09):
 * se drena en cada refresh; add=false representa un DELETE pendiente.
 */
@Entity(tableName = "pending_favorite_op")
data class PendingFavoriteOpEntity(
    @PrimaryKey val eventId: String,
    val add: Boolean,
    val createdAt: Long,
)
