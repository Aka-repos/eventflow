package com.app.eventflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdersDao {

    @Query("SELECT * FROM order_cache ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM order_cache")
    suspend fun clearOrders()

    @Query("SELECT * FROM ticket_cache ORDER BY purchasedAt DESC")
    fun observeTickets(): Flow<List<TicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTickets(tickets: List<TicketEntity>)

    @Query("DELETE FROM ticket_cache")
    suspend fun clearTickets()
}
