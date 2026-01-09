package com.mirror2922.ecvl.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ModelManager {
    private val client = OkHttpClient()

    suspend fun downloadModel(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@withContext
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
                                val progress = totalRead.toFloat() / totalBytes
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        return File(context.filesDir, fileName).delete()
    }
}