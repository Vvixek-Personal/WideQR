package com.example.models

enum class QrContentType(val label: String) {
    UPI_PAYMENT("UPI Payment"),
    URL("Web Link"),
    WIFI("Wi-Fi Network"),
    PHONE("Phone Number"),
    EMAIL("Email Address"),
    CONTACT("Contact Card"),
    SMS("SMS Message"),
    PLAIN_TEXT("Text")
}

data class UpiData(
    val rawUri: String,
    val payeeAddress: String?,
    val payeeName: String?,
    val amount: String?,
    val formattedAmount: String?,
    val currency: String,
    val note: String?,
    val merchantCode: String?,
    val transactionRef: String?,
    val transactionId: String?,
    val isAmountSpecified: Boolean
)

data class WifiData(
    val ssid: String,
    val securityType: String,
    val password: String?,
    val isHidden: Boolean
)

data class ContactData(
    val name: String?,
    val organization: String?,
    val title: String?,
    val phones: List<String>,
    val emails: List<String>,
    val urls: List<String>,
    val address: String?,
    val rawPayload: String
)

data class EmailData(
    val address: String,
    val subject: String?,
    val body: String?
)

data class SmsData(
    val phoneNumber: String,
    val message: String?
)

data class LinkInfo(
    val originalUrl: String,
    val scheme: String,
    val host: String,
    val isHttps: Boolean,
    val isHttp: Boolean,
    val isUrlShortener: Boolean,
    val suspiciousReasons: List<String>,
    val isSuspicious: Boolean
)

data class RedirectHop(
    val url: String,
    val statusCode: Int,
    val method: String
)

sealed interface RedirectInspectionState {
    data object Idle : RedirectInspectionState
    data object Loading : RedirectInspectionState
    data class Completed(
        val hops: List<RedirectHop>,
        val finalUrl: String,
        val finalHost: String,
        val finalIsHttps: Boolean,
        val reachedMaxHops: Boolean,
        val blockedDangerousScheme: String?,
        val suspiciousReasons: List<String>,
        val statusNote: String
    ) : RedirectInspectionState

    data class Failed(
        val message: String,
        val isOfflineLikely: Boolean
    ) : RedirectInspectionState
}

sealed class ScanResult(
    val contentType: QrContentType
) {
    abstract val rawValue: String
    abstract val sourceLabel: String

    data class UpiResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val upiData: UpiData
    ) : ScanResult(QrContentType.UPI_PAYMENT)

    data class UrlResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val linkInfo: LinkInfo
    ) : ScanResult(QrContentType.URL)

    data class WifiResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val wifiData: WifiData
    ) : ScanResult(QrContentType.WIFI)

    data class PhoneResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val phoneNumber: String
    ) : ScanResult(QrContentType.PHONE)

    data class EmailResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val emailData: EmailData
    ) : ScanResult(QrContentType.EMAIL)

    data class ContactResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val contactData: ContactData
    ) : ScanResult(QrContentType.CONTACT)

    data class SmsResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val smsData: SmsData
    ) : ScanResult(QrContentType.SMS)

    data class PlainTextResult(
        override val rawValue: String,
        override val sourceLabel: String,
        val text: String
    ) : ScanResult(QrContentType.PLAIN_TEXT)
}
