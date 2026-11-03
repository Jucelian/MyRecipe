package com.example.myrecipe.data

import android.net.Uri
import android.util.Log
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan
import com.example.myrecipe.network.RecipeApiService
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val categoryDao: CategoryDao,
    private val shoppingDao: ShoppingDao,
    private val mealPlanDao: MealPlanDao,
    private val apiService: RecipeApiService
) {
    private val TAG = "RecipeRepository"
    private val syncMutex = Mutex()

    fun getRecipes(owner: String): Flow<List<Recipe>> = recipeDao.getRecipesByOwner(owner)
    fun getCategories(owner: String): Flow<List<Category>> = categoryDao.getCategoriesByOwner(owner)
    fun getShoppingList(owner: String): Flow<List<ShoppingItem>> = shoppingDao.getShoppingList(owner)
    fun getMealPlans(owner: String): Flow<List<MealPlan>> = mealPlanDao.getMealPlans(owner)
    
    fun getUnsyncedRecipesFlow(): Flow<List<Recipe>> = recipeDao.getUnsyncedRecipesFlow()
    fun getUnsyncedCategoriesFlow(): Flow<List<Category>> = categoryDao.getUnsyncedCategoriesFlow()
    fun getUnsyncedItemsFlow(): Flow<List<ShoppingItem>> = shoppingDao.getUnsyncedItemsFlow()
    fun getUnsyncedPlansFlow(): Flow<List<MealPlan>> = mealPlanDao.getUnsyncedPlansFlow()

    suspend fun refreshData(owner: String) {
        if (owner.isBlank()) return
        Log.d(TAG, "Refreshing data for owner: $owner")
        // 1. Push any local changes first
        try {
            syncPendingChanges(owner)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed during refreshData: ${e.message}")
        }

        // 2. Fetch from remote in parallel
        val remoteData = coroutineScope {
            val r = async { apiService.getRecipes(owner) }
            val c = async { apiService.getCategories(owner) }
            val s = async { apiService.getShoppingList(owner) }
            val m = async { apiService.getMealPlans(owner) }
            
            // Explicitly return a typed result object
            RemoteData(r.await(), c.await(), s.await(), m.await())
        }

        val remoteRecipes = remoteData.recipes
        val remoteCategories = remoteData.categories
        val remoteShoppingItems = remoteData.shoppingItems
        val remoteMealPlans = remoteData.mealPlans

        Log.d(TAG, "Fetched ${remoteRecipes.size} recipes, ${remoteCategories.size} categories, ${remoteShoppingItems.size} shopping items, and ${remoteMealPlans.size} meal plans")

        // 3. Update local database (Upsert), but DON'T overwrite unsynced local changes
        remoteRecipes.forEach { remoteRecipe ->
            val localRecipe = recipeDao.getRecipeById(remoteRecipe.id)
            if (localRecipe == null || localRecipe.isSynced) {
                recipeDao.insertRecipe(remoteRecipe.copy(owner = owner, isSynced = true))
            }
        }
        remoteCategories.forEach { remoteCategory ->
            val localCategories = categoryDao.getCategoriesByOwnerSync(owner)
            val localCategory = localCategories.find { it.id == remoteCategory.id }
            if (localCategory == null || localCategory.isSynced) {
                categoryDao.insertCategory(remoteCategory.copy(owner = owner, isSynced = true))
            }
        }
        remoteShoppingItems.forEach { remoteItem ->
            shoppingDao.insertItem(remoteItem.copy(owner = owner, isSynced = true))
        }
        remoteMealPlans.forEach { remotePlan ->
            mealPlanDao.insertMealPlan(remotePlan.copy(owner = owner, isSynced = true))
        }

        // 4. Cleanup local recipes that were deleted on other devices
        val remoteRecipeIds = remoteRecipes.map { it.id }.toSet()
        val localSyncedRecipes = recipeDao.getSyncedRecipesByOwner(owner)
        localSyncedRecipes.forEach { local ->
            if (local.id !in remoteRecipeIds) {
                recipeDao.deleteRecipe(local)
            }
        }

        // 5. Cleanup local categories
        val remoteCategoryIds = remoteCategories.map { it.id }.toSet()
        val localCategories = categoryDao.getCategoriesByOwnerSync(owner)
        localCategories.forEach { local ->
            if (local.id !in remoteCategoryIds && local.isSynced) {
                categoryDao.deleteCategory(local)
            }
        }

        // 6. Cleanup local shopping items
        val remoteShoppingItemIds = remoteShoppingItems.map { it.id }.toSet()
        val localShoppingList = shoppingDao.getShoppingListSync(owner)
        localShoppingList.forEach { local ->
            if (local.id !in remoteShoppingItemIds && local.isSynced) {
                shoppingDao.deleteItem(local)
            }
        }

        // 7. Cleanup local meal plans
        val remoteMealPlanIds = remoteMealPlans.map { it.id }.toSet()
        val localMealPlans = mealPlanDao.getMealPlansSync(owner)
        localMealPlans.forEach { local ->
            if (local.id !in remoteMealPlanIds && local.isSynced) {
                mealPlanDao.deleteMealPlan(local)
            }
        }
    }

    private data class RemoteData(
        val recipes: List<Recipe>,
        val categories: List<Category>,
        val shoppingItems: List<ShoppingItem>,
        val mealPlans: List<MealPlan>
    )

    suspend fun addRecipe(recipe: Recipe) {
        Log.d(TAG, "Adding recipe: ${recipe.title}")
        recipeDao.insertRecipe(recipe.copy(isSynced = false))
        try {
            syncPendingChanges(recipe.owner)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed after addRecipe: ${e.message}")
        }
    }

    suspend fun updateRecipe(recipe: Recipe) {
        Log.d(TAG, "Updating recipe: ${recipe.title}")
        recipeDao.insertRecipe(recipe.copy(isSynced = false))
        try {
            syncPendingChanges(recipe.owner)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed after updateRecipe: ${e.message}")
        }
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        Log.d(TAG, "Deleting recipe: ${recipe.title}")
        recipeDao.deleteRecipe(recipe)
        try {
            apiService.deleteRecipe(recipe.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting remote recipe: ${e.message}")
        }
    }

    suspend fun addCategory(category: Category) {
        categoryDao.insertCategory(category.copy(isSynced = false))
        syncPendingChanges(category.owner)
    }

    suspend fun addShoppingItem(item: ShoppingItem) {
        shoppingDao.insertItem(item.copy(isSynced = false))
        syncPendingChanges(item.owner)
    }

    suspend fun addShoppingItems(items: List<ShoppingItem>, owner: String) {
        if (items.isEmpty()) return
        shoppingDao.insertItems(items.map { it.copy(isSynced = false, owner = owner) })
        syncPendingChanges(owner)
    }

    suspend fun updateShoppingItem(item: ShoppingItem) {
        shoppingDao.insertItem(item.copy(isSynced = false))
        syncPendingChanges(item.owner)
    }

    suspend fun deleteShoppingItem(item: ShoppingItem) {
        shoppingDao.deleteItem(item)
        try {
            apiService.deleteShoppingItem(item.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting remote shopping item: ${e.message}")
        }
    }

    suspend fun clearCheckedShoppingItems(owner: String) {
        shoppingDao.clearCheckedItems(owner)
        try {
            apiService.clearCheckedItems(owner)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing remote shopping items: ${e.message}")
        }
        syncPendingChanges(owner)
    }

    suspend fun addMealPlan(plan: MealPlan) {
        mealPlanDao.insertMealPlan(plan.copy(isSynced = false))
        syncPendingChanges(plan.owner)
    }

    suspend fun deleteMealPlan(plan: MealPlan) {
        // 1. Delete the meal plan locally and remote
        mealPlanDao.deleteMealPlan(plan)
        try {
            apiService.deleteMealPlan(plan.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting remote meal plan: ${e.message}")
        }

        // 2. Check if this recipe is still planned for any other day
        val otherPlans = mealPlanDao.getMealPlansByRecipeId(plan.recipeId, plan.owner)
        
        // 3. If no other plans exist, cleanup corresponding shopping items
        if (otherPlans.isEmpty()) {
            val itemsToRemove = shoppingDao.getUncheckedItemsByRecipeId(plan.recipeId, plan.owner)
            Log.d(TAG, "Cleaning up ${itemsToRemove.size} shopping items for deleted meal plan")
            
            itemsToRemove.forEach { item ->
                deleteShoppingItem(item)
            }
        }
    }

    suspend fun updateCategory(oldName: String, newCategory: Category) {
        categoryDao.insertCategory(newCategory.copy(isSynced = false))
        recipeDao.updateRecipeCategory(oldName, newCategory.name, newCategory.owner)
        syncPendingChanges(newCategory.owner)
    }

    suspend fun deleteCategory(category: Category, deleteRecipes: Boolean = false) {
        val recipesInCat = recipeDao.getRecipesByOwnerSync(category.owner).filter { r -> r.category == category.name }
        
        if (deleteRecipes) {
            recipesInCat.forEach { recipe -> deleteRecipe(recipe) }
        } else {
            recipesInCat.forEach { recipe ->
                recipeDao.insertRecipe(recipe.copy(category = "General", isSynced = false))
            }
        }
        
        categoryDao.deleteCategory(category)
        try {
            apiService.deleteCategory(category.id)
            if (!deleteRecipes) syncPendingChanges(category.owner)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting remote category: ${e.message}")
        }
    }

    suspend fun syncPendingChanges(owner: String) = syncMutex.withLock {
        Log.d(TAG, "Syncing pending changes for owner: $owner")
        
        coroutineScope {
            val catJob = async {
                val unsynced = categoryDao.getUnsyncedCategories().filter { it.owner == owner || it.owner.isBlank() }
                for (category in unsynced) {
                    try {
                        val toSync = if (category.owner.isBlank()) category.copy(owner = owner) else category
                        val response = apiService.addCategory(toSync)
                        if (response["status"] == "success") categoryDao.insertCategory(toSync.copy(isSynced = true))
                    } catch (e: Exception) { Log.e(TAG, "Error syncing category: ${e.message}") }
                }
            }

            val shopJob = async {
                val unsynced = shoppingDao.getUnsyncedItems().filter { it.owner == owner || it.owner.isBlank() }
                for (item in unsynced) {
                    try {
                        val toSync = if (item.owner.isBlank()) item.copy(owner = owner) else item
                        val response = apiService.addShoppingItem(toSync)
                        if (response["status"] == "success") shoppingDao.insertItem(toSync.copy(isSynced = true))
                    } catch (e: Exception) { Log.e(TAG, "Error syncing item: ${e.message}") }
                }
            }

            val planJob = async {
                val unsynced = mealPlanDao.getUnsyncedPlans().filter { it.owner == owner || it.owner.isBlank() }
                for (plan in unsynced) {
                    try {
                        val toSync = if (plan.owner.isBlank()) plan.copy(owner = owner) else plan
                        val response = apiService.addMealPlan(toSync)
                        if (response["status"] == "success") mealPlanDao.insertMealPlan(toSync.copy(isSynced = true))
                    } catch (e: Exception) { Log.e(TAG, "Error syncing plan: ${e.message}") }
                }
            }

            val recipeJob = async {
                val unsynced = recipeDao.getUnsyncedRecipes().filter { it.owner == owner || it.owner.isBlank() }
                for (recipe in unsynced) {
                    try {
                        var currentRecipe = if (recipe.owner.isBlank()) recipe.copy(owner = owner) else recipe
                        var skipSync = false

                        currentRecipe.imageUri?.takeIf { it.scheme == "file" }?.path?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                try {
                                    val response = uploadFile(file, "image")
                                    getRemoteUri(response)?.let { currentRecipe = currentRecipe.copy(imageUri = it) } ?: run { skipSync = true }
                                } catch (e: Exception) { skipSync = true }
                            }
                        }

                        if (!skipSync) {
                            currentRecipe.videoUri?.takeIf { it.scheme == "file" }?.path?.let { path ->
                                val file = File(path)
                                if (file.exists()) {
                                    try {
                                        val response = uploadFile(file, "video")
                                        getRemoteUri(response)?.let { currentRecipe = currentRecipe.copy(videoUri = it) } ?: run { skipSync = true }
                                    } catch (e: Exception) { skipSync = true }
                                }
                            }
                        }

                        if (!skipSync) {
                            val response = apiService.addRecipe(currentRecipe)
                            if (response["status"] == "success") recipeDao.insertRecipe(currentRecipe.copy(isSynced = true))
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing recipe: ${e.message}") }
                }
            }
            
            awaitAll(catJob, shopJob, planJob, recipeJob)
        }
    }

    private suspend fun uploadFile(file: File, type: String): Map<String, String> {
        val mediaType = if (type == "video") "video/*" else "image/*"
        val requestFile = file.asRequestBody(mediaType.toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
        return apiService.uploadImage(body)
    }

    private fun getRemoteUri(response: Map<String, String>): Uri? {
        val relativeUrl = response["url"] ?: return null
        val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
        return Uri.parse("$baseUrl$relativeUrl")
    }
}
