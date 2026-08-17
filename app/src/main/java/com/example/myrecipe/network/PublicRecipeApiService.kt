package com.example.myrecipe.network

import com.example.myrecipe.model.MealDBResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PublicRecipeApiService {
    @GET("random.php")
    suspend fun getRandomRecipe(): MealDBResponse

    @GET("search.php")
    suspend fun searchRecipesByName(@Query("s") query: String): MealDBResponse

    @GET("filter.php")
    suspend fun getRecipesByIngredient(@Query("i") ingredient: String): MealDBResponse

    @GET("filter.php")
    suspend fun getRecipesByCategory(@Query("c") category: String): MealDBResponse

    @GET("lookup.php")
    suspend fun getRecipeDetails(@Query("i") id: String): MealDBResponse
}
