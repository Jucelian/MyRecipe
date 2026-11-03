package com.example.myrecipe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan

@Database(entities = [Recipe::class, Category::class, ShoppingItem::class, MealPlan::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recipe_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
