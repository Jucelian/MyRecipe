package com.example.myrecipe.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

import com.google.gson.annotations.SerializedName

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val ingredients: List<String>? = emptyList(),
    val instructions: List<String>? = emptyList(),
    @SerializedName("imageUri")
    val imageUri: Uri? = null,
    @SerializedName("videoUri")
    val videoUri: Uri? = null,
    val rating: Double? = 0.0,
    val tags: List<String>? = emptyList(),
    val category: String? = "General",
    val isFavorite: Boolean = false,
    val isSynced: Boolean = false,
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPublic: Boolean = false
)
