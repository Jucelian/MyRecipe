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

    fun clearError() {
        _errorMessage.value = null
    }

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
                val processedRecipe = if (recipe.imageUri != null && recipe.imageUri.scheme == "content") {
                    val internalUri = saveImageToInternalStorage(recipe.imageUri)
                    recipe.copy(imageUri = internalUri, owner = owner)
                } else {
                    recipe.copy(owner = owner)
                }
                
                // 1. Insert locally first
                repository.addRecipe(processedRecipe)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add recipe locally: ${e.message}"
            }
        }
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val owner = if (recipe.owner.isBlank()) currentOwner.value else recipe.owner
                val processedRecipe = if (recipe.imageUri != null && recipe.imageUri.scheme == "content") {
                    val internalUri = saveImageToInternalStorage(recipe.imageUri)
                    recipe.copy(imageUri = internalUri, owner = owner)
                } else {
                    recipe.copy(owner = owner)
                }
                
                // 1. Update locally first
                repository.updateRecipe(processedRecipe)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update recipe locally: ${e.message}"
            }
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
            try {
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

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(category)
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
}
