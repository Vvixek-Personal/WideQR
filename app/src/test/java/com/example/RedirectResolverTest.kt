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

class RedirectResolverTest {

    @Test
    fun `follows redirects up to final destination using HEAD and GET fallback`() = runTest {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url == "https://short.example/go" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(301)
                        .message("Moved Permanently")
                        .header("Location", "https://mid.example/step2")
                        .body("".toResponseBody(null))
                        .build()

                    url == "https://mid.example/step2" && req.method == "HEAD" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(405)
                        .message("Method Not Allowed")
                        .body("".toResponseBody(null))
                        .build()

                    url == "https://mid.example/step2" && req.method == "GET" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ok".toResponseBody(null))
                        .build()

                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val resolver = RedirectResolver(client)
        val result = resolver.inspectRedirects("https://short.example/go")

        assertTrue(result is RedirectInspectionState.Completed)
        val completed = result as RedirectInspectionState.Completed
        assertEquals("https://mid.example/step2", completed.finalUrl)
        assertEquals("mid.example", completed.finalHost)
        assertEquals(2, completed.hops.size)
        assertEquals("HEAD", completed.hops[0].method)
        assertEquals("GET", completed.hops[1].method)
    }

    @Test
    fun `blocks redirect to dangerous javascript or intent scheme`() = runTest {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "intent://evil/#Intent;end")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val resolver = RedirectResolver(client)
        val result = resolver.inspectRedirects("https://example.org/redir")

        assertTrue(result is RedirectInspectionState.Completed)
        val completed = result as RedirectInspectionState.Completed
        assertEquals("intent", completed.blockedDangerousScheme)
        assertTrue(completed.suspiciousReasons.any { it.contains("intent:") })
    }

    @Test
    fun `detects redirect loop cleanly`() = runTest {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val req = chain.request()
                val next = if (req.url.toString().endsWith("/a")) "https://loop.example/b" else "https://loop.example/a"
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", next)
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val resolver = RedirectResolver(client)
        val result = resolver.inspectRedirects("https://loop.example/a")

        assertTrue(result is RedirectInspectionState.Completed)
        val completed = result as RedirectInspectionState.Completed
        assertTrue(completed.suspiciousReasons.any { it.contains("loop", ignoreCase = true) })
    }
}
