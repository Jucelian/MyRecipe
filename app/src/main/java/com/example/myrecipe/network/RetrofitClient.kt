package com.example.myrecipe.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Replace with your computer's local IP address
    // Use "10.0.2.2" for Emulator or your local IP (e.g., 192.168.1.73) for physical devices
    private const val BASE_URL = "http://192.168.1.73:8080/"

    val instance: RecipeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecipeApiService::class.java)
    }
}
