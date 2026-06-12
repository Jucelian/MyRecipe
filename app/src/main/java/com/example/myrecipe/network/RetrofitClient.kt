package com.example.myrecipe.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://my-recipe-server-t7vb.onrender.com/"

    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, JsonSerializer<Uri> { src, _, _ ->
            JsonPrimitive(src.toString())
        })
        .registerTypeAdapter(Uri::class.java, JsonDeserializer { json, _, _ ->
            Uri.parse(json.asString)
        })
        .create()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: RecipeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RecipeApiService::class.java)
    }
}
