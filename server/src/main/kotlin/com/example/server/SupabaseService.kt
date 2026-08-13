package com.example.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class SupabaseService {
    private val client = HttpClient(CIO)
    private val supabaseUrl = System.getenv("SUPABASE_URL")?.removeSuffix("/") ?: ""
    private val supabaseKey = System.getenv("SUPABASE_KEY") ?: ""

    suspend fun uploadFile(fileName: String, bytes: ByteArray): String? {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            println("ERROR: SUPABASE_URL or SUPABASE_KEY environment variable is not set!")
            return null
        }

        // Supabase Storage API endpoint
        val uploadUrl = "$supabaseUrl/storage/v1/object/recipe-media/$fileName"
        println("Attempting upload to Supabase: $uploadUrl")
        
        try {
            val response: HttpResponse = client.post(uploadUrl) {
                header("Authorization", "Bearer $supabaseKey")
                header("apikey", supabaseKey)
                header("x-upsert", "true") // Allow overwriting
                
                val extension = fileName.substringAfterLast(".", "").lowercase()
                val contentType = when (extension) {
                    "jpg", "jpeg" -> ContentType.Image.JPEG
                    "png" -> ContentType.Image.PNG
                    "mp4" -> ContentType.Video.MP4
                    else -> ContentType.Application.OctetStream
                }
                header(HttpHeaders.ContentType, contentType.toString())
                setBody(bytes)
            }

            if (response.status.value in 200..299) {
                val publicUrl = "$supabaseUrl/storage/v1/object/public/recipe-media/$fileName"
                println("Supabase upload SUCCESS: $publicUrl")
                return publicUrl
            } else {
                val errorBody = response.bodyAsText()
                println("Supabase upload failed: ${response.status} - $errorBody")
                return null
            }
        } catch (e: Exception) {
            println("Supabase upload Exception: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
