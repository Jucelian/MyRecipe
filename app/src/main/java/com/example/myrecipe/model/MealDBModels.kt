package com.example.myrecipe.model

import com.google.gson.annotations.SerializedName

data class MealDBResponse(
    val meals: List<MealDetail>?
)

data class MealDetail(
    val idMeal: String,
    val strMeal: String,
    val strInstructions: String?,
    val strMealThumb: String?,
    val strYoutube: String?,
    val strCategory: String?,
    val strArea: String?,
    val strIngredient1: String?, val strIngredient2: String?, val strIngredient3: String?,
    val strIngredient4: String?, val strIngredient5: String?, val strIngredient6: String?,
    val strIngredient7: String?, val strIngredient8: String?, val strIngredient9: String?,
    val strIngredient10: String?, val strIngredient11: String?, val strIngredient12: String?,
    val strIngredient13: String?, val strIngredient14: String?, val strIngredient15: String?,
    val strIngredient16: String?, val strIngredient17: String?, val strIngredient18: String?,
    val strIngredient19: String?, val strIngredient20: String?
) {
    fun toRecipe(): Recipe {
        val ingredients = listOfNotNull(
            strIngredient1, strIngredient2, strIngredient3, strIngredient4, strIngredient5,
            strIngredient6, strIngredient7, strIngredient8, strIngredient9, strIngredient10,
            strIngredient11, strIngredient12, strIngredient13, strIngredient14, strIngredient15,
            strIngredient16, strIngredient17, strIngredient18, strIngredient19, strIngredient20
        ).filter { it.isNotBlank() }

        return Recipe(
            id = "mealdb_$idMeal",
            title = strMeal,
            description = "A delicious $strCategory dish from $strArea.",
            ingredients = ingredients,
            instructions = strInstructions?.split("\r\n")?.filter { it.isNotBlank() } ?: emptyList(),
            imageUri = android.net.Uri.parse(strMealThumb),
            videoUri = if (strYoutube.isNullOrBlank()) null else android.net.Uri.parse(strYoutube),
            rating = 4.8, // Public recipes get a high default rating
            category = strCategory ?: "General",
            owner = "Public"
        )
    }
}
