package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Exporter {

    // Helper to load bitmap from path safely
    private fun loadBitmap(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Generate PDF to a target file on A4 layout
    fun generatePdf(
        context: Context,
        editedImagePaths: List<String>,
        layoutType: Int,
        customCols: Int,
        customRows: Int,
        passportCopies: Int,
        outputFile: File
    ): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Fill page background with clean white
            canvas.drawColor(Color.WHITE)

            // Get layout items
            val items = LayoutEngine.getLayoutItems(
                layoutType = layoutType,
                imageCount = editedImagePaths.size,
                customCols = customCols,
                customRows = customRows,
                passportCopies = passportCopies
            )

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#CCCCCC") // Light gray cut line helper
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }

            // Load and draw each bitmap in its exact position
            val loadedBitmaps = editedImagePaths.map { loadBitmap(it) }

            for (item in items) {
                val bmp = loadedBitmaps.getOrNull(item.imageIndex)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, item.rect, paint)
                    canvas.drawRect(item.rect, borderPaint)
                }
            }

            pdfDocument.finishPage(page)

            val outputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Generate pristine high DPI A4 JPEG (2480 x 3508)
    fun generateJpg(
        context: Context,
        editedImagePaths: List<String>,
        layoutType: Int,
        customCols: Int,
        customRows: Int,
        passportCopies: Int,
        outputFile: File
    ): Boolean {
        return try {
            val fullW = 2480
            val fullH = 3508
            val bitmap = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Fill background white
            canvas.drawColor(Color.WHITE)

            val scaleX = fullW.toFloat() / 595f
            val scaleY = fullH.toFloat() / 842f

            val items = LayoutEngine.getLayoutItems(
                layoutType = layoutType,
                imageCount = editedImagePaths.size,
                customCols = customCols,
                customRows = customRows,
                passportCopies = passportCopies
            )

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#CCCCCC")
                style = Paint.Style.STROKE
                strokeWidth = 2.0f // scaled border
            }

            val loadedBitmaps = editedImagePaths.map { loadBitmap(it) }

            for (item in items) {
                val bmp = loadedBitmaps.getOrNull(item.imageIndex)
                if (bmp != null) {
                    val scaledRect = RectF(
                        item.rect.left * scaleX,
                        item.rect.top * scaleY,
                        item.rect.right * scaleX,
                        item.rect.bottom * scaleY
                    )
                    canvas.drawBitmap(bmp, null, scaledRect, paint)
                    canvas.drawRect(scaledRect, borderPaint)
                }
            }

            val outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.close()
            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Share a file using Android system sharesheet
    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Direct interface to Android printing subsystem
    fun printFile(context: Context, pdfFile: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "CardPrint Pro Job"
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val builder = PrintDocumentInfo.Builder("CardPrint_Output.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    callback.onLayoutFinished(builder.build(), true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    var input: FileInputStream? = null
                    var output: FileOutputStream? = null
                    try {
                        input = FileInputStream(pdfFile)
                        output = FileOutputStream(destination.fileDescriptor)
                        val buf = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } >= 0) {
                            output.write(buf, 0, bytesRead)
                        }
                        callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onWriteFailed(e.toString())
                    } finally {
                        input?.close()
                        output?.close()
                    }
                }
            }
            printManager.print(jobName, printAdapter, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
