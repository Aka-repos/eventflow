package com.app.eventflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionUserEntity::class, FavoriteEventEntity::class, PendingFavoriteOpEntity::class,
        OrderEntity::class, TicketEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class EventFlowDatabase : RoomDatabase() {

    abstract fun sessionUserDao(): SessionUserDao

    abstract fun catalogDao(): CatalogDao

    abstract fun ordersDao(): OrdersDao
}
