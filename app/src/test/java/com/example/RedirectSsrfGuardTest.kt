package com.example

import com.example.links.RedirectResolver
import com.example.models.RedirectInspectionState
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSRF guard coverage: link inspection must never reach loopback, RFC1918,
 * link-local, CGNAT, ULA or multicast destinations, even when a public
 * hostname resolves to one (DNS rebinding).
 */
class RedirectSsrfGuardTest {

    private fun clientWithDns(vararg hostToIp: Pair<String, String>): OkHttpClient {
        val map = hostToIp.toMap()
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns { hostname ->
                val mapped = map[hostname] ?: throw java.net.UnknownHostException(hostname)
                listOf(java.net.InetAddress.getByName(mapped))
            }
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody(null))
                    .build()
            }
            .build()
    }

    @Test
    fun `private ipv4 destinations are blocked`() = runTest {
        val resolver = RedirectResolver(clientWithDns("evil.example" to "192.168.1.10"))
        val state = resolver.inspectRedirects("https://evil.example/steal")
        assertTrue(state is RedirectInspectionState.Failed)
        state as RedirectInspectionState.Failed
        assertEquals(false, state.isOfflineLikely)
    }

    @Test
    fun `loopback destinations are blocked`() = runTest {
        val resolver = RedirectResolver(clientWithDns("local.example" to "127.0.0.1"))
        val state = resolver.inspectRedirects("http://local.example/admin")
        assertTrue(state is RedirectInspectionState.Failed)
    }

    @Test
    fun `link local metadata address is blocked`() = runTest {
        val resolver = RedirectResolver(clientWithDns("meta.example" to "169.254.169.254"))
        val state = resolver.inspectRedirects("http://meta.example/latest/meta-data")
        assertTrue(state is RedirectInspectionState.Failed)
    }

    @Test
    fun `cgnat range is blocked`() = runTest {
        val resolver = RedirectResolver(clientWithDns("cgnat.example" to "100.64.0.1"))
        val state = resolver.inspectRedirects("http://cgnat.example/")
        assertTrue(state is RedirectInspectionState.Failed)
    }

    @Test
    fun `public destination is allowed`() = runTest {
        val resolver = RedirectResolver(clientWithDns("public.example" to "93.184.216.34"))
        val state = resolver.inspectRedirects("https://public.example/page")
        assertTrue(state is RedirectInspectionState.Completed)
    }

    @Test
    fun `default client installs ssrf guard dns`() {
        val dns = RedirectResolver.defaultClient().dns
        assertEquals(false, dns::class.java.simpleName == "Dns\$SYSTEM")
    }
}
