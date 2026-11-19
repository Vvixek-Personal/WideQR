package com.example.upi

import com.example.models.UpiData
import java.math.BigDecimal
import java.net.URLDecoder
import java.text.NumberFormat
import java.util.Locale

object UPIParser {

    private val UPI_PAY_PREFIX_REGEX = Regex("^upi://pay([/?#].*)?$", RegexOption.IGNORE_CASE)

    fun isUpiPayload(raw: String): Boolean {
        val trimmed = raw.trim()
        return UPI_PAY_PREFIX_REGEX.matches(trimmed)
    }

    fun parse(raw: String): UpiData? {
        val trimmed = raw.trim()
        if (!isUpiPayload(trimmed)) return null

        val queryStart = trimmed.indexOf('?')
        val params = if (queryStart >= 0 && queryStart < trimmed.length - 1) {
            parseQueryParams(trimmed.substring(queryStart + 1))
        } else {
            emptyMap()
        }

        val payeeAddress = params["pa"]?.trim()?.takeIf { it.isNotEmpty() }
        val payeeName = params["pn"]?.trim()?.takeIf { it.isNotEmpty() }
        val rawAmount = params["am"]?.trim()?.takeIf { it.isNotEmpty() }
        val currency = params["cu"]?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: "INR"
        val note = params["tn"]?.trim()?.takeIf { it.isNotEmpty() }
        val merchantCode = params["mc"]?.trim()?.takeIf { it.isNotEmpty() }
        val transactionRef = params["tr"]?.trim()?.takeIf { it.isNotEmpty() }
        val transactionId = params["tid"]?.trim()?.takeIf { it.isNotEmpty() }

        val formattedAmount = rawAmount?.let { formatAmount(it, currency) }

        return UpiData(
            rawUri = trimmed,
            payeeAddress = payeeAddress,
            payeeName = payeeName,
            amount = rawAmount,
            formattedAmount = formattedAmount,
            currency = currency,
            note = note,
            merchantCode = merchantCode,
            transactionRef = transactionRef,
            transactionId = transactionId,
            isAmountSpecified = rawAmount != null
        )
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eqIndex = pair.indexOf('=')
            val rawKey = if (eqIndex >= 0) pair.substring(0, eqIndex) else pair
            val rawValue = if (eqIndex >= 0 && eqIndex < pair.length - 1) pair.substring(eqIndex + 1) else ""
            val key = decodePercent(rawKey).lowercase(Locale.US)
            val value = decodePercent(rawValue)
            if (key.isNotEmpty() && !result.containsKey(key)) {
                result[key] = value
            }
        }
        return result
    }

    private fun decodePercent(input: String): String {
        return try {
            URLDecoder.decode(input, "UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    private fun formatAmount(rawAmount: String, currency: String): String {
        val numeric = rawAmount.replace(",", "").toBigDecimalOrNull() ?: return "$currency $rawAmount"
        return if (currency == "INR") {
            val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            "₹${formatter.format(numeric)}"
        } else {
            val scaled = numeric.setScale(2, BigDecimal.ROUND_HALF_UP)
            "$currency $scaled"
        }
    }
}
