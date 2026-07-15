package com.app.eventflow.core.di

import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.local.CatalogDao
import com.app.eventflow.data.local.OrdersDao
import com.app.eventflow.data.local.SessionUserDao
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.remote.api.CatalogApi
import com.app.eventflow.data.remote.api.CheckInApi
import com.app.eventflow.data.remote.api.MeApi
import com.app.eventflow.data.remote.api.OrdersApi
import com.app.eventflow.data.remote.api.RefundApi
import com.app.eventflow.data.repository.AuthRepositoryImpl
import com.app.eventflow.data.repository.CatalogRepositoryImpl
import com.app.eventflow.data.repository.CheckInRepositoryImpl
import com.app.eventflow.data.repository.OrdersRepositoryImpl
import com.app.eventflow.data.repository.RefundRepositoryImpl
import com.app.eventflow.domain.repository.AuthRepository
import com.app.eventflow.domain.repository.CatalogRepository
import com.app.eventflow.domain.repository.CheckInRepository
import com.app.eventflow.domain.repository.OrdersRepository
import com.app.eventflow.domain.repository.RefundRepository
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
        meApi: MeApi,
        tokenStore: TokenStore,
        dao: SessionUserDao,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AuthRepository = AuthRepositoryImpl(api, meApi, tokenStore, dao, problemConverter, ioDispatcher)

    @Provides
    @Singleton
    fun catalogRepository(
        api: CatalogApi,
        dao: CatalogDao,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CatalogRepository = CatalogRepositoryImpl(api, dao, problemConverter, ioDispatcher)

    @Provides
    @Singleton
    fun ordersRepository(
        api: OrdersApi,
        dao: OrdersDao,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): OrdersRepository = OrdersRepositoryImpl(api, dao, problemConverter, ioDispatcher)

    @Provides
    @Singleton
    fun checkInRepository(
        api: CheckInApi,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CheckInRepository = CheckInRepositoryImpl(api, problemConverter, ioDispatcher)

    @Provides
    @Singleton
    fun refundRepository(
        api: RefundApi,
        problemConverter: ProblemConverter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): RefundRepository = RefundRepositoryImpl(api, problemConverter, ioDispatcher)
}
