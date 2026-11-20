package com.example.links

import com.example.models.RedirectHop
import com.example.models.RedirectInspectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class RedirectResolver(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        const val MAX_HOPS = 6

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SsrfGuardDns())
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun inspectRedirects(startUrl: String): RedirectInspectionState = withContext(Dispatchers.IO) {
        val initialScheme = LinkInspector.extractScheme(startUrl)
        if (initialScheme != "http" && initialScheme != "https") {
            return@withContext RedirectInspectionState.Failed(
                message = "Only http:// and https:// URLs can be inspected.",
                isOfflineLikely = false
            )
        }

        val hops = mutableListOf<RedirectHop>()
        val visited = mutableSetOf<String>()
        var currentUrl = startUrl.trim()

        try {
            for (hopIndex in 0 until MAX_HOPS) {
                if (!visited.add(currentUrl)) {
                    val info = LinkInspector.inspect(currentUrl)
                    return@withContext RedirectInspectionState.Completed(
                        hops = hops,
                        finalUrl = currentUrl,
                        finalHost = info.host,
                        finalIsHttps = info.isHttps,
                        reachedMaxHops = false,
                        blockedDangerousScheme = null,
                        suspiciousReasons = info.suspiciousReasons + "Redirect loop detected",
                        statusNote = "Stopped because a redirect loop was detected."
                    )
                }

                val responseResult = executeHeaderCheck(currentUrl)
                hops.add(
                    RedirectHop(
                        url = currentUrl,
                        statusCode = responseResult.code,
                        method = responseResult.methodUsed
                    )
                )

                val location = responseResult.locationHeader
                if (responseResult.isRedirect && !location.isNullOrBlank()) {
                    val nextUrl = resolveRelativeLocation(currentUrl, location)
                    if (nextUrl == null) {
                        val info = LinkInspector.inspect(currentUrl)
                        return@withContext RedirectInspectionState.Completed(
                            hops = hops,
                            finalUrl = currentUrl,
                            finalHost = info.host,
                            finalIsHttps = info.isHttps,
                            reachedMaxHops = false,
                            blockedDangerousScheme = null,
                            suspiciousReasons = info.suspiciousReasons + "Malformed redirect target",
                            statusNote = "Server returned a malformed redirect location."
                        )
                    }

                    val nextScheme = LinkInspector.extractScheme(nextUrl)
                    if (nextScheme != "http" && nextScheme != "https") {
                        val info = LinkInspector.inspect(currentUrl)
                        return@withContext RedirectInspectionState.Completed(
                            hops = hops,
                            finalUrl = nextUrl,
                            finalHost = info.host,
                            finalIsHttps = false,
                            reachedMaxHops = false,
                            blockedDangerousScheme = nextScheme,
                            suspiciousReasons = info.suspiciousReasons +
                                "Blocked redirect to non-web scheme ($nextScheme:)",
                            statusNote = "Redirect tried to switch to $nextScheme: scheme and was halted."
                        )
                    }

                    currentUrl = nextUrl
                } else {
                    val finalInfo = LinkInspector.inspect(currentUrl)
                    val note = when {
                        hops.size == 1 -> "No redirect — URL points directly to this destination (HTTP ${responseResult.code})."
                        else -> "Resolved across ${hops.size - 1} redirect(s) (HTTP ${responseResult.code})."
                    }
                    return@withContext RedirectInspectionState.Completed(
                        hops = hops,
                        finalUrl = currentUrl,
                        finalHost = finalInfo.host,
                        finalIsHttps = finalInfo.isHttps,
                        reachedMaxHops = false,
                        blockedDangerousScheme = null,
                        suspiciousReasons = finalInfo.suspiciousReasons,
                        statusNote = note
                    )
                }
            }

            val finalInfo = LinkInspector.inspect(currentUrl)
            RedirectInspectionState.Completed(
                hops = hops,
                finalUrl = currentUrl,
                finalHost = finalInfo.host,
                finalIsHttps = finalInfo.isHttps,
                reachedMaxHops = true,
                blockedDangerousScheme = null,
                suspiciousReasons = finalInfo.suspiciousReasons + "Exceeded maximum redirect limit ($MAX_HOPS hops)",
                statusNote = "Stopped after $MAX_HOPS redirects."
            )
        } catch (e: BlockedDestinationException) {
            RedirectInspectionState.Failed(
                message = "This link points to a private or internal address and was blocked.",
                isOfflineLikely = false
            )
        } catch (e: UnknownHostException) {
            RedirectInspectionState.Failed(
                message = "Destination could not be checked. Internet connection may be unavailable.",
                isOfflineLikely = true
            )
        } catch (e: ConnectException) {
            RedirectInspectionState.Failed(
                message = "Destination could not be checked. Internet connection may be unavailable.",
                isOfflineLikely = true
            )
        } catch (e: SocketTimeoutException) {
            RedirectInspectionState.Failed(
                message = "Connection timed out while checking redirects.",
                isOfflineLikely = true
            )
        } catch (e: IOException) {
            RedirectInspectionState.Failed(
                message = "Link inspection failed. You can still view the original URL.",
                isOfflineLikely = false
            )
        } catch (_: Exception) {
            RedirectInspectionState.Failed(
                message = "Link inspection failed. You can still view the original URL.",
                isOfflineLikely = false
            )
        }
    }

    private class BlockedDestinationException(message: String) : IOException(message)

    /**
     * SSRF guard: resolves via the system DNS, then rejects any non-public address
     * (loopback, RFC1918, link-local, CGNAT, ULA, multicast, unspecified) before
     * OkHttp opens a socket. Validating at DNS time also defeats DNS-rebinding
     * where a public hostname resolves to an internal IP.
     */
    private class SsrfGuardDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            for (addr in addresses) {
                if (isBlockedAddress(addr)) {
                    throw BlockedDestinationException(
                        "Blocked non-public destination for host: $hostname"
                    )
                }
            }
            return addresses
        }
    }

    private data class HopResponse(
        val code: Int,
        val methodUsed: String,
        val isRedirect: Boolean,
        val locationHeader: String?
    )

    private fun executeHeaderCheck(url: String): HopResponse {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "WideQR-LinkInspector/1.0")
            .header("Connection", "close")
            .build()

        client.newCall(headReq).execute().use { resp ->
            val code = resp.code
            val location = resp.header("Location")
            val isRedirect = code in setOf(301, 302, 303, 307, 308)
            // Fall back to minimal-range GET if server rejects HEAD (405, 501, 403, 400)
            if (!isRedirect && code in setOf(400, 403, 405, 501)) {
                return executeMinimalGetFallback(url)
            }
            return HopResponse(code, "HEAD", isRedirect, location)
        }
    }

    private fun executeMinimalGetFallback(url: String): HopResponse {
        val getReq = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "WideQR-LinkInspector/1.0")
            .header("Range", "bytes=0-0")
            .header("Connection", "close")
            .build()

        client.newCall(getReq).execute().use { resp ->
            val code = resp.code
            val location = resp.header("Location")
            val isRedirect = code in setOf(301, 302, 303, 307, 308)
            return HopResponse(code, "GET", isRedirect, location)
        }
    }

    private fun resolveRelativeLocation(baseUrl: String, location: String): String? {
        return try {
            val trimmedLoc = location.trim()
            val scheme = LinkInspector.extractScheme(trimmedLoc)
            if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") {
                return trimmedLoc
            }
            URI(baseUrl).resolve(trimmedLoc).toString()
        } catch (_: Exception) {
            null
        }
    }
}

