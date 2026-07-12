package com.app.eventflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM favorite_event ORDER BY savedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteEventEntity)

    @Query("DELETE FROM favorite_event WHERE id = :eventId")
    suspend fun deleteFavorite(eventId: String)

    @Query("DELETE FROM favorite_event")
    suspend fun clearFavorites()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorites(entities: List<FavoriteEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOp(op: PendingFavoriteOpEntity)

    @Query("SELECT * FROM pending_favorite_op ORDER BY createdAt ASC")
    suspend fun pendingOps(): List<PendingFavoriteOpEntity>

    @Query("DELETE FROM pending_favorite_op WHERE eventId = :eventId")
    suspend fun clearOp(eventId: String)
}
