package com.app.eventflow.data

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.data.local.OrderEntity
import com.app.eventflow.data.local.OrdersDao
import com.app.eventflow.data.local.TicketEntity
import com.app.eventflow.data.remote.api.OrdersApi
import com.app.eventflow.data.repository.OrdersRepositoryImpl
import com.app.eventflow.domain.model.orders.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: OrdersRepositoryImpl
    private lateinit var dao: FakeOrdersDao

    private val orderJson = """
        {"data":{"id":"o1","status":"PENDING","total":{"amount":"80.00","currency":"USD"},
         "expiresAt":"2027-01-01T12:15:00Z","createdAt":"2027-01-01T12:00:00Z",
         "items":[{"id":"i1","type":"TICKET","description":"VIP — Concierto","quantity":2,
                   "unitPrice":{"amount":"40.00","currency":"USD"}}]}}
    """.trimIndent()

    private val emptyPage = """{"data":[],"meta":{"hasNext":false}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OrdersApi::class.java)
        dao = FakeOrdersDao()
        repository = OrdersRepositoryImpl(api, dao, ProblemConverter(json), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createOrder sends idempotency key and maps envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(orderJson)
            .setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody(emptyPage).setHeader("Content-Type", "application/json"))

        val result = repository.createOrder("t1", 2)

        assertTrue(result is AppResult.Success)
        val order = (result as AppResult.Success).value
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals("80.00", order.total.amount)
        val request = server.takeRequest()
        assertNotNull(request.getHeader("Idempotency-Key"))
        assertTrue(request.body.readUtf8().contains("\"referenceId\":\"t1\""))
    }

    @Test
    fun `payment declined maps 402 problem to business error with detail`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402)
            .setHeader("Content-Type", "application/problem+json")
            .setBody("""{"type":"about:blank","title":"Payment failed","status":402,
                "detail":"La tarjeta fue rechazada por el emisor","instance":"/orders/o1/pay",
                "code":"payment_failed","timestamp":"t","traceId":"tr"}"""))

        val result = repository.payOrder("o1", "CARD")

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Business)
        assertEquals("payment_failed", (error as AppError.Business).code)
        assertEquals("La tarjeta fue rechazada por el emisor", error.detail)
    }

    @Test
    fun `tickets cached in room survive offline`() = runTest {
        dao.tickets.value = listOf(
            TicketEntity("tk1", "e1", "Concierto", "Arena", "2027-01-10T20:00:00Z", "America/Panama",
                "VIP", null, "ACTIVE", "PRIMARY", "2027-01-01T12:00:00Z", null, false),
        )
        server.shutdown() // sin red

        val cached = repository.observeTickets().first()
        assertEquals(1, cached.size)
        assertEquals("Concierto", cached.first().event?.title)

        val refresh = repository.refreshTickets()
        assertTrue(refresh is AppResult.Failure)
        assertTrue((refresh as AppResult.Failure).error is AppError.Network)
        // el cache NO se borra ante fallo de red
        assertEquals(1, repository.observeTickets().first().size)
    }
}

/** DAO fake en memoria. */
class FakeOrdersDao : OrdersDao {

    val orders = MutableStateFlow<List<OrderEntity>>(emptyList())
    val tickets = MutableStateFlow<List<TicketEntity>>(emptyList())

    override fun observeOrders(): Flow<List<OrderEntity>> = orders

    override suspend fun upsertOrders(rows: List<OrderEntity>) {
        orders.value = (orders.value.associateBy { it.id } + rows.associateBy { it.id }).values.toList()
    }

    override suspend fun clearOrders() {
        orders.value = emptyList()
    }

    override fun observeTickets(): Flow<List<TicketEntity>> = tickets

    override suspend fun upsertTickets(rows: List<TicketEntity>) {
        tickets.value = (tickets.value.associateBy { it.id } + rows.associateBy { it.id }).values.toList()
    }

    override suspend fun clearTickets() {
        tickets.value = emptyList()
    }
}
