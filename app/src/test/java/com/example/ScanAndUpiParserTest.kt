package com.example

import com.example.links.LinkInspector
import com.example.models.ScanResult
import com.example.scanner.ScanResultProcessor
import com.example.upi.UPIParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAndUpiParserTest {

    @Test
    fun `parses full UPI payment payload accurately`() {
        val raw = "upi://pay?pa=merchant@okaxis&pn=Fresh%20Cafe&am=1250.5&cu=INR&tn=Coffee%20Order&mc=5812&tr=TXN99&orgid=159002&sign=MEUCIQ=="
        val result = ScanResultProcessor.process(raw, "Ultra-wide camera")

        assertTrue(result is ScanResult.UpiResult)
        val upi = (result as ScanResult.UpiResult).upiData
        assertEquals(raw, upi.rawUri)
        assertEquals("merchant@okaxis", upi.payeeAddress)
        assertEquals("Fresh Cafe", upi.payeeName)
        assertEquals("1250.5", upi.amount)
        assertEquals("₹1,250.50", upi.formattedAmount)
        assertEquals("Coffee Order", upi.note)
        assertEquals("5812", upi.merchantCode)
        assertEquals("TXN99", upi.transactionRef)
        assertTrue(upi.isAmountSpecified)
    }

    @Test
    fun `parses UPI QR without amount cleanly`() {
        val upi = UPIParser.parse("upi://pay?pa=chai@ybl&pn=Tea+Stall")
        assertNotNull(upi)
        assertEquals("chai@ybl", upi?.payeeAddress)
        assertEquals("Tea Stall", upi?.payeeName)
        assertFalse(upi?.isAmountSpecified ?: true)
        assertNull(upi?.formattedAmount)
    }

    @Test
    fun `detects suspicious URL traits and dangerous schemes`() {
        val httpIp = LinkInspector.inspect("http://192.168.1.50:8080/login")
        assertTrue(httpIp.isHttp)
        assertFalse(httpIp.isHttps)
        assertTrue(httpIp.isSuspicious)
        assertTrue(httpIp.suspiciousReasons.size >= 3)

        val userinfoSpoof = LinkInspector.inspect("https://trusted.com@evil.example/pay")
        assertTrue(userinfoSpoof.isSuspicious)
        assertEquals("evil.example", userinfoSpoof.host)

        val punycode = LinkInspector.inspect("https://xn--pple-43d.com/auth")
        assertTrue(punycode.isSuspicious)

        assertTrue(LinkInspector.isDangerousScheme("javascript:alert(1)"))
        assertTrue(LinkInspector.isDangerousScheme("file:///etc/passwd"))
        assertTrue(LinkInspector.isDangerousScheme("intent://scan/#Intent;end"))
    }

    @Test
    fun `parses Wi-Fi, vCard, SMS, Email, and Phone QR payloads`() {
        val wifi = ScanResultProcessor.process("WIFI:T:WPA;S:Studio\\;Net;P:pass\\:123;H:true;;", "Test")
        assertTrue(wifi is ScanResult.WifiResult)
        assertEquals("Studio;Net", (wifi as ScanResult.WifiResult).wifiData.ssid)
        assertEquals("pass:123", wifi.wifiData.password)
        assertTrue(wifi.wifiData.isHidden)

        val contact = ScanResultProcessor.process(
            "BEGIN:VCARD\nVERSION:3.0\nFN:Asha Rao\nTEL:+919876543210\nEMAIL:asha@example.com\nEND:VCARD",
            "Test"
        )
        assertTrue(contact is ScanResult.ContactResult)
        assertEquals("Asha Rao", (contact as ScanResult.ContactResult).contactData.name)
        assertEquals(listOf("+919876543210"), contact.contactData.phones)

        val sms = ScanResultProcessor.process("SMSTO:+911234567890:Hello WideQR", "Test")
        assertTrue(sms is ScanResult.SmsResult)
        assertEquals("+911234567890", (sms as ScanResult.SmsResult).smsData.phoneNumber)
        assertEquals("Hello WideQR", sms.smsData.message)

        val email = ScanResultProcessor.process("mailto:help@example.org?subject=Test", "Test")
        assertTrue(email is ScanResult.EmailResult)
        assertEquals("help@example.org", (email as ScanResult.EmailResult).emailData.address)
        assertEquals("Test", email.emailData.subject)

        val phone = ScanResultProcessor.process("tel:+919811122233", "Test")
        assertTrue(phone is ScanResult.PhoneResult)
        assertEquals("+919811122233", (phone as ScanResult.PhoneResult).phoneNumber)
    }
}
