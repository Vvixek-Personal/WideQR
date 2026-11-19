package com.example.links

import com.example.models.LinkInfo
import java.net.URI
import java.util.Locale

object LinkInspector {

    private val DANGEROUS_SCHEMES = setOf("javascript", "file", "intent", "data", "content", "vbscript")

    private val KNOWN_SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "rebrand.ly", "cutt.ly", "rb.gy", "shorturl.at", "amzn.to", "lnkd.in", "youtu.be"
    )

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun isWebUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
    }

    fun isDangerousScheme(raw: String): Boolean {
        val scheme = extractScheme(raw)
        return scheme in DANGEROUS_SCHEMES
    }

    fun inspect(rawUrl: String): LinkInfo {
        val trimmed = rawUrl.trim()
        val scheme = extractScheme(trimmed)
        val isHttps = scheme == "https"
        val isHttp = scheme == "http"

        val parsed = try {
            URI(trimmed)
        } catch (_: Exception) {
            null
        }

        val host = parsed?.host?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
            ?: extractHostFallback(trimmed)
        val reasons = mutableListOf<String>()

        if (scheme in DANGEROUS_SCHEMES) {
            reasons.add("Blocked/dangerous URI scheme ($scheme:)")
        } else if (!isHttps && !isHttp) {
            reasons.add("Unusual URL scheme ($scheme:)")
        }

        if (isHttp) {
            reasons.add("Unencrypted HTTP connection (traffic is not encrypted)")
        }

        if (parsed?.rawUserInfo != null || trimmed.substringAfter("://", "").substringBefore("/").contains("@")) {
            reasons.add("URL contains '@' userinfo which can disguise the real domain")
        }

        if (host.startsWith("xn--") || host.contains(".xn--")) {
            reasons.add("Domain uses Punycode (IDN) characters that may mimic another domain")
        }

        if (IPV4_REGEX.matches(host) || host.startsWith("[")) {
            reasons.add("Destination uses a raw IP address instead of a domain name")
        }

        val port = parsed?.port ?: -1
        if (port != -1 && port != 80 && port != 443) {
            reasons.add("Uses non-standard port ($port)")
        }

        val isShortener = host in KNOWN_SHORTENERS
        if (isShortener) {
            reasons.add("Shortened URL hides its final destination until inspected")
        }

        if (host.isBlank()) {
            reasons.add("Could not verify a valid domain host")
        }

        return LinkInfo(
            originalUrl = trimmed,
            scheme = scheme,
            host = host.ifBlank { "(unknown host)" },
            isHttps = isHttps,
            isHttp = isHttp,
            isUrlShortener = isShortener,
            suspiciousReasons = reasons,
            isSuspicious = reasons.any {
                !it.startsWith("Shortened URL") || scheme in DANGEROUS_SCHEMES
            } || isHttp || scheme in DANGEROUS_SCHEMES
        )
    }

    fun extractScheme(url: String): String {
        val colonIdx = url.trim().indexOf(':')
        if (colonIdx <= 0) return ""
        return url.trim().substring(0, colonIdx).lowercase(Locale.US)
    }

    private fun extractHostFallback(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        return afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase(Locale.US)
    }
}
