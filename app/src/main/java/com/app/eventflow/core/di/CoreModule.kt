package com.app.eventflow.core.di

import android.content.Context
import androidx.room.Room
import com.app.eventflow.core.security.EncryptedTokenStore
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.local.EventFlowDatabase
import com.app.eventflow.data.local.SessionUserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun tokenStore(@ApplicationContext context: Context): TokenStore = EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): EventFlowDatabase =
        Room.databaseBuilder(context, EventFlowDatabase::class.java, "eventflow.db").build()

    @Provides
    fun sessionUserDao(database: EventFlowDatabase): SessionUserDao = database.sessionUserDao()
}
