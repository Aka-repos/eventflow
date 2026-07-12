package com.app.eventflow.data

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.data.local.CatalogDao
import com.app.eventflow.data.local.FavoriteEventEntity
import com.app.eventflow.data.local.PendingFavoriteOpEntity
import com.app.eventflow.data.remote.api.CatalogApi
import com.app.eventflow.data.repository.CatalogRepositoryImpl
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.testutil.anEventSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: CatalogRepositoryImpl
    private lateinit var dao: FakeCatalogDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.SECONDS)
                    .build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatalogApi::class.java)
        dao = FakeCatalogDao()
        repository = CatalogRepositoryImpl(api, dao, ProblemConverter(json), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `searchEvents maps page envelope with cursor`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":[{"id":"e1","title":"Concierto","venueName":"Estadio",
                  "startsAt":"2027-01-10T20:00:00Z","endsAt":"2027-01-10T23:00:00Z",
                  "timezone":"America/Panama","status":"PUBLISHED",
                  "category":{"id":1,"name":"Conciertos"},
                  "priceFrom":{"amount":"25.00","currency":"USD"}}],
                 "meta":{"hasNext":true,"nextCursor":"abc123"}}
                """.trimIndent(),
            ).setHeader("Content-Type", "application/json"),
        )

        val result = repository.searchEvents(EventQuery(q = "conc"))

        assertTrue(result is AppResult.Success)
        val page = (result as AppResult.Success).value
        assertEquals("e1", page.items.single().id)
        assertEquals("abc123", page.nextCursor)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("q=conc"))
    }

    @Test
    fun `toggleFavorite offline keeps optimistic state and queues op`() = runTest {
        server.shutdown() // sin red

        val result = repository.toggleFavorite(anEventSummary(id = "e9"), favorite = true)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("e9"), dao.favoriteRows.keys.toList())
        assertEquals(true, dao.ops["e9"]?.add)
    }

    @Test
    fun `toggleFavorite business error reverts local state`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"about:blank","title":"Not found","status":404,"detail":"x",
                    "instance":"/me/favorites/e9","code":"not_found","timestamp":"t","traceId":"tr"}"""),
        )

        val result = repository.toggleFavorite(anEventSummary(id = "e9"), favorite = true)

        assertTrue(result is AppResult.Failure)
        assertTrue(dao.favoriteRows.isEmpty())
        assertTrue(dao.ops.isEmpty())
    }

    @Test
    fun `refreshFavorites drains pending queue then syncs from server`() = runTest {
        dao.ops["e9"] = PendingFavoriteOpEntity("e9", add = true, createdAt = 1L)
        server.enqueue(MockResponse().setResponseCode(204)) // flush del PUT pendiente
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":[{"id":"e9","title":"Concierto","venueName":"Estadio",
                  "startsAt":"2027-01-10T20:00:00Z","endsAt":"2027-01-10T23:00:00Z",
                  "timezone":"America/Panama","status":"PUBLISHED",
                  "category":{"id":1,"name":"Conciertos"},"isFavorite":true}],
                 "meta":{"hasNext":false}}
                """.trimIndent(),
            ).setHeader("Content-Type", "application/json"),
        )

        val result = repository.refreshFavorites()

        assertTrue(result is AppResult.Success)
        assertTrue(dao.ops.isEmpty())
        assertEquals(listOf("e9"), dao.favoriteRows.keys.toList())
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
    }
}

/** DAO fake en memoria (kotlin/testing.md: fakes sobre mocks). */
class FakeCatalogDao : CatalogDao {

    val favoriteRows = LinkedHashMap<String, FavoriteEventEntity>()
    val ops = LinkedHashMap<String, PendingFavoriteOpEntity>()
    private val flow = MutableStateFlow<List<FavoriteEventEntity>>(emptyList())

    override fun observeFavorites(): Flow<List<FavoriteEventEntity>> = flow

    override suspend fun upsertFavorite(entity: FavoriteEventEntity) {
        favoriteRows[entity.id] = entity
        emit()
    }

    override suspend fun deleteFavorite(eventId: String) {
        favoriteRows.remove(eventId)
        emit()
    }

    override suspend fun clearFavorites() {
        favoriteRows.clear()
        emit()
    }

    override suspend fun upsertFavorites(entities: List<FavoriteEventEntity>) {
        entities.forEach { favoriteRows[it.id] = it }
        emit()
    }

    override suspend fun enqueueOp(op: PendingFavoriteOpEntity) {
        ops[op.eventId] = op
    }

    override suspend fun pendingOps(): List<PendingFavoriteOpEntity> = ops.values.toList()

    override suspend fun clearOp(eventId: String) {
        ops.remove(eventId)
    }

    private fun emit() {
        flow.value = favoriteRows.values.sortedByDescending { it.savedAt }
    }
}
