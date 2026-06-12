package com.example.myrecipe.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val imageUri: Uri? = null,
    val rating: Double = 0.0,
    val tags: List<String> = emptyList(),
    val category: String = "General",
    val isFavorite: Boolean = false,
    val isSynced: Boolean = false,
    val owner: String = ""
)
