package com.example.myrecipe.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.data.AppDatabase
import com.example.myrecipe.data.RecipeRepository
import com.example.myrecipe.data.SyncWorker
import com.example.myrecipe.data.MealReminderWorker
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = RecipeRepository(
        database.recipeDao(),
        database.categoryDao(),
        database.shoppingDao(),
        database.mealPlanDao(),
        RetrofitClient.instance
    )

    private val currentOwner = MutableStateFlow("")

    fun setCurrentOwner(owner: String) {
        if (currentOwner.value == owner) return
        currentOwner.value = owner
        if (owner.isNotBlank()) {
            SyncWorker.startPeriodicSync(getApplication(), owner)
            MealReminderWorker.scheduleDailyReminder(getApplication())
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

    private var lastDailyFetchDay = -1

    fun clearError() {
        _errorMessage.value = null
    }

    init {
        startAutoSync()
        startAutoRefresh()
        // Initial fetch for the current day
        fetchDailyRecipes(forceRandom = false)
    }

    private fun fetchDailyRecipes(forceRandom: Boolean) {
        val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (!forceRandom && currentDay == lastDailyFetchDay) return
        
        lastDailyFetchDay = currentDay
        viewModelScope.launch {
            try {
                val api = RetrofitClient.publicInstance
                
                // Expanded pool for Lunch and Dinner
                val mainMealPool = listOf(
                    "Beef", "Chicken", "Goat", "Lamb", "Pasta", 
                    "Pork", "Seafood", "Vegetarian", "Vegan", "Miscellaneous"
                )
                
                // Better seeding: Combine Year and Day to ensure it's unique every single day forever
                val calendar = java.util.Calendar.getInstance()
                val dateSeed = calendar.get(java.util.Calendar.YEAR) * 1000L + calendar.get(java.util.Calendar.DAY_OF_YEAR)
                
                val seed = if (forceRandom) System.currentTimeMillis() else dateSeed
                val random = java.util.Random(seed)
                
                val results = mutableMapOf<String, List<Recipe>>()
                
                // Helper to fetch random recipes from a list of categories with strict dessert filtering
                suspend fun fetchMixedRecipes(count: Int, pool: List<String>): List<Recipe> {
                    val shuffledPool = pool.shuffled(random).take(3)
                    
                    val allMealIds = coroutineScope {
                        shuffledPool.map { cat ->
                            async {
                                try {
                                    api.getRecipesByCategory(cat).meals?.map { it.idMeal } ?: emptyList()
                                } catch (e: Exception) { emptyList() }
                            }
                        }.awaitAll().flatten()
                    }
                    
                    val shuffledIds = allMealIds.shuffled(random).take(count * 2)
                    
                    return coroutineScope {
                        shuffledIds.map { id ->
                            async {
                                try {
                                    val meal = api.getRecipeDetails(id).meals?.firstOrNull()
                                    if (meal != null && meal.strCategory != "Dessert") meal.toRecipe() else null
                                } catch (e: Exception) { null }
                            }
                        }.awaitAll().filterNotNull().take(count)
                    }
                }

                coroutineScope {
                    val breakfastJob = async {
                        val breakfastIds = api.getRecipesByCategory("Breakfast").meals?.shuffled(java.util.Random(seed + 123))?.take(5)?.map { it.idMeal } ?: emptyList()
                        breakfastIds.map { id -> async { api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe() } }.awaitAll().filterNotNull()
                    }

                    val lunchJob = async { fetchMixedRecipes(5, mainMealPool) }
                    val dinnerJob = async { fetchMixedRecipes(5, mainMealPool) }

                    val dessertJob = async {
                        val dessertIds = api.getRecipesByCategory("Dessert").meals?.shuffled(java.util.Random(seed + 456))?.take(5)?.map { it.idMeal } ?: emptyList()
                        dessertIds.map { id -> async { api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe() } }.awaitAll().filterNotNull()
                    }

                    results["Breakfast"] = breakfastJob.await()
                    results["Lunch (Mixed)"] = lunchJob.await()
                    results["Dinner (Mixed)"] = dinnerJob.await()
                    results["Sweet Treats"] = dessertJob.await()
                }

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val shoppingList: StateFlow<List<ShoppingItem>> = currentOwner.flatMapLatest { owner ->
        repository.getShoppingList(owner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val mealPlans: StateFlow<List<MealPlan>> = currentOwner.flatMapLatest { owner ->
        repository.getMealPlans(owner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _communityRecipes = MutableStateFlow<List<Recipe>>(emptyList())
    val communityRecipes: StateFlow<List<Recipe>> = _communityRecipes

    fun refreshData(owner: String, forceDailyRefresh: Boolean = false) {
        if (owner.isBlank() || _isRefreshing.value) return
        currentOwner.value = owner
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Fetch latest from server (which also pushes local changes)
                repository.refreshData(owner)
                // Refresh daily picks if new day or forced
                fetchDailyRecipes(forceRandom = forceDailyRefresh)
                // Also fetch community recipes
                fetchCommunityRecipes()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync data: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchCommunityRecipes() {
        try {
            val recipes = RetrofitClient.instance.getCommunityRecipes()
            _communityRecipes.value = recipes.filter { it.owner != currentOwner.value }
        } catch (e: Exception) {
            Log.e("RecipeViewModel", "Failed to fetch community: ${e.message}")
        }
    }

    fun togglePublish(recipe: Recipe) {
        viewModelScope.launch {
            repository.updateRecipe(recipe.copy(isPublic = !recipe.isPublic, isSynced = false))
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

    fun updateCategory(oldName: String, category: Category) {
        viewModelScope.launch {
            try {
                repository.updateCategory(oldName, category)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update category: ${e.message}"
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

    fun addIngredientsToShoppingList(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val currentList = shoppingList.value
                recipe.ingredients?.forEach { ingredient ->
                    // Check if item already exists (simple name match)
                    val existingItem = currentList.find { it.name.equals(ingredient, ignoreCase = true) && !it.isChecked }
                    
                    if (existingItem != null) {
                        // Increment quantity if possible (very basic parser)
                        val newQuantity = try {
                            val currentNum = existingItem.quantity?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                            "${currentNum + 1}"
                        } catch (e: Exception) { "2" }
                        
                        // Update item: Keep existing recipe info if present, otherwise use current recipe
                        val updatedItem = existingItem.copy(
                            quantity = newQuantity,
                            isSynced = false,
                            recipeId = existingItem.recipeId ?: recipe.id,
                            recipeTitle = existingItem.recipeTitle ?: recipe.title
                        )
                        repository.updateShoppingItem(updatedItem)
                    } else {
                        val item = ShoppingItem(
                            name = ingredient,
                            category = recipe.category ?: "Other",
                            owner = currentOwner.value,
                            recipeId = recipe.id,
                            recipeTitle = recipe.title
                        )
                        repository.addShoppingItem(item)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add ingredients: ${e.message}"
            }
        }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateShoppingItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteShoppingItem(item)
        }
    }

    fun clearCheckedShoppingItems() {
        viewModelScope.launch {
            repository.clearCheckedShoppingItems(currentOwner.value)
        }
    }

    fun addMealPlan(recipe: Recipe, date: Long, mealType: String) {
        viewModelScope.launch {
            try {
                val plan = MealPlan(
                    recipeId = recipe.id,
                    recipeTitle = recipe.title,
                    date = date,
                    mealType = mealType,
                    owner = currentOwner.value
                )
                repository.addMealPlan(plan)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add meal plan: ${e.message}"
            }
        }
    }

    fun updateMealPlan(plan: MealPlan) {
        viewModelScope.launch {
            try {
                repository.addMealPlan(plan) // Repository uses insert (replace)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update meal plan: ${e.message}"
            }
        }
    }

    fun deleteMealPlan(plan: MealPlan) {
        viewModelScope.launch {
            repository.deleteMealPlan(plan)
        }
    }

    fun generateShoppingListForWeek() {
        viewModelScope.launch {
            try {
                val startCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startTime = startCalendar.timeInMillis
                val endTime = startTime + 7 * 24 * 60 * 60 * 1000L
                
                val weeklyPlans = mealPlans.value.filter { it.date in startTime until endTime }
                val allRecipes = recipes.value
                val currentShoppingList = shoppingList.value
                
                weeklyPlans.forEach { plan ->
                    val recipe = allRecipes.find { it.id == plan.recipeId }
                    recipe?.ingredients?.forEach { ingredient ->
                        val existingItem = currentShoppingList.find { it.name.equals(ingredient, ignoreCase = true) && !it.isChecked }
                        if (existingItem == null) {
                            val item = ShoppingItem(
                                name = ingredient,
                                category = recipe.category ?: "Other",
                                owner = currentOwner.value,
                                recipeId = recipe.id,
                                recipeTitle = recipe.title
                            )
                            repository.addShoppingItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Failed to generate shopping list: ${e.message}")
                _errorMessage.value = "Failed to generate shopping list: ${e.message}"
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
                    isFavorite = false,
                    createdAt = System.currentTimeMillis()
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

    private val _aiGeneratedRecipe = MutableStateFlow<Recipe?>(null)
    val aiGeneratedRecipe: StateFlow<Recipe?> = _aiGeneratedRecipe

    private val _isGeneratingAI = MutableStateFlow(false)
    val isGeneratingAI: StateFlow<Boolean> = _isGeneratingAI

    fun generateRecipeFromIngredients(ingredients: List<String>) {
        if (ingredients.isEmpty()) return
        
        viewModelScope.launch {
            _isGeneratingAI.value = true
            _aiGeneratedRecipe.value = null
            try {
                // Simulate AI logic by searching for recipes with these ingredients
                val api = RetrofitClient.publicInstance
                val firstIngredient = ingredients.first()
                val response = api.getRecipesByIngredient(firstIngredient)
                val mealId = response.meals?.randomOrNull()?.idMeal
                
                if (mealId != null) {
                    val details = api.getRecipeDetails(mealId).meals?.firstOrNull()
                    _aiGeneratedRecipe.value = details?.toRecipe()?.copy(owner = "AI Chef")
                } else {
                    // Fallback to a placeholder if no match found
                    _aiGeneratedRecipe.value = Recipe(
                        title = "AI Surprise: ${ingredients.joinToString(" & ")}",
                        description = "A creative dish made with what you have!",
                        ingredients = ingredients + listOf("Salt", "Pepper", "Olive Oil"),
                        instructions = listOf(
                            "Clean and prep all your ingredients.",
                            "Saute the ${ingredients.joinToString(" and ")} in a pan with olive oil.",
                            "Season with salt and pepper to taste.",
                            "Serve hot and enjoy your AI-powered meal!"
                        ),
                        owner = "AI Chef",
                        rating = 5.0,
                        tags = listOf("AI", "Quick", "Custom")
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "AI Generation failed: ${e.message}"
            } finally {
                _isGeneratingAI.value = false
            }
        }
    }

    fun clearAIRecipe() {
        _aiGeneratedRecipe.value = null
    }
}
