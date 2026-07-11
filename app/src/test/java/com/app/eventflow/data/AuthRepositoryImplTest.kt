package com.app.eventflow.data

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.data.local.SessionUserDao
import com.app.eventflow.data.local.SessionUserEntity
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.repository.AuthRepositoryImpl
import com.app.eventflow.core.security.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepositoryImpl
    private val tokenStore = FakeTokenStore()
    private val dao = FakeSessionUserDao()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        repository = AuthRepositoryImpl(api, tokenStore, dao, ProblemConverter(json), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueLoginOk() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"data":{"accessToken":"jwt-abc","accessTokenExpiresIn":900,"refreshToken":"rt-1",
                      "user":{"id":"u-1","email":"ana@mail.com","fullName":"Ana P.",
                              "roles":["ATTENDEE","ROL_FUTURO"],"createdAt":"2026-07-10T12:00:00Z"}}}
                    """.trimIndent(),
                ),
        )
    }

    @Test
    fun `login exitoso guarda tokens y usuario, y tolera roles desconocidos`() = runTest {
        enqueueLoginOk()

        val result = repository.login("ana@mail.com", "S3gura!pass")

        assertTrue(result is AppResult.Success)
        assertEquals("jwt-abc", tokenStore.accessToken())
        assertEquals("rt-1", tokenStore.refreshToken())
        assertNotNull(dao.stored)
        // rol desconocido del servidor → UNKNOWN (tolerant reader, docs/api/06 §5)
        val user = (result as AppResult.Success).value
        assertEquals(listOf("ATTENDEE", "UNKNOWN"), user.roles.map { it.name })
    }

    @Test
    fun `401 problem+json se mapea a AppError_Auth`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """
                    {"type":"https://api.eventflow.app/errors/invalid_credentials","title":"Invalid credentials",
                     "status":401,"detail":"Email o contraseña incorrectos","instance":"/auth/login",
                     "code":"invalid_credentials","timestamp":"2026-07-10T12:00:00Z","traceId":"t-1"}
                    """.trimIndent(),
                ),
        )

        val result = repository.login("ana@mail.com", "mala")

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Auth, (result as AppResult.Failure).error)
        assertNull(tokenStore.accessToken())
    }

    @Test
    fun `409 email duplicado llega con su code para la UI`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"status":409,"code":"email_already_registered","title":"c","detail":"d"}"""),
        )

        val result = repository.register("ana@mail.com", "S3gura!pass", "Ana", null)

        val error = (result as AppResult.Failure).error
        assertEquals(AppError.Conflict("email_already_registered", null), error)
    }

    @Test
    fun `logout limpia sesion local aunque el servidor falle`() = runTest {
        enqueueLoginOk()
        repository.login("ana@mail.com", "S3gura!pass")
        server.enqueue(MockResponse().setResponseCode(500))

        repository.logout()

        assertNull(tokenStore.accessToken())
        assertNull(dao.stored)
    }

    private class FakeTokenStore : TokenStore {
        private var access: String? = null
        private var refresh: String? = null
        private val _hasSession = MutableStateFlow(false)
        override val hasSession: StateFlow<Boolean> = _hasSession
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun save(accessToken: String, refreshToken: String) {
            access = accessToken; refresh = refreshToken; _hasSession.value = true
        }
        override fun clear() {
            access = null; refresh = null; _hasSession.value = false
        }
    }

    private class FakeSessionUserDao : SessionUserDao {
        var stored: SessionUserEntity? = null
        private val flow = MutableStateFlow<SessionUserEntity?>(null)
        override suspend fun upsert(user: SessionUserEntity) {
            stored = user; flow.value = user
        }
        override fun observe(): Flow<SessionUserEntity?> = flow
        override suspend fun clear() {
            stored = null; flow.value = null
        }
    }
}
