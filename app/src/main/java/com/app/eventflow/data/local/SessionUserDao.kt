package com.app.eventflow.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionUserDao {

    @Upsert
    suspend fun upsert(user: SessionUserEntity)

    @Query("SELECT * FROM session_user LIMIT 1")
    fun observe(): Flow<SessionUserEntity?>

    @Query("DELETE FROM session_user")
    suspend fun clear()
}
