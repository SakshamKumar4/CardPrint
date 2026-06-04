package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
fun CustomCropView(
    bitmap: Bitmap,
    corners: List<PointF>, // Must have exactly 4 PointFs
    onCornersUpdated: (List<PointF>) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        if (containerWidth <= 0f || containerHeight <= 0f) return@BoxWithConstraints

        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()

        val bitmapRatio = bitmapW / bitmapH
        val containerRatio = containerWidth / containerHeight

        // Center-Inside geometry calculations
        val scale: Float
        val offsetX: Float
        val offsetY: Float
        val drawW: Float
        val drawH: Float

        if (bitmapRatio > containerRatio) {
            scale = containerWidth / bitmapW
            drawW = containerWidth
            drawH = bitmapH * scale
            offsetX = 0f
            offsetY = (containerHeight - drawH) / 2f
        } else {
            scale = containerHeight / bitmapH
            drawW = bitmapW * scale
            drawH = containerHeight
            offsetX = (containerWidth - drawW) / 2f
            offsetY = 0f
        }

        // Keep local state of screen corners mapped from bitmap space
        val currentCorners = remember(corners, scale, offsetX, offsetY) {
            corners.map { pt ->
                PointF(pt.x * scale + offsetX, pt.y * scale + offsetY)
            }.toMutableStateList()
        }

        // Map screen offset back to original bitmap coordinates
        fun updateBitmapCorners() {
            val updated = currentCorners.map { pt ->
                val bx = ((pt.x - offsetX) / scale).coerceIn(0f, bitmapW)
                val by = ((pt.y - offsetY) / scale).coerceIn(0f, bitmapH)
                PointF(bx, by)
            }
            onCornersUpdated(updated)
        }

        var activeHandleIndex by remember { mutableStateOf(-1) }
        val touchRadius = 45f // Interactive drag threshold size in points

        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, scale, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { point ->
                            // Determine which corner handle is closest to the click point
                            var closestIndex = -1
                            var minDistance = Float.MAX_VALUE
                            for (i in 0..3) {
                                val handle = currentCorners[i]
                                val dist = hypot((point.x - handle.x).toDouble(), (point.y - handle.y).toDouble()).toFloat()
                                if (dist < minDistance && dist < touchRadius * 1.5f) {
                                    minDistance = dist
                                    closestIndex = i
                                }
                            }
                            activeHandleIndex = closestIndex
                        },
                        onDrag = { change, dragAmount ->
                            if (activeHandleIndex in 0..3) {
                                change.consume()
                                val pt = currentCorners[activeHandleIndex]
                                val newX = (pt.x + dragAmount.x).coerceIn(offsetX, offsetX + drawW)
                                val newY = (pt.y + dragAmount.y).coerceIn(offsetY, offsetY + drawH)
                                currentCorners[activeHandleIndex] = PointF(newX, newY)
                                updateBitmapCorners()
                            }
                        },
                        onDragEnd = {
                            activeHandleIndex = -1
                        },
                        onDragCancel = {
                            activeHandleIndex = -1
                        }
                    )
                }
        ) {
            // 1. Draw source image scaled to Center-Inside
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt())
            )

            if (currentCorners.size == 4) {
                val tl = Offset(currentCorners[0].x, currentCorners[0].y)
                val tr = Offset(currentCorners[1].x, currentCorners[1].y)
                val br = Offset(currentCorners[2].x, currentCorners[2].y)
                val bl = Offset(currentCorners[3].x, currentCorners[3].y)

                // 2. Draw connecting polygon edges representing scanner highlight grid bounds
                val strokeColor = primaryColor
                drawPath(
                    path = Path().apply {
                        moveTo(tl.x, tl.y)
                        lineTo(tr.x, tr.y)
                        lineTo(br.x, br.y)
                        lineTo(bl.x, bl.y)
                        close()
                    },
                    color = strokeColor,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw standard inside-grid guides (1/3rd lines) for aligning identity cards comfortably
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(tl.x + (tr.x - tl.x) / 3f, tl.y + (tr.y - tl.y) / 3f),
                    end = Offset(bl.x + (br.x - bl.x) / 3f, bl.y + (br.y - bl.y) / 3f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(tl.x + 2 * (tr.x - tl.x) / 3f, tl.y + 2 * (tr.y - tl.y) / 3f),
                    end = Offset(bl.x + 2 * (br.x - bl.x) / 3f, bl.y + 2 * (br.y - bl.y) / 3f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(tl.x + (bl.x - tl.x) / 3f, tl.y + (bl.y - tl.y) / 3f),
                    end = Offset(tr.x + (br.x - tr.x) / 3f, tr.y + (br.y - tr.y) / 3f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(tl.x + 2 * (bl.x - tl.x) / 3f, tl.y + 2 * (bl.y - tl.y) / 3f),
                    end = Offset(tr.x + 2 * (br.x - tr.x) / 3f, tr.y + 2 * (br.y - tr.y) / 3f),
                    strokeWidth = 1.dp.toPx()
                )

                // 3. Draw active highlights and interactive handle circles
                for (i in 0..3) {
                    val pt = currentCorners[i]
                    val isDragging = i == activeHandleIndex
                    val radius = if (isDragging) 16.dp.toPx() else 12.dp.toPx()
                    val coreColor = if (isDragging) secondaryColor else strokeColor

                    // External outline glow
                    drawCircle(
                        color = Color.White,
                        radius = radius + 2.dp.toPx(),
                        center = Offset(pt.x, pt.y)
                    )

                    // Foreground knob
                    drawCircle(
                        color = coreColor,
                        radius = radius,
                        center = Offset(pt.x, pt.y)
                    )

                    // Inner core visual dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(pt.x, pt.y)
                    )
                }
            }
        }
    }
}
