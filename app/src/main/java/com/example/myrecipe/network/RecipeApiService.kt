package com.example.myrecipe.network

import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.User
import com.example.myrecipe.model.UpdateInfo
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan
import okhttp3.MultipartBody
import retrofit2.http.*

interface RecipeApiService {
    @GET("app/version")
    suspend fun getUpdateInfo(): UpdateInfo

    @POST("signup")
    suspend fun signup(@Body user: User): LoginResponse

    @POST("login")
    suspend fun login(@Body user: User): LoginResponse

    @POST("refresh")
    suspend fun refreshToken(@Body body: Map<String, String>): LoginResponse

    @POST("user/update")
    suspend fun updateUser(@Body user: User): Map<String, String>

    @POST("ai/generate")
    suspend fun generateAiAvatar(@Body request: Map<String, String>): Map<String, String>

    @Multipart
    @POST("upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): Map<String, String>

    @GET("recipes/community")
    suspend fun getCommunityRecipes(): List<Recipe>

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

    @GET("shopping/{owner}")
    suspend fun getShoppingList(@Path("owner") owner: String): List<ShoppingItem>

    @POST("shopping")
    suspend fun addShoppingItem(@Body item: ShoppingItem): Map<String, String>

    @DELETE("shopping/{id}")
    suspend fun deleteShoppingItem(@Path("id") id: String): Map<String, String>

    @DELETE("shopping/clear/{owner}")
    suspend fun clearCheckedItems(@Path("owner") owner: String): Map<String, String>

    @GET("planner/{owner}")
    suspend fun getMealPlans(@Path("owner") owner: String): List<MealPlan>

    @POST("planner")
    suspend fun addMealPlan(@Body plan: MealPlan): Map<String, String>

    @DELETE("planner/{id}")
    suspend fun deleteMealPlan(@Path("id") id: String): Map<String, String>
}
