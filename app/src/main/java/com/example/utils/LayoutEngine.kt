package com.example.utils

import android.graphics.RectF

data class LayoutItem(
    val imageIndex: Int, // Index of edited image 
    val rect: RectF      // Position coordinates in postscript points (0..595, 0..842)
)

object LayoutEngine {
    fun getLayoutItems(
        layoutType: Int,
        imageCount: Int,
        customCols: Int = 2,
        customRows: Int = 3,
        passportCopies: Int = 12
    ): List<LayoutItem> {
        val items = mutableListOf<LayoutItem>()
        val pageW = 595f
        val pageH = 842f
        if (imageCount == 0) return items

        when (layoutType) {
            1 -> {
                // Layout 1: Single card centered (larger size for standard center placement)
                val cardW = 320f
                val cardH = 202f 
                val x = (pageW - cardW) / 2f
                val y = (pageH - cardH) / 2f
                items.add(LayoutItem(0, RectF(x, y, x + cardW, y + cardH)))
            }
            2 -> {
                // Layout 2: Two cards side by side (standard ID size)
                val cardW = 243f
                val cardH = 153f
                val spacing = 20f
                val totalW = cardW * 2 + spacing
                val startX = (pageW - totalW) / 2f
                val y = (pageH - cardH) / 2f

                items.add(LayoutItem(0, RectF(startX, y, startX + cardW, y + cardH)))
                if (imageCount > 1) {
                    val x2 = startX + cardW + spacing
                    items.add(LayoutItem(1, RectF(x2, y, x2 + cardW, y + cardH)))
                } else {
                    val x2 = startX + cardW + spacing
                    items.add(LayoutItem(0, RectF(x2, y, x2 + cardW, y + cardH)))
                }
            }
            3 -> {
                // Layout 3: One card top, one card bottom stacked
                val cardW = 243f
                val cardH = 153f
                val spacing = 80f
                val totalH = cardH * 2 + spacing
                val x = (pageW - cardW) / 2f
                val startY = (pageH - totalH) / 2f

                items.add(LayoutItem(0, RectF(x, startY, x + cardW, startY + cardH)))
                if (imageCount > 1) {
                    val y2 = startY + cardH + spacing
                    items.add(LayoutItem(1, RectF(x, y2, x + cardW, y2 + cardH)))
                } else {
                    val y2 = startY + cardH + spacing
                    items.add(LayoutItem(0, RectF(x, y2, x + cardW, y2 + cardH)))
                }
            }
            4 -> {
                // Layout 4: Four cards on one A4 (2x2 grid)
                val cardW = 243f
                val cardH = 153f
                val hSpacing = 30f
                val vSpacing = 50f
                val totalW = cardW * 2 + hSpacing
                val totalH = cardH * 2 + vSpacing
                val startX = (pageW - totalW) / 2f
                val startY = (pageH - totalH) / 2f

                for (row in 0..1) {
                    for (col in 0..1) {
                        val idx = (row * 2 + col) % imageCount
                        val x = startX + col * (cardW + hSpacing)
                        val y = startY + row * (cardH + vSpacing)
                        items.add(LayoutItem(idx, RectF(x, y, x + cardW, y + cardH)))
                    }
                }
            }
            5 -> {
                // Layout 5: Six cards on one A4 (2x3 grid)
                val cardW = 240f
                val cardH = 150f
                val hSpacing = 30f
                val vSpacing = 40f
                val totalW = cardW * 2 + hSpacing
                val totalH = cardH * 3 + vSpacing * 2
                val startX = (pageW - totalW) / 2f
                val startY = (pageH - totalH) / 2f

                for (row in 0..2) {
                    for (col in 0..1) {
                        val idx = (row * 2 + col) % imageCount
                        val x = startX + col * (cardW + hSpacing)
                        val y = startY + row * (cardH + vSpacing)
                        items.add(LayoutItem(idx, RectF(x, y, x + cardW, y + cardH)))
                    }
                }
            }
            6 -> {
                // Layout 6: Passport style copies (standard 3.5cm x 4.5cm photos)
                val cardW = 100f
                val cardH = 128f
                val hSpacing = 15f
                val vSpacing = 20f
                val cols = 4
                val totalW = cardW * cols + hSpacing * (cols - 1)
                val startX = (pageW - totalW) / 2f
                val startY = 40f

                for (i in 0 until passportCopies) {
                    val row = i / cols
                    val col = i % cols
                    val idx = i % imageCount // Cycles through images (or first image preference)
                    val x = startX + col * (cardW + hSpacing)
                    val y = startY + row * (cardH + vSpacing)
                    items.add(LayoutItem(idx, RectF(x, y, x + cardW, y + cardH)))
                }
            }
            7 -> {
                // Layout 7: Custom dynamic grid builder
                val margin = 30f
                val spacing = 15f
                val cols = customCols.coerceIn(1, 5)
                val rows = customRows.coerceIn(1, 8)

                val availW = pageW - (margin * 2)
                val availH = pageH - (margin * 2)

                val cardW = (availW - (spacing * (cols - 1))) / cols
                val cardH = (availH - (spacing * (rows - 1))) / rows

                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val i = row * cols + col
                        val idx = i % imageCount
                        val x = margin + col * (cardW + spacing)
                        val y = margin + row * (cardH + spacing)
                        items.add(LayoutItem(idx, RectF(x, y, x + cardW, y + cardH)))
                    }
                }
            }
        }
        return items
    }
}
