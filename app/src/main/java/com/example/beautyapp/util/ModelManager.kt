package com.example.beautyapp.util

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    private val client = OkHttpClient()

    fun downloadModel(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val request = Request.Builder().url(url).build()
        
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    onComplete(false)
                    return@Thread
                }

                val body = response.body ?: throw Exception("Empty body")
                val totalBytes = body.contentLength()
                val file = File(context.filesDir, fileName)
                
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                onProgress(totalRead.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }.start()
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        return File(context.filesDir, fileName).delete()
    }
}
