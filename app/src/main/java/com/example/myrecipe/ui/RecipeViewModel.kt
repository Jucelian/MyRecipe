package com.example.myrecipe.ui

import android.app.Application
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
import kotlinx.coroutines.launch

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = RecipeRepository(
        database.recipeDao(),
        database.categoryDao(),
        RetrofitClient.instance
    )

    val recipes: StateFlow<List<Recipe>> = repository.allRecipes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshData(owner: String) {
        viewModelScope.launch {
            repository.refreshData(owner)
        }
    }

    fun addRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.addRecipe(recipe)
        }
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.updateRecipe(recipe)
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
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
