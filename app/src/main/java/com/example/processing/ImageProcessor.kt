package com.example.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

object ImageProcessor {

    // Rotate bitmap by degrees
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Perspective Warp: Crop 4 corners of card and transform to perfect flat rectangle
    fun perspectiveWarp(
        bitmap: Bitmap,
        tl: PointF, // Top-Left
        tr: PointF, // Top-Right
        br: PointF, // Bottom-Right
        bl: PointF  // Bottom-Left
    ): Bitmap {
        // Calculate dynamic dimensions of card to preserve native aspect ratio
        val targetWidth = Math.max(
            Math.hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()),
            Math.hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        ).toFloat().coerceAtLeast(300f)

        val targetHeight = Math.max(
            Math.hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()),
            Math.hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        ).toFloat().coerceAtLeast(180f)

        // Standardize output size (usually ~1000px width for pristine print rendering)
        val w = 1000f
        val h = (w * (targetHeight / targetWidth)).coerceAtLeast(100f).coerceAtMost(2000f)

        val destBitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBitmap)
        val matrix = Matrix()

        val srcPts = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            w, 0f,
            w, h,
            0f, h
        )

        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(bitmap, matrix, paint)
        return destBitmap
    }

    // Auto Crop boundary detection
    fun detectCardCorners(bitmap: Bitmap): List<PointF> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Defaults occupying center 80%
        var left = w * 0.1f
        var top = h * 0.15f
        var right = w * 0.9f
        var bottom = h * 0.85f

        // Small proactive pixel gradient scan to shrink box to real card boundaries if contrast exists
        try {
            val sampleSize = 100
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
            val pixels = IntArray(sampleSize * sampleSize)
            scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)

            // Calculate luminance
            val lums = DoubleArray(sampleSize * sampleSize)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                lums[i] = 0.299 * r + 0.587 * g + 0.114 * b
            }

            // Find horizontal and vertical edges (simple Sobel gradient-like)
            val dx = DoubleArray(sampleSize * sampleSize)
            val dy = DoubleArray(sampleSize * sampleSize)
            var maxG = 0.0

            for (y in 1 until sampleSize - 1) {
                for (x in 1 until sampleSize - 1) {
                    val idx = y * sampleSize + x
                    val xGrad = lums[idx + 1] - lums[idx - 1]
                    val yGrad = lums[idx + sampleSize] - lums[idx - sampleSize]
                    dx[idx] = xGrad
                    dy[idx] = yGrad
                    val mag = Math.hypot(xGrad, yGrad)
                    if (mag > maxG) maxG = mag
                }
            }

            // Find bounding limits of high gradient values
            val threshold = maxG * 0.3
            var minX = sampleSize
            var maxX = 0
            var minY = sampleSize
            var maxY = 0

            for (y in 5 until sampleSize - 5) {
                for (x in 5 until sampleSize - 5) {
                    val idx = y * sampleSize + x
                    val mag = Math.hypot(dx[idx], dy[idx])
                    if (mag > threshold) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            // If a valid bounding box is found, use it!
            if (maxX > minX && maxY > minY && (maxX - minX) > sampleSize * 0.3) {
                left = (minX.toFloat() / sampleSize) * w
                right = (maxX.toFloat() / sampleSize) * w
                top = (minY.toFloat() / sampleSize) * h
                bottom = (maxY.toFloat() / sampleSize) * h
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return listOf(
            PointF(left, top),
            PointF(right, top),
            PointF(right, bottom),
            PointF(left, bottom)
        )
    }

    // Apply Filters & Slide Adjustments under perfect GPU speed
    fun applyFilterAndAdjustments(
        src: Bitmap,
        filterType: String,
        brightness: Float, // -100 to 100
        contrast: Float,   // 0.5 to 2.5
        saturation: Float, // 0.0 to 2.0
        sharpness: Float   // 0 to 100
    ): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val cm = ColorMatrix()

        // Apply filters
        when (filterType.uppercase()) {
            "GRAYSCALE" -> {
                cm.setSaturation(0f)
            }
            "BLACK_AND_WHITE" -> {
                cm.setSaturation(0f)
                val scale = 2.5f
                val translate = -90f
                val matrix = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(matrix))
            }
            "SCANNER" -> {
                cm.setSaturation(0f)
                val scale = 4.5f
                val translate = -260f
                val matrix = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(matrix))
            }
            "HIGH_CONTRAST" -> {
                val scale = 1.8f
                val translate = -50f
                val matrix = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(matrix))
            }
            "BRIGHTNESS_ENHANCE" -> {
                val translate = 40f
                val matrix = floatArrayOf(
                    1f, 0f, 0f, 0f, translate,
                    0f, 1f, 0f, 0f, translate,
                    0f, 0f, 1f, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(matrix))
            }
            "VINTAGE" -> {
                val matrix = floatArrayOf(
                    0.95f, 0f, 0f, 0f, 12f,
                    0f, 0.90f, 0f, 0f, 6f,
                    0f, 0f, 0.80f, 0f, -12f,
                    0f, 0.4f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(matrix))
            }
        }

        // Apply sliders
        if (saturation != 1f && filterType.uppercase() != "GRAYSCALE" && filterType.uppercase() != "SCANNER" && filterType.uppercase() != "BLACK_AND_WHITE") {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(saturation)
            cm.postConcat(satMatrix)
        }

        if (brightness != 0f || contrast != 1f) {
            val bTranslate = brightness
            val cScale = contrast
            val adjMatrix = floatArrayOf(
                cScale, 0f, 0f, 0f, bTranslate,
                0f, cScale, 0f, 0f, bTranslate,
                0f, 0f, cScale, 0f, bTranslate,
                0f, 0f, 0f, 1f, 0f
            )
            cm.postConcat(ColorMatrix(adjMatrix))
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Apply Sharpen (Lightweight 1D Convolution)
        if (sharpness > 0) {
            return applySharpness(dest, sharpness)
        }

        return dest
    }

    private fun applySharpness(src: Bitmap, level: Float): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val factor = (level / 100f) * 0.25f // Scale maximum strength

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x

                val c = pixels[idx]
                val rC = (c shr 16) and 0xFF
                val gC = (c shr 8) and 0xFF
                val bC = c and 0xFF

                val n1 = pixels[idx - 1]
                val n2 = pixels[idx + 1]
                val n3 = pixels[idx - width]
                val n4 = pixels[idx + width]

                val rN = (((n1 shr 16) and 0xFF) + ((n2 shr 16) and 0xFF) + ((n3 shr 16) and 0xFF) + ((n4 shr 16) and 0xFF)) shr 2
                val gN = (((n1 shr 8) and 0xFF) + ((n2 shr 8) and 0xFF) + ((n3 shr 8) and 0xFF) + ((n4 shr 8) and 0xFF)) shr 2
                val bN = ((n1 and 0xFF) + (n2 and 0xFF) + (n3 and 0xFF) + (n4 and 0xFF)) shr 2

                var r = rC + (rC - rN) * factor
                var g = gC + (gC - gN) * factor
                var b = bC + (bC - bN) * factor

                r = r.coerceIn(0f, 255f)
                g = g.coerceIn(0f, 255f)
                b = b.coerceIn(0f, 255f)

                outPixels[idx] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }

        // Fill borders
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        return out
    }
}
