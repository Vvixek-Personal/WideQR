package com.example.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.net.Uri
import android.os.SystemClock
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class QRDecoder {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    @Volatile
    private var scannerInstance: BarcodeScanner? = null

    private fun getOrCreateScanner(): BarcodeScanner? {
        scannerInstance?.let { return it }
        return synchronized(this) {
            scannerInstance ?: try {
                BarcodeScanning.getClient(options).also { scannerInstance = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    private val isProcessingFrame = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    @Volatile
    private var lastAnalysisTimestampMs = 0L

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun analyzeCameraImage(
        image: Image,
        rotationDegrees: Int,
        onDone: () -> Unit,
        onQrDetected: (String) -> Unit
    ) {
        val now = SystemClock.elapsedRealtime()
        if (isPaused.get() ||
            (now - lastAnalysisTimestampMs) < MIN_FRAME_INTERVAL_MS ||
            !isProcessingFrame.compareAndSet(false, true)
        ) {
            onDone()
            return
        }

        val activeScanner = getOrCreateScanner()
        if (activeScanner == null) {
            isProcessingFrame.set(false)
            onDone()
            return
        }

        lastAnalysisTimestampMs = now
        val inputImage = try {
            InputImage.fromMediaImage(image, rotationDegrees)
        } catch (_: Exception) {
            isProcessingFrame.set(false)
            onDone()
            return
        }

        activeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { s -> s.isNotBlank() } }
                if (raw != null && !isPaused.get()) {
                    isPaused.set(true)
                    onQrDetected(raw)
                }
            }
            .addOnCompleteListener {
                isProcessingFrame.set(false)
                onDone()
            }
    }

    suspend fun decodeFromUri(context: Context, uri: Uri): Result<String?> {
        val activeScanner = getOrCreateScanner()
            ?: return Result.failure(IllegalStateException("QR decoder unavailable"))
        return try {
            val bitmap = decodeDownsampledBitmap(context, uri, MAX_GALLERY_DIMENSION_PX)
            val inputImage = if (bitmap != null) {
                InputImage.fromBitmap(bitmap, 0)
            } else {
                InputImage.fromFilePath(context, uri)
            }

            val value = suspendCancellableCoroutine<String?> { cont ->
                activeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstNotNullOfOrNull {
                            it.rawValue?.takeIf { s -> s.isNotBlank() }
                        }
                        if (cont.isActive) cont.resume(raw)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
            bitmap?.recycle()
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeDownsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / sampleSize > maxDimension || origH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    fun close() {
        try {
            scannerInstance?.close()
            scannerInstance = null
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MIN_FRAME_INTERVAL_MS = 90L
        private const val MAX_GALLERY_DIMENSION_PX = 1600
    }
}
