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
    const val BASE_URL = "https://my-recipe-server-t7vb.onrender.com/"
    
    private var tokenProvider: (() -> String?)? = null
    private var refreshAction: (suspend () -> Boolean)? = null

    fun setTokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    fun setRefreshAction(action: suspend () -> Boolean) {
        refreshAction = action
    }

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
        .addInterceptor { chain ->
            val original = chain.request()
            val token = tokenProvider?.invoke()
            val requestBuilder = original.newBuilder()
            
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            
            chain.proceed(requestBuilder.build())
        }
        .authenticator { _, response ->
            // If we get a 401, try to refresh the token
            if (response.code == 401 && refreshAction != null) {
                val success = kotlinx.coroutines.runBlocking {
                    refreshAction?.invoke() ?: false
                }
                
                if (success) {
                    val newToken = tokenProvider?.invoke()
                    if (newToken != null) {
                        return@authenticator response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                }
            }
            null
        }
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val instance: RecipeApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(RecipeApiService::class.java)
}
