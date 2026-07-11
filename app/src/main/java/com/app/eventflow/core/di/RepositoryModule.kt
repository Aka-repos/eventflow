package com.app.eventflow.core.di

import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.local.SessionUserDao
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.repository.AuthRepositoryImpl
import com.app.eventflow.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun authRepository(
        api: AuthApi,
        tokenStore: TokenStore,
        dao: SessionUserDao,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AuthRepository = AuthRepositoryImpl(api, tokenStore, dao, problemConverter, ioDispatcher)
}
