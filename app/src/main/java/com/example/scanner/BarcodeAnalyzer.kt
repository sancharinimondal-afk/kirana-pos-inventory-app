package com.example.scanner

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

class BarcodeAnalyzer(
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX
        )
        hints[DecodeHintType.TRY_HARDER] = true
        setHints(hints)
    }

    private var isPaused = false
    private var lastScannedCode = ""
    private var lastScannedTime = 0L

    fun pauseScanning() {
        isPaused = true
    }

    fun resumeScanning() {
        isPaused = false
        lastScannedCode = ""
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null && mediaImage.planes.isNotEmpty()) {
            val yPlane = mediaImage.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            try {
                // Extract clean luminance data without rowStride padding
                val cleanLuminance = ByteArray(width * height)
                yBuffer.rewind()
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    yBuffer.get(cleanLuminance, row * width, width)
                }

                // Rotate luminance data based on sensor orientation (phones in portrait are usually 90 or 270)
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val rotatedData: ByteArray
                val rotatedWidth: Int
                val rotatedHeight: Int

                when (rotationDegrees) {
                    90 -> {
                        rotatedData = rotateYuv90(cleanLuminance, width, height)
                        rotatedWidth = height
                        rotatedHeight = width
                    }
                    180 -> {
                        rotatedData = rotateYuv180(cleanLuminance, width, height)
                        rotatedWidth = width
                        rotatedHeight = height
                    }
                    270 -> {
                        rotatedData = rotateYuv270(cleanLuminance, width, height)
                        rotatedWidth = height
                        rotatedHeight = width
                    }
                    else -> {
                        rotatedData = cleanLuminance
                        rotatedWidth = width
                        rotatedHeight = height
                    }
                }

                val source = PlanarYUVLuminanceSource(
                    rotatedData,
                    rotatedWidth,
                    rotatedHeight,
                    0,
                    0,
                    rotatedWidth,
                    rotatedHeight,
                    false
                )

                // Try HybridBinarizer first, then fallback to GlobalHistogramBinarizer
                var decodedCode: String? = null
                try {
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    val result = reader.decodeWithState(binaryBitmap)
                    decodedCode = result.text?.trim()
                } catch (_: NotFoundException) {
                    try {
                        val globalBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                        val result = reader.decodeWithState(globalBitmap)
                        decodedCode = result.text?.trim()
                    } catch (_: Exception) {}
                } catch (_: Exception) {}

                val now = System.currentTimeMillis()
                if (!decodedCode.isNullOrEmpty() && (decodedCode != lastScannedCode || now - lastScannedTime > 2000)) {
                    lastScannedCode = decodedCode
                    lastScannedTime = now
                    onBarcodeScanned(decodedCode)
                }
            } catch (_: Exception) {
                // Ignore frame decode exceptions
            } finally {
                reader.reset()
            }
        }
        imageProxy.close()
    }

    companion object {
        fun rotateYuv90(data: ByteArray, width: Int, height: Int): ByteArray {
            val rotated = ByteArray(width * height)
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rotated[i] = data[y * width + x]
                    i++
                }
            }
            return rotated
        }

        fun rotateYuv180(data: ByteArray, width: Int, height: Int): ByteArray {
            val rotated = ByteArray(width * height)
            val size = width * height
            for (i in 0 until size) {
                rotated[i] = data[size - 1 - i]
            }
            return rotated
        }

        fun rotateYuv270(data: ByteArray, width: Int, height: Int): ByteArray {
            val rotated = ByteArray(width * height)
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rotated[i] = data[y * width + x]
                    i++
                }
            }
            return rotated
        }

        fun decodeBitmap(bitmap: Bitmap): String? {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val reader = MultiFormatReader().apply {
                val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
                hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX
                )
                hints[DecodeHintType.TRY_HARDER] = true
                setHints(hints)
            }
            return try {
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                reader.decodeWithState(binaryBitmap).text?.trim()
            } catch (_: Exception) {
                try {
                    val globalBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                    reader.decodeWithState(globalBitmap).text?.trim()
                } catch (_: Exception) {
                    null
                }
            } finally {
                reader.reset()
            }
        }
    }
}

