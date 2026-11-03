package com.example.myrecipe.data

import androidx.room.*
import com.example.myrecipe.model.MealPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plans WHERE owner = :owner ORDER BY date ASC")
    fun getMealPlans(owner: String): Flow<List<MealPlan>>

    @Query("SELECT * FROM meal_plans WHERE owner = :owner ORDER BY date ASC")
    suspend fun getMealPlansSync(owner: String): List<MealPlan>

    @Query("SELECT * FROM meal_plans WHERE owner = :owner AND date = :date")
    suspend fun getMealPlansByDateSync(owner: String, date: Long): List<MealPlan>

    @Query("SELECT * FROM meal_plans WHERE recipeId = :recipeId AND owner = :owner")
    suspend fun getMealPlansByRecipeId(recipeId: String, owner: String): List<MealPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(plan: MealPlan): Long

    @Delete
    suspend fun deleteMealPlan(plan: MealPlan): Int

    @Query("SELECT * FROM meal_plans WHERE isSynced = 0")
    suspend fun getUnsyncedPlans(): List<MealPlan>

    @Query("SELECT * FROM meal_plans WHERE isSynced = 0")
    fun getUnsyncedPlansFlow(): Flow<List<MealPlan>>
}
