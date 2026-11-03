package com.example.myrecipe.data

import androidx.room.*
import com.example.myrecipe.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE owner = :owner ORDER BY name ASC")
    fun getCategoriesByOwner(owner: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE owner = :owner ORDER BY name ASC")
    suspend fun getCategoriesByOwnerSync(owner: String): List<Category>

    @Query("SELECT * FROM categories WHERE isSynced = 0")
    suspend fun getUnsyncedCategories(): List<Category>

    @Query("SELECT * FROM categories WHERE isSynced = 0")
    fun getUnsyncedCategoriesFlow(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category): Int
    
    @Query("DELETE FROM categories WHERE owner = :owner")
    suspend fun deleteCategoriesByOwner(owner: String): Int
}
