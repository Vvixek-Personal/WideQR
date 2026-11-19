package com.example.scanner

import com.example.links.LinkInspector
import com.example.models.ContactData
import com.example.models.EmailData
import com.example.models.ScanResult
import com.example.models.SmsData
import com.example.models.WifiData
import com.example.upi.UPIParser
import java.net.URLDecoder
import java.util.Locale

object ScanResultProcessor {

    private val PHONE_ONLY_REGEX = Regex("^\\+?[0-9][0-9\\s\\-().]{5,18}$")
    private val EMAIL_ONLY_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun process(rawValue: String, sourceLabel: String): ScanResult {
        val trimmed = rawValue.trim()

        // 1. UPI Payment URI
        UPIParser.parse(trimmed)?.let { upiData ->
            return ScanResult.UpiResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                upiData = upiData
            )
        }

        // 2. Wi-Fi QR (WIFI:T:WPA;S:MyWifi;P:secret;H:false;;)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            parseWifi(trimmed)?.let { wifi ->
                return ScanResult.WifiResult(
                    rawValue = trimmed,
                    sourceLabel = sourceLabel,
                    wifiData = wifi
                )
            }
        }

        // 3. Contact Card (vCard or MeCard)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            return ScanResult.ContactResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                contactData = parseVCard(trimmed)
            )
        }
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            return ScanResult.ContactResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                contactData = parseMeCard(trimmed)
            )
        }

        // 4. SMS (SMSTO:number:msg or sms:number?body=...)
        if (trimmed.startsWith("SMSTO:", ignoreCase = true) ||
            trimmed.startsWith("sms:", ignoreCase = true)
        ) {
            parseSms(trimmed)?.let { sms ->
                return ScanResult.SmsResult(
                    rawValue = trimmed,
                    sourceLabel = sourceLabel,
                    smsData = sms
                )
            }
        }

        // 5. Email (mailto:... or MATMSG:...)
        if (trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("MATMSG:", ignoreCase = true)
        ) {
            parseEmail(trimmed)?.let { email ->
                return ScanResult.EmailResult(
                    rawValue = trimmed,
                    sourceLabel = sourceLabel,
                    emailData = email
                )
            }
        }

        // 6. Phone (tel:...)
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val number = trimmed.substring(4).trim()
            if (number.isNotEmpty()) {
                return ScanResult.PhoneResult(
                    rawValue = trimmed,
                    sourceLabel = sourceLabel,
                    phoneNumber = number
                )
            }
        }

        // 7. Web URL or Dangerous Scheme URL
        if (LinkInspector.isWebUrl(trimmed) || LinkInspector.isDangerousScheme(trimmed)) {
            return ScanResult.UrlResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                linkInfo = LinkInspector.inspect(trimmed)
            )
        }

        // 8. Bare email or phone
        if (EMAIL_ONLY_REGEX.matches(trimmed)) {
            return ScanResult.EmailResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                emailData = EmailData(address = trimmed, subject = null, body = null)
            )
        }
        if (PHONE_ONLY_REGEX.matches(trimmed)) {
            return ScanResult.PhoneResult(
                rawValue = trimmed,
                sourceLabel = sourceLabel,
                phoneNumber = trimmed
            )
        }

        // 9. Plain text fallback
        return ScanResult.PlainTextResult(
            rawValue = trimmed,
            sourceLabel = sourceLabel,
            text = trimmed
        )
    }

    private fun parseWifi(raw: String): WifiData? {
        val body = raw.substring(5)
        val fields = splitEscapedFields(body, ';')
        var ssid = ""
        var type = "Open"
        var password: String? = null
        var hidden = false

        for (field in fields) {
            val colonIdx = field.indexOf(':')
            if (colonIdx <= 0) continue
            val key = field.substring(0, colonIdx).uppercase(Locale.US)
            val valUnescaped = unescapeWifiValue(field.substring(colonIdx + 1))
            when (key) {
                "S" -> ssid = valUnescaped
                "T" -> type = when (valUnescaped.uppercase(Locale.US)) {
                    "WPA", "WPA2", "WPA/WPA2" -> "WPA/WPA2"
                    "WPA3", "SAE" -> "WPA3"
                    "WEP" -> "WEP"
                    "NOPASS", "" -> "Open"
                    else -> valUnescaped
                }
                "P" -> password = valUnescaped.takeIf { it.isNotEmpty() }
                "H" -> hidden = valUnescaped.equals("true", ignoreCase = true)
            }
        }
        if (ssid.isEmpty() && password == null) return null
        return WifiData(
            ssid = ssid.ifEmpty { "(Hidden / Unnamed Network)" },
            securityType = type,
            password = password,
            isHidden = hidden
        )
    }

    private fun parseVCard(raw: String): ContactData {
        var name: String? = null
        var org: String? = null
        var title: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        var address: String? = null

        val lines = raw.replace("\r\n", "\n").replace("\r", "\n").lines()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue
            val keyPart = line.substring(0, colonIdx).uppercase(Locale.US)
            val baseKey = keyPart.substringBefore(';')
            val value = line.substring(colonIdx + 1).trim()
            if (value.isEmpty()) continue

            when (baseKey) {
                "FN" -> name = value
                "N" -> if (name == null) {
                    val parts = value.split(';').filter { it.isNotBlank() }
                    name = parts.reversed().joinToString(" ").ifBlank { value }
                }
                "ORG" -> org = value.replace(";", ", ").trim(',', ' ')
                "TITLE" -> title = value
                "TEL" -> phones.add(value)
                "EMAIL" -> emails.add(value)
                "URL" -> urls.add(value)
                "ADR" -> address = value.split(';').filter { it.isNotBlank() }.joinToString(", ")
            }
        }
        return ContactData(
            name = name,
            organization = org,
            title = title,
            phones = phones.distinct(),
            emails = emails.distinct(),
            urls = urls.distinct(),
            address = address,
            rawPayload = raw
        )
    }

    private fun parseMeCard(raw: String): ContactData {
        val body = raw.substring(7)
        val fields = splitEscapedFields(body, ';')
        var name: String? = null
        var org: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        var address: String? = null

        for (field in fields) {
            val colonIdx = field.indexOf(':')
            if (colonIdx <= 0) continue
            val key = field.substring(0, colonIdx).uppercase(Locale.US)
            val value = unescapeWifiValue(field.substring(colonIdx + 1)).trim()
            if (value.isEmpty()) continue
            when (key) {
                "N" -> name = value.split(',').reversed().joinToString(" ").trim()
                "ORG" -> org = value
                "TEL" -> phones.add(value)
                "EMAIL" -> emails.add(value)
                "URL" -> urls.add(value)
                "ADR" -> address = value
            }
        }
        return ContactData(
            name = name,
            organization = org,
            title = null,
            phones = phones.distinct(),
            emails = emails.distinct(),
            urls = urls.distinct(),
            address = address,
            rawPayload = raw
        )
    }

    private fun parseSms(raw: String): SmsData? {
        return if (raw.startsWith("SMSTO:", ignoreCase = true)) {
            val rest = raw.substring(6)
            val colonIdx = rest.indexOf(':')
            if (colonIdx >= 0) {
                SmsData(
                    phoneNumber = rest.substring(0, colonIdx).trim(),
                    message = rest.substring(colonIdx + 1).takeIf { it.isNotEmpty() }
                )
            } else {
                SmsData(phoneNumber = rest.trim(), message = null)
            }
        } else {
            val rest = raw.substring(4)
            val qIdx = rest.indexOf('?')
            val number = if (qIdx >= 0) rest.substring(0, qIdx) else rest
            val body = if (qIdx >= 0) {
                rest.substring(qIdx + 1)
                    .split('&')
                    .firstOrNull { it.startsWith("body=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.let { decodeUrlSafe(it) }
            } else {
                null
            }
            SmsData(phoneNumber = number.trim(), message = body?.takeIf { it.isNotEmpty() })
        }
    }

    private fun parseEmail(raw: String): EmailData? {
        if (raw.startsWith("MATMSG:", ignoreCase = true)) {
            val fields = splitEscapedFields(raw.substring(7), ';')
            var to = ""
            var sub: String? = null
            var body: String? = null
            for (field in fields) {
                val colonIdx = field.indexOf(':')
                if (colonIdx <= 0) continue
                val key = field.substring(0, colonIdx).uppercase(Locale.US)
                val value = unescapeWifiValue(field.substring(colonIdx + 1))
                when (key) {
                    "TO" -> to = value.trim()
                    "SUB" -> sub = value.takeIf { it.isNotEmpty() }
                    "BODY" -> body = value.takeIf { it.isNotEmpty() }
                }
            }
            return if (to.isNotEmpty()) EmailData(to, sub, body) else null
        }

        val rest = raw.substring(7)
        val qIdx = rest.indexOf('?')
        val address = (if (qIdx >= 0) rest.substring(0, qIdx) else rest).trim()
        if (address.isEmpty()) return null
        var subject: String? = null
        var body: String? = null
        if (qIdx >= 0) {
            for (param in rest.substring(qIdx + 1).split('&')) {
                val eqIdx = param.indexOf('=')
                if (eqIdx <= 0) continue
                val k = param.substring(0, eqIdx).lowercase(Locale.US)
                val v = decodeUrlSafe(param.substring(eqIdx + 1))
                when (k) {
                    "subject" -> subject = v.takeIf { it.isNotEmpty() }
                    "body" -> body = v.takeIf { it.isNotEmpty() }
                }
            }
        }
        return EmailData(address, subject, body)
    }

    private fun splitEscapedFields(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                current.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun unescapeWifiValue(input: String): String {
        val sb = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                sb.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun decodeUrlSafe(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
