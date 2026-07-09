package com.example.myrecipe.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.data.AppDatabase
import com.example.myrecipe.data.RecipeRepository
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
        currentOwner.value = owner
        if (owner.isNotBlank()) {
            refreshData(owner)
        }
    }
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        startAutoSync()
        startAutoRefresh()
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
                    repository.syncPendingChanges(owner)
                }
            }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60000) // Auto-refresh every minute
                if (currentOwner.value.isNotBlank()) {
                    repository.refreshData(currentOwner.value)
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recipes: StateFlow<List<Recipe>> = currentOwner.flatMapLatest { owner ->
        repository.getRecipes(owner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<Category>> = combine(
        currentOwner.flatMapLatest { owner -> repository.getCategories(owner) },
        recipes
    ) { dbCategories, allRecipes ->
        // Extract unique category names from existing recipes
        val recipeCategoryNames = allRecipes.map { it.category }
            .filter { it.isNotBlank() }
            .distinct()

        // Get names of categories already in the DB
        val dbCategoryNames = dbCategories.map { it.name }.toSet()

        // Create virtual categories for any recipe category not in the DB
        val additionalCategories = recipeCategoryNames
            .filter { it !in dbCategoryNames }
            .map { Category(name = it, owner = currentOwner.value) }

        // Combine and sort
        (dbCategories + additionalCategories).distinctBy { it.name }.sortedBy { it.name }
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
            // Fetch latest from server (which also pushes local changes)
            repository.refreshData(owner)
            _isRefreshing.value = false
        }
    }

    fun addRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val owner = if (recipe.owner.isBlank()) currentOwner.value else recipe.owner
            val processedRecipe = if (recipe.imageUri != null && recipe.imageUri.scheme == "content") {
                saveImageToInternalStorage(recipe.imageUri)?.let { internalUri ->
                    recipe.copy(imageUri = internalUri, owner = owner)
                } ?: recipe.copy(owner = owner)
            } else {
                recipe.copy(owner = owner)
            }
            repository.addRecipe(processedRecipe)
        }
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val owner = if (recipe.owner.isBlank()) currentOwner.value else recipe.owner
            val processedRecipe = if (recipe.imageUri != null && recipe.imageUri.scheme == "content") {
                saveImageToInternalStorage(recipe.imageUri)?.let { internalUri ->
                    recipe.copy(imageUri = internalUri, owner = owner)
                } ?: recipe.copy(owner = owner)
            } else {
                recipe.copy(owner = owner)
            }
            repository.updateRecipe(processedRecipe)
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): Uri? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val directory = File(context.filesDir, "recipe_images")
            if (!directory.exists()) directory.mkdirs()

            val fileName = "img_${System.currentTimeMillis()}.jpg"
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
            // Delete local image file if it exists
            recipe.imageUri?.let { uri ->
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
        }
    }

    fun addCategory(name: String, owner: String) {
        viewModelScope.launch {
            repository.addCategory(Category(name = name, owner = owner))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            repository.updateRecipe(recipe.copy(isFavorite = !recipe.isFavorite))
        }
    }
}
