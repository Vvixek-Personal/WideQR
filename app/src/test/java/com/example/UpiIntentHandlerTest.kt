package com.example

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.example.upi.UPIIntentHandler
import com.example.upi.UpiLaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpiIntentHandlerTest {

    @Test
    fun `preserves all original UPI query parameters verbatim and validates upi pay`() {
        val original = "upi://pay?pa=merchant@okaxis&pn=Fresh%20Cafe&am=50.00&cu=INR&tn=Food%20Order&mc=5812&tr=REF123&orgid=159002&sign=MEUCIQ=="
        val parsed = UPIIntentHandler.buildNormalizedPayUri(original)
        assertNotNull(parsed)
        assertEquals(original, parsed.toString())

        val uppercasePrefix = "UPI://PAY?pa=merchant@upi&am=10"
        val normalized = UPIIntentHandler.buildNormalizedPayUri(uppercasePrefix)
        assertEquals("upi://pay?pa=merchant@upi&am=10", normalized.toString())

        assertNull(UPIIntentHandler.buildNormalizedPayUri("https://example.com/pay"))
        assertNull(UPIIntentHandler.buildNormalizedPayUri("upi://mandate?pa=merchant@upi"))
    }

    @Test
    fun `returns no compatible UPI app message when Android cannot resolve any activity`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        shadowOf(activity.application).checkActivities(true)
        val uri = "upi://pay?pa=merchant@upi&pn=Merchant&am=50&cu=INR&tn=Food"

        val outcome = UPIIntentHandler.openInUpiApp(activity, uri)

        assertTrue(outcome is UpiLaunchOutcome.Failed)
        assertEquals(
            "No compatible UPI payment app found.",
            (outcome as UpiLaunchOutcome.Failed).message
        )
    }

    @Test
    fun `launches directly with preserved UPI URI when one app is installed`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val shadowPm = shadowOf(activity.packageManager)

        val gpayActivity = android.content.ComponentName("com.google.android.apps.nbu.paisa.user", "UpiActivity")
        shadowPm.addActivityIfNotPresent(gpayActivity)
        shadowPm.addIntentFilterForActivity(
            gpayActivity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("upi")
                addDataAuthority("pay", null)
            }
        )

        val uri = "upi://pay?pa=merchant@upi&pn=Merchant&am=50&cu=INR&tn=Food&orgid=123"
        val outcome = UPIIntentHandler.openInUpiApp(activity, uri)

        assertEquals(UpiLaunchOutcome.Launched, outcome)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse(uri), started.data)
    }

    @Test
    fun `opens Android app chooser when multiple UPI apps are installed`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val shadowPm = shadowOf(activity.packageManager)

        listOf(
            android.content.ComponentName("com.google.android.apps.nbu.paisa.user", "UpiActivity"),
            android.content.ComponentName("com.phonepe.app", "UpiActivity")
        ).forEach { component ->
            shadowPm.addActivityIfNotPresent(component)
            shadowPm.addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("upi")
                    addDataAuthority("pay", null)
                }
            )
        }

        val uri = "upi://pay?pa=merchant@upi&pn=Merchant&am=50&cu=INR&tn=Food"
        val outcome = UPIIntentHandler.openInUpiApp(activity, uri)

        assertEquals(UpiLaunchOutcome.Launched, outcome)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)

        @Suppress("DEPRECATION")
        val targetIntent = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_VIEW, targetIntent?.action)
        assertEquals(Uri.parse(uri), targetIntent?.data)
    }
}
