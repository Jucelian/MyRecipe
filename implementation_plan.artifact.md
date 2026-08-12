# Delete Category Confirmation Plan

Implement a confirmation dialog when a user attempts to delete a category that contains recipes. If the user confirms, both the category and its associated recipes will be deleted. If the user cancels, the operation is aborted.

## User Review Required

> [!IMPORTANT]
> The confirmation dialog will only appear if the category has recipes. If the category is empty, it will be deleted immediately as before.

## Proposed Changes

### [app] (:app)

#### [MODIFY] [RecipeRepository.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/data/RecipeRepository.kt)
- Update `deleteCategory` to accept a `deleteRecipes: Boolean` parameter.
- If `deleteRecipes` is true, delete all recipes in the category (locally and remotely).
- If `deleteRecipes` is false, keep the current behavior (moving recipes to "General").

#### [MODIFY] [RecipeViewModel.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/ui/RecipeViewModel.kt)
- Update `deleteCategory` to accept the new `deleteRecipes` parameter and pass it to the repository.

#### [MODIFY] [RecipeComponents.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/ui/RecipeComponents.kt)
- Add state variables `showDeleteConfirmationDialog` and `categoryToDelete` in `MyRecipesTab`.
- Create a `DeleteCategoryConfirmationDialog` Composable.
- Update `MyRecipesTab` to check for recipes in the category before initiating deletion.
- Show the confirmation dialog if recipes exist.
- Call `viewModel.deleteCategory(category, deleteRecipes = true)` if user confirms.

## Verification Plan

### Automated Tests
- N/A (Manual verification is more suitable for UI dialogs).

### Manual Verification
1. Open the app and go to the "My Recipes" tab.
2. Create a new category "Test Category".
3. Add a recipe to "Test Category".
4. Try to delete "Test Category".
5. Verify that a confirmation dialog appears warning that recipes will be deleted.
6. Click "Cancel" and verify the category and recipe are still there.
7. Try to delete "Test Category" again.
8. Click "Yes" and verify that both the category and the recipe are deleted.
9. Create another category "Empty Category".
10. Try to delete "Empty Category".
11. Verify it is deleted immediately without a popup.
