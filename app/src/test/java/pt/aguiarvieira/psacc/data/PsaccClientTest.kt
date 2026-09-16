package pt.aguiarvieira.psacc.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import pt.aguiarvieira.psacc.data.auth.ConfigStore
import pt.aguiarvieira.psacc.data.auth.ConnectionRepositoryImpl
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import pt.aguiarvieira.psacc.data.network.PsaccClient
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.dto.VehicleDto

class PsaccClientTest {

    private val server = MockWebServer()
    private val client = PsaccClient(OkHttpClient(), Fixtures.json)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun config(user: String = "psa", pass: String = "secret", path: String = "") =
        ServerConfig(server.url(path).toString().trimEnd('/'), user, pass)

    @Test
    fun `sends basic auth, keeps sub-path and encodes segments`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.vehicleInfo).build())

        client.getJson(config(path = "/psacc"), listOf("get_vehicleinfo", "VIN/1"), mapOf("from_cache" to "1"))

        val request = server.takeRequest()
        assertEquals(Credentials.basic("psa", "secret"), request.headers["Authorization"])
        assertEquals("/psacc/get_vehicleinfo/VIN%2F1", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("from_cache"))
    }

    @Test
    fun `no credentials means no auth header`() = runTest {
        server.enqueue(MockResponse.Builder().body("true").build())
        client.getJson(config(user = "", pass = ""), listOf("wakeup", "X"))
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        try {
            client.getJson(config(), listOf("get_vehicles"))
            fail("expected Unauthorized")
        } catch (_: PsaccException.Unauthorized) {
        }
    }

    @Test
    fun `html body maps to BadResponse`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>login</html>").build())
        try {
            client.get(config(), ListSerializer(VehicleDto.serializer()), listOf("get_vehicles"))
            fail("expected BadResponse")
        } catch (_: PsaccException.BadResponse) {
        }
    }

    @Test
    fun `connect saves only after a successful probe`() = runTest {
        val store = FakeConfigStore()
        val repo = ConnectionRepositoryImpl(store, client)

        server.enqueue(MockResponse.Builder().code(401).build())
        assertTrue(repo.verifyAndSave(server.url("/").toString(), "psa", "wrong").isFailure)
        assertNull(store.saved)
        assertNull(repo.config.value)

        server.enqueue(MockResponse.Builder().body("[]").build())
        assertTrue(repo.verifyAndSave(server.url("/").toString(), "psa", "secret").isFailure)
        assertNull(store.saved)

        server.enqueue(MockResponse.Builder().body(Fixtures.vehicles).build())
        val result = repo.verifyAndSave(server.url("/").toString() + "/", " psa ", "secret")
        assertEquals(1, result.getOrThrow())
        assertEquals("psa", store.saved!!.username)
        assertFalse(store.saved!!.baseUrl.endsWith("/"))
        assertEquals(store.saved, repo.config.value)

        repo.disconnect()
        assertNull(repo.config.value)
        assertNull(store.saved)
    }
}

private class FakeConfigStore : ConfigStore {
    var saved: ServerConfig? = null
    override fun load() = saved
    override fun save(config: ServerConfig) { saved = config }
    override fun clear() { saved = null }
}
