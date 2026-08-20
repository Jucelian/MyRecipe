package com.example.myrecipe.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.data.AppDatabase
import com.example.myrecipe.data.RecipeRepository
import com.example.myrecipe.data.SyncWorker
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

private fun mapToAisle(category: String?, ingredient: String): String {
    val cat = category?.lowercase() ?: ""
    val ing = ingredient.lowercase()
    
    return when {
        ing.contains("chicken") || ing.contains("beef") || ing.contains("pork") || ing.contains("lamb") || ing.contains("bacon") || ing.contains("steak") || cat.contains("chicken") || cat.contains("beef") || cat.contains("pork") || cat.contains("lamb") || cat.contains("meat") -> "Meat & Poultry"
        ing.contains("fish") || ing.contains("shrimp") || ing.contains("prawn") || ing.contains("salmon") || cat.contains("seafood") -> "Seafood"
        ing.contains("milk") || ing.contains("cheese") || ing.contains("butter") || ing.contains("yogurt") || ing.contains("cream") || ing.contains("egg") -> "Dairy & Eggs"
        ing.contains("onion") || ing.contains("garlic") || ing.contains("tomato") || ing.contains("potato") || ing.contains("carrot") || ing.contains("lettuce") || ing.contains("apple") || ing.contains("banana") || cat.contains("vegetarian") || cat.contains("vegan") -> "Produce"
        ing.contains("flour") || ing.contains("sugar") || ing.contains("oil") || ing.contains("salt") || ing.contains("pepper") || ing.contains("spice") || ing.contains("sauce") -> "Pantry"
        ing.contains("bread") || ing.contains("pasta") || ing.contains("rice") || ing.contains("noodle") || cat.contains("pasta") -> "Grains & Pasta"
        cat.contains("dessert") || cat.contains("sweet") || ing.contains("chocolate") || ing.contains("cake") -> "Sweet Treats"
        else -> "Other"
    }
}

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
                        refreshData(currentOwner.value, silent = true)
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

    // UI State Persistence
    val expandedCategories = mutableStateMapOf<String, Boolean>()
    val expandedDays = mutableStateMapOf<Long, Boolean>()

    fun refreshData(owner: String, forceDailyRefresh: Boolean = false, silent: Boolean = false) {
        if (owner.isBlank() || _isRefreshing.value) return
        currentOwner.value = owner
        viewModelScope.launch {
            if (!silent) _isRefreshing.value = true
            try {
                // Fetch latest from server (which also pushes local changes)
                repository.refreshData(owner)
                // Refresh daily picks if new day or forced
                fetchDailyRecipes(forceRandom = forceDailyRefresh)
                // Also fetch community recipes
                fetchCommunityRecipes()
            } catch (e: Exception) {
                if (!silent) _errorMessage.value = "Failed to sync data: ${e.message}"
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
                            recipeTitle = existingItem.recipeTitle ?: recipe.title,
                            category = mapToAisle(recipe.category, ingredient)
                        )
                        repository.updateShoppingItem(updatedItem)
                    } else {
                        val item = ShoppingItem(
                            name = ingredient,
                            category = mapToAisle(recipe.category, ingredient),
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
                val startCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startTime = startCalendar.timeInMillis
                val endTime = startTime + 8 * 24 * 60 * 60 * 1000L
                
                val weeklyPlans = mealPlans.value.filter { it.date in startTime..endTime }
                Log.d("RecipeViewModel", "Generating shopping list. Found ${weeklyPlans.size} plans in range.")
                
                val allLocalRecipes = recipes.value
                val currentShoppingList = shoppingList.value
                val itemsToAdd = mutableListOf<ShoppingItem>()
                val publicApi = RetrofitClient.publicInstance

                weeklyPlans.forEach { plan ->
                    // 1. Try to find the recipe (Local or fetch from Remote)
                    val recipe: Recipe? = if (plan.recipeId.startsWith("mealdb_")) {
                        Log.d("RecipeViewModel", "Fetching remote recipe for shopping list: ${plan.recipeTitle}")
                        try {
                            val id = plan.recipeId.removePrefix("mealdb_")
                            publicApi.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                        } catch (e: Exception) {
                            Log.e("RecipeViewModel", "Failed to fetch remote recipe ${plan.recipeId}: ${e.message}")
                            null
                        }
                    } else {
                        allLocalRecipes.find { it.id == plan.recipeId }
                    }

                    recipe?.ingredients?.forEach { ingredient ->
                        val alreadyInList = currentShoppingList.any { it.name.equals(ingredient, ignoreCase = true) && !it.isChecked }
                        val alreadyPlanned = itemsToAdd.any { it.name.equals(ingredient, ignoreCase = true) }
                        
                        if (!alreadyInList && !alreadyPlanned) {
                            itemsToAdd.add(ShoppingItem(
                                name = ingredient,
                                category = mapToAisle(recipe.category, ingredient),
                                owner = currentOwner.value,
                                recipeId = recipe.id,
                                recipeTitle = recipe.title
                            ))
                        }
                    }
                }
                
                if (itemsToAdd.isNotEmpty()) {
                    Log.d("RecipeViewModel", "Adding ${itemsToAdd.size} unique ingredients to shopping list")
                    repository.addShoppingItems(itemsToAdd, currentOwner.value)
                } else {
                    Log.d("RecipeViewModel", "No new ingredients to add")
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
        viewModelScope.launch {
            _isGeneratingAI.value = true
            try {
                val api = RetrofitClient.publicInstance
                
                // 1. Flatten and clean ingredients (handle potential commas in items)
                val cleanIngredients = ingredients
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                
                Log.d("RecipeViewModel", "Starting Fridge AI with ${cleanIngredients.size} clean ingredients: $cleanIngredients")
                
                if (cleanIngredients.isEmpty()) {
                    _publicSearchResults.value = emptyList()
                    return@launch
                }

                // 2. Search for each ingredient individually and collect IDs
                val idCounts = mutableMapOf<String, Int>()
                
                coroutineScope {
                    cleanIngredients.map { ingredient ->
                        async {
                            try {
                                val response = api.getRecipesByIngredient(ingredient)
                                response.meals?.forEach { meal ->
                                    synchronized(idCounts) {
                                        idCounts[meal.idMeal] = idCounts.getOrDefault(meal.idMeal, 0) + 1
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("RecipeViewModel", "Failed to search for $ingredient: ${e.message}")
                            }
                        }
                    }.awaitAll()
                }

                Log.d("RecipeViewModel", "Found ${idCounts.size} total candidate recipes across all ingredients")

                // 3. Fetch details for the top recipes (those that matched the most user ingredients)
                val topIds = idCounts.entries
                    .sortedByDescending { it.value }
                    .take(20) // Increase to 20 for better variety
                    .map { it.key }

                val candidates = coroutineScope {
                    topIds.map { id ->
                        async {
                            try {
                                api.getRecipeDetails(id).meals?.firstOrNull()?.toRecipe()
                            } catch (e: Exception) { null }
                        }
                    }.awaitAll().filterNotNull()
                }

                // 4. Perform "Coverage Scoring": How many ingredients in the recipe does the user have?
                val userIngs = cleanIngredients.map { it.lowercase() }
                
                val rankedResults = candidates.map { recipe ->
                    val recipeIngs = recipe.ingredients?.map { it.lowercase() } ?: emptyList()
                    
                    var matches = 0
                    recipeIngs.forEach { ri ->
                        if (userIngs.any { ui -> ri.contains(ui) || ui.contains(ri) }) {
                            matches++
                        }
                    }
                    
                    // Score = Match Density (what % of recipe do we have) + Absolute Match Bonus
                    // This prioritizes simpler recipes where you have almost everything
                    val matchRatio = if (recipeIngs.isNotEmpty()) matches.toDouble() / recipeIngs.size else 0.0
                    val score = matchRatio + (matches * 0.2)
                    recipe to score
                }.sortedByDescending { it.second }
                .map { it.first }

                Log.d("RecipeViewModel", "Ranked ${rankedResults.size} recipes for the user")
                _publicSearchResults.value = rankedResults
                _aiGeneratedRecipe.value = rankedResults.firstOrNull()
                
            } catch (e: Exception) {
                Log.e("RecipeViewModel", "Advanced Fridge AI failed: ${e.message}")
                _errorMessage.value = "AI Search failed. Please check your connection."
                _publicSearchResults.value = emptyList()
            } finally {
                _isGeneratingAI.value = false
            }
        }
    }

    fun clearAIRecipe() {
        _aiGeneratedRecipe.value = null
    }
}
