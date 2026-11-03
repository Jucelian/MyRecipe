package com.example.myrecipe.data

import androidx.room.*
import com.example.myrecipe.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list WHERE owner = :owner")
    fun getShoppingList(owner: String): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_list WHERE owner = :owner")
    suspend fun getShoppingListSync(owner: String): List<ShoppingItem>

    @Query("SELECT * FROM shopping_list WHERE owner = :owner AND isChecked = :checked")
    suspend fun getCheckedItemsSync(owner: String, checked: Boolean = true): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItem>): List<Long>

    @Update
    suspend fun updateItem(item: ShoppingItem): Int

    @Delete
    suspend fun deleteItem(item: ShoppingItem): Int

    @Query("DELETE FROM shopping_list WHERE owner = :owner AND isChecked = :checked")
    suspend fun clearCheckedItems(owner: String, checked: Boolean = true): Int

    @Query("SELECT * FROM shopping_list WHERE recipeId = :recipeId AND owner = :owner AND isChecked = 0")
    suspend fun getUncheckedItemsByRecipeId(recipeId: String, owner: String): List<ShoppingItem>

    @Query("SELECT * FROM shopping_list WHERE isSynced = 0")
    suspend fun getUnsyncedItems(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_list WHERE isSynced = 0")
    fun getUnsyncedItemsFlow(): Flow<List<ShoppingItem>>
}