/** Returns true for any non-public address that must never be contacted by link inspection. */
private fun isBlockedAddress(addr: InetAddress): Boolean {
    if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
        addr.isMulticastAddress || addr.isAnyLocalAddress
    ) {
        return true
    }
    val bytes = addr.address
    return when (bytes.size) {
        4 -> isBlockedIpv4(bytes)
        16 -> isBlockedIpv6(bytes)
        else -> true
    }
}

private fun isBlockedIpv4(b: ByteArray): Boolean {
    val o1 = b[0].toInt() and 0xFF
    val o2 = b[1].toInt() and 0xFF
    return when {
        o1 == 0 -> true // 0.0.0.0/8 "this network"
        o1 == 10 -> true // 10.0.0.0/8 private
        o1 == 100 && o2 in 64..127 -> true // 100.64.0.0/10 CGNAT
        o1 == 127 -> true // loopback
        o1 == 169 && o2 == 254 -> true // link-local
        o1 == 172 && o2 in 16..31 -> true // 172.16.0.0/12 private
        o1 == 192 && o2 == 168 -> true // 192.168.0.0/16 private
        o1 >= 224 -> true // multicast + reserved
        else -> false
    }
}

private fun isBlockedIpv6(b: ByteArray): Boolean {
    // IPv4-mapped IPv6 (::ffff:a.b.c.d) — unwrap and apply IPv4 rules.
    val isMapped = b[0..9].all { it == 0.toByte() } &&
        b[10] == 0xFF.toByte() && b[11] == 0xFF.toByte()
    if (isMapped) return isBlockedIpv4(b.copyOfRange(12, 16))
    val first = b[0].toInt() and 0xFF
    return when {
        first == 0xFF -> true // multicast ff00::/8
        (first and 0xFE) == 0xFC -> true // unique local fc00::/7
        (first and 0xFF) == 0xFE && (b[1].toInt() and 0xC0) == 0x80 -> true // link-local fe80::/10
        b.all { it == 0.toByte() } -> true // :: unspecified
        else -> false
    }
}
