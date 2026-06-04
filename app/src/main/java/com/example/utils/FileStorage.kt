package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileStorage {
    fun copyImageToInternal(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val directory = File(context.filesDir, "card_print_images")
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val fileName = "img_${UUID.randomUUID()}.jpg"
                val file = File(directory, fileName)
                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.close()
                inputStream.close()
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToInternal(context: Context, bitmap: Bitmap): String? {
        return try {
            val directory = File(context.filesDir, "card_print_images")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createTempFileForCamera(context: Context): File {
        val directory = File(context.cacheDir, "camera_temp")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "temp_capture.jpg")
    }
}
