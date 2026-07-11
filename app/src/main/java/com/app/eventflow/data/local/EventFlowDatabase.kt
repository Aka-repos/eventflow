package com.app.eventflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionUserEntity::class], version = 1, exportSchema = true)
abstract class EventFlowDatabase : RoomDatabase() {

    abstract fun sessionUserDao(): SessionUserDao
}
