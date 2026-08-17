package com.example.myrecipe.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.data.AppDatabase
import com.example.myrecipe.data.RecipeRepository
import com.example.myrecipe.data.SyncWorker
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = RecipeRepository(
        database.recipeDao(),
        database.categoryDao(),
        RetrofitClient.instance
    )

    private val currentOwner = MutableStateFlow("")

    fun setCurrentOwner(owner: String) {
        if (currentOwner.value == owner) return
        currentOwner.value = owner
        if (owner.isNotBlank()) {
            SyncWorker.startPeriodicSync(getApplication(), owner)
            refreshData(owner)
        } else {
            SyncWorker.stopSync(getApplication())
        }
    }
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _dailyRecipes = MutableStateFlow<Map<String, List<Recipe>>>(emptyMap())
    val dailyRecipes: StateFlow<Map<String, List<Recipe>>> = _dailyRecipes

    private val _publicSearchResults = MutableStateFlow<List<Recipe>>(emptyList())
    val publicSearchResults: StateFlow<List<Recipe>> = _publicSearchResults

    private val _isSearchingPublic = MutableStateFlow(false)
    val isSearchingPublic: StateFlow<Boolean> = _isSearchingPublic

    fun clearError() {
        _errorMessage.value = null
    }

    init {
        startAutoSync()
        startAutoRefresh()
        fetchDailyRecipes(forceRandom = false)
    }

    private fun fetchDailyRecipes(forceRandom: Boolean) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.publicInstance
                
                // Expanded pool for Lunch and Dinner
                val mainMealPool = listOf(
                    "Beef", "Chicken", "Goat", "Lamb", "Pasta", 
                    "Pork", "Seafood", "Vegetarian", "Vegan", "Miscellaneous"
                )
                
                val seed = if (forceRandom) System.currentTimeMillis() else java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR).toLong()
                val random = java.util.Random(seed)
                
                val results = mutableMapOf<String, List<Recipe>>()
                
                // Helper to fetch random recipes from a list of categories
                suspend fun fetchMixedRecipes(count: Int, pool: List<String>): List<Recipe> {
                    val allMealIds = mutableListOf<String>()
                    // Shuffle pool to get different categories each time
                    val shuffledPool = pool.shuffled(random)
                    
                    for (cat in shuffledPool) {
                        if (allMealIds.size >= count) break
                        try {
                            val response = api.getRecipesByCategory(cat)
                            val ids = response.meals?.map { it.idMeal } ?: emptyList()
                            allMealIds.addAll(ids)
                        } catch (e: Exception) {
                            Log.e("RecipeViewModel", "Failed to fetch category $cat: ${e.message}")
                        }
                    }
                    
                    return allMealIds.shuffled(random).take(count).map { id ->
                        api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                    }.filterNotNull()
                }

                // 1. Breakfast (Always Breakfast category)
                val breakfastResponse = api.getRecipesByCategory("Breakfast")
                val breakfastIds = breakfastResponse.meals?.shuffled(random)?.take(5)?.map { it.idMeal } ?: emptyList()
                results["Breakfast"] = breakfastIds.map { id ->
                    api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                }.filterNotNull()

                // 2. Lunch - Now a mix!
                results["Lunch (Mixed)"] = fetchMixedRecipes(5, mainMealPool)

                // 3. Dinner - Also a mix!
                results["Dinner (Mixed)"] = fetchMixedRecipes(5, mainMealPool)

                // 4. Sweet Treats (Always Dessert category)
                val dessertResponse = api.getRecipesByCategory("Dessert")
                val dessertIds = dessertResponse.meals?.shuffled(random)?.take(5)?.map { it.idMeal } ?: emptyList()
                results["Sweet Treats"] = dessertIds.map { id ->
                    api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                }.filterNotNull()

                _dailyRecipes.value = results
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Failed to fetch daily recipes: ${e.message}")
            }
        }
    }

    private fun startAutoSync() {
        viewModelScope.launch {
            combine(
                repository.getUnsyncedRecipesFlow(),
                repository.getUnsyncedCategoriesFlow(),
                currentOwner
            ) { unsyncedRecipes, unsyncedCategories, owner ->
                (unsyncedRecipes.isNotEmpty() || unsyncedCategories.isNotEmpty()) to owner
            }.collect { (hasUnsynced, owner) ->
                if (hasUnsynced && owner.isNotBlank()) {
                    try {
                        repository.syncPendingChanges(owner)
                    } catch (e: Exception) {
                        _errorMessage.value = "Background sync failed: ${e.message}"
                    }
                }
            }
        }
    }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30000) // Auto-refresh every 30 seconds when app is active
                if (currentOwner.value.isNotBlank()) {
                    try {
                        repository.refreshData(currentOwner.value)
                    } catch (e: Exception) {
                        // Silent failure for auto-refresh
                    }
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recipes: StateFlow<List<Recipe>> = currentOwner.flatMapLatest { owner ->
        Log.d("RecipeViewModel", "Observing recipes for owner: $owner")
        repository.getRecipes(owner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<Category>> = currentOwner.flatMapLatest { owner ->
        Log.d("RecipeViewModel", "Observing categories for owner: $owner")
        repository.getCategories(owner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshData(owner: String) {
        if (owner.isBlank() || _isRefreshing.value) return
        currentOwner.value = owner
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Fetch latest from server (which also pushes local changes)
                repository.refreshData(owner)
                // Also refresh daily picks with a fresh random shuffle
                fetchDailyRecipes(forceRandom = true)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync data: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val owner = if (recipe.owner.isBlank()) currentOwner.value else recipe.owner
                
                var imageUriToSave = recipe.imageUri
                if (imageUriToSave != null) {
                    val scheme = imageUriToSave.scheme
                    if (scheme == "content" || (scheme == "file" && !imageUriToSave.path.orEmpty().contains(getApplication<Application>().filesDir.absolutePath))) {
                        // Copy to internal storage if it's a content URI or a file URI outside our internal files dir
                        imageUriToSave = saveFileToInternalStorage(imageUriToSave, "recipe_images", "img_")
                    }
                }

                var videoUriToSave = recipe.videoUri
                if (videoUriToSave != null) {
                    val scheme = videoUriToSave.scheme
                    if (scheme == "content" || (scheme == "file" && !videoUriToSave.path.orEmpty().contains(getApplication<Application>().filesDir.absolutePath))) {
                        videoUriToSave = saveFileToInternalStorage(videoUriToSave, "recipe_videos", "vid_")
                    }
                }
                
                val processedRecipe = recipe.copy(imageUri = imageUriToSave, videoUri = videoUriToSave, owner = owner)
                
                Log.d("RecipeViewModel", "Adding recipe locally: ${processedRecipe.title}, imageUri: ${processedRecipe.imageUri}")
                repository.addRecipe(processedRecipe)
                
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Failed to add recipe locally: ${e.message}")
                _errorMessage.value = "Failed to add recipe: ${e.message}"
            }
        }
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val owner = if (recipe.owner.isBlank()) currentOwner.value else recipe.owner
                
                var imageUriToSave = recipe.imageUri
                if (imageUriToSave != null) {
                    val scheme = imageUriToSave.scheme
                    if (scheme == "content" || (scheme == "file" && !imageUriToSave.path.orEmpty().contains(getApplication<Application>().filesDir.absolutePath))) {
                        imageUriToSave = saveFileToInternalStorage(imageUriToSave, "recipe_images", "img_")
                    }
                }

                var videoUriToSave = recipe.videoUri
                if (videoUriToSave != null) {
                    val scheme = videoUriToSave.scheme
                    if (scheme == "content" || (scheme == "file" && !videoUriToSave.path.orEmpty().contains(getApplication<Application>().filesDir.absolutePath))) {
                        videoUriToSave = saveFileToInternalStorage(videoUriToSave, "recipe_videos", "vid_")
                    }
                }

                val processedRecipe = recipe.copy(imageUri = imageUriToSave, videoUri = videoUriToSave, owner = owner)
                
                Log.d("RecipeViewModel", "Updating recipe locally: ${processedRecipe.title}, imageUri: ${processedRecipe.imageUri}")
                repository.updateRecipe(processedRecipe)
                
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Failed to update recipe locally: ${e.message}")
                _errorMessage.value = "Failed to update recipe: ${e.message}"
            }
        }
    }

    private fun saveFileToInternalStorage(uri: Uri, subDir: String, prefix: String): Uri? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val directory = File(context.filesDir, subDir)
            if (!directory.exists()) directory.mkdirs()

            val extension = if (subDir == "recipe_videos") "mp4" else "jpg"
            val fileName = "${prefix}${System.currentTimeMillis()}.$extension"
            val file = File(directory, fileName)

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                // Delete local image and video files if they exist
                listOfNotNull(recipe.imageUri, recipe.videoUri).forEach { uri ->
                    if (uri.scheme == "file") {
                        try {
                            val file = File(uri.path ?: "")
                            val internalDir = getApplication<Application>().filesDir
                            if (file.exists() && file.absolutePath.startsWith(internalDir.absolutePath)) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                repository.deleteRecipe(recipe)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete recipe: ${e.message}"
            }
        }
    }

    fun addCategory(name: String, owner: String) {
        viewModelScope.launch {
            try {
                repository.addCategory(Category(name = name, owner = owner))
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add category: ${e.message}"
            }
        }
    }

    fun deleteCategory(category: Category, deleteRecipes: Boolean = false) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(category, deleteRecipes)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete category: ${e.message}"
            }
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            repository.updateRecipe(recipe.copy(isFavorite = !recipe.isFavorite))
        }
    }

    fun searchPublicRecipes(query: String) {
        if (query.isBlank()) {
            _publicSearchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            _isSearchingPublic.value = true
            try {
                val api = RetrofitClient.publicInstance
                
                // 1. Search by Name (Full details)
                val nameResponse = api.searchRecipesByName(query)
                val nameResults = nameResponse.meals?.map { it.toRecipe() } ?: emptyList()
                
                // 2. Search by Main Ingredient (Requires follow-up detail lookup)
                val ingredientResponse = api.getRecipesByIngredient(query)
                val ingredientMealIds = ingredientResponse.meals?.map { it.idMeal } ?: emptyList()
                
                // 3. Search by Category (If query matches a category name)
                val categories = listOf("Beef", "Chicken", "Dessert", "Lamb", "Pasta", "Pork", "Seafood", "Side", "Starter", "Vegan", "Vegetarian", "Breakfast", "Goat")
                val matchedCategory = categories.firstOrNull { it.contains(query, ignoreCase = true) }
                val categoryMealIds = if (matchedCategory != null) {
                    api.getRecipesByCategory(matchedCategory).meals?.map { it.idMeal } ?: emptyList()
                } else {
                    emptyList()
                }
                
                // Combine and filter unique IDs that aren't already in nameResults
                val existingIds = nameResults.map { it.id.removePrefix("mealdb_") }.toSet()
                val uniqueNewIds = (ingredientMealIds + categoryMealIds).distinct().filter { it !in existingIds }.take(10)
                
                // Fetch full details for the new unique IDs
                val extraResults = uniqueNewIds.map { id ->
                    api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                }.filterNotNull()
                
                // Combine all results and filter for strict relevance
                val allResults = (nameResults + extraResults).distinctBy { it.id }
                
                val filteredResults = allResults.filter { recipe ->
                    recipe.title.contains(query, ignoreCase = true) ||
                    recipe.description?.contains(query, ignoreCase = true) == true ||
                    recipe.ingredients?.any { it.contains(query, ignoreCase = true) } == true ||
                    recipe.category?.equals(query, ignoreCase = true) == true
                }
                
                _publicSearchResults.value = filteredResults
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Public search failed: ${e.message}")
                _publicSearchResults.value = emptyList()
            } finally {
                _isSearchingPublic.value = false
            }
        }
    }

    fun savePublicRecipe(recipe: Recipe, category: String = "General") {
        viewModelScope.launch {
            try {
                // Create a clean copy of the public recipe for the current user
                val myRecipe = recipe.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    owner = currentOwner.value,
                    category = category,
                    isFavorite = false // Don't auto-favorite on save
                )
                
                // Use existing addRecipe logic which handles image/video storage correctly
                addRecipe(myRecipe)
                
                Log.d("RecipeViewModel", "Saved public recipe '${recipe.title}' to personal collection in category '$category'.")
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Failed to save public recipe: ${e.message}")
                _errorMessage.value = "Failed to save recipe: ${e.message}"
            }
        }
    }
}
