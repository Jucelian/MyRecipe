package com.example.myrecipe.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "meal_plans")
data class MealPlan(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val recipeId: String, // Reference to local recipe ID or "mealdb_ID"
    val recipeTitle: String,
    val date: Long, // Midnight timestamp
    val mealType: String, // Breakfast, Lunch, Dinner, Snack
    val owner: String = "",
    val isSynced: Boolean = false
)
