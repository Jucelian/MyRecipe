package com.example.myrecipe.network

import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.User
import okhttp3.MultipartBody
import retrofit2.http.*

interface RecipeApiService {
    @POST("signup")
    suspend fun signup(@Body user: User): Map<String, String>

    @POST("login")
    suspend fun login(@Body user: User): Map<String, String>

    @Multipart
    @POST("upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): Map<String, String>

    @GET("recipes/{owner}")
    suspend fun getRecipes(@Path("owner") owner: String): List<Recipe>

    @POST("recipes")
    suspend fun addRecipe(@Body recipe: Recipe): Map<String, String>

    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String): Map<String, String>

    @GET("categories/{owner}")
    suspend fun getCategories(@Path("owner") owner: String): List<Category>

    @POST("categories")
    suspend fun addCategory(@Body category: Category): Map<String, String>

    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): Map<String, String>
}
