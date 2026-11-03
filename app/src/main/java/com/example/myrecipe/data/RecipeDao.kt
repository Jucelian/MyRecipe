package com.example.myrecipe.data

import androidx.room.*
import com.example.myrecipe.model.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE owner = :owner ORDER BY createdAt DESC")
    fun getRecipesByOwner(owner: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE owner = :owner ORDER BY createdAt DESC")
    suspend fun getRecipesByOwnerSync(owner: String): List<Recipe>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe): Int

    @Delete
    suspend fun deleteRecipe(recipe: Recipe): Int

    @Query("SELECT * FROM recipes WHERE isSynced = 0")
    fun getUnsyncedRecipesFlow(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE isSynced = 0")
    suspend fun getUnsyncedRecipes(): List<Recipe>
    @Query("SELECT * FROM recipes WHERE owner = :owner AND isSynced = 1")
    suspend fun getSyncedRecipesByOwner(owner: String): List<Recipe>

    @Query("DELETE FROM recipes WHERE owner = :owner AND isSynced = 1")
    suspend fun deleteSyncedRecipesByOwner(owner: String): Int

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getRecipeById(id: String): Recipe?

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipes(): List<Recipe>

    @Query("UPDATE recipes SET category = :newCategory, isSynced = 0 WHERE category = :oldCategory AND owner = :owner")
    suspend fun updateRecipeCategory(oldCategory: String, newCategory: String, owner: String): Int
}
