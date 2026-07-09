package com.example.myrecipe.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myrecipe.R
import com.example.myrecipe.model.Recipe
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeApp(
    viewModel: RecipeViewModel,
    authViewModel: AuthViewModel
) {
    val showAddDialogState = remember { mutableStateOf(false) }
    val searchQueryState = remember { mutableStateOf("") }
    val selectedTabState = remember { mutableIntStateOf(0) }
    val currentUser = authViewModel.currentUser.value

    LaunchedEffect(currentUser) {
        currentUser?.let {
            viewModel.setCurrentOwner(it.username)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selectedTabState.intValue != 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchBar(
                        query = searchQueryState.value,
                        onQueryChange = { searchQueryState.value = it },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { authViewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTabState.intValue == 0,
                    onClick = { selectedTabState.intValue = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTabState.intValue == 1,
                    onClick = { selectedTabState.intValue = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Explore") },
                    label = { Text("Explore") }
                )
                NavigationBarItem(
                    selected = selectedTabState.intValue == 2,
                    onClick = { selectedTabState.intValue = 2 },
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "My Recipes") },
                    label = { Text("My Recipes") }
                )
                NavigationBarItem(
                    selected = selectedTabState.intValue == 3,
                    onClick = { selectedTabState.intValue = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTabState.intValue != 3) {
                FloatingActionButton(onClick = { showAddDialogState.value = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Recipe")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val tab = selectedTabState.intValue
            if (tab == 0) HomeScreen(viewModel, authViewModel)
            else if (tab == 1) ExploreScreen(viewModel, searchQueryState.value)
            else if (tab == 2) MyRecipesTab(viewModel, currentUser?.username ?: "")
            else if (tab == 3) ProfileScreen(authViewModel)
        }

        if (showAddDialogState.value) {
            AddRecipeDialog(
                viewModel = viewModel,
                onDismiss = { showAddDialogState.value = false },
                onRecipeAdded = { recipe ->
                    viewModel.addRecipe(recipe.copy(owner = currentUser?.username ?: ""))
                    showAddDialogState.value = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: RecipeViewModel, authViewModel: AuthViewModel) {
    val selectedRecipeState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToEditState = remember { mutableStateOf<Recipe?>(null) }
    
    val recipesState = viewModel.recipes.collectAsState()
    val isRefreshingState = viewModel.isRefreshing.collectAsState()
    
    val recipes = recipesState.value
    val isRefreshing = isRefreshingState.value
    
    val currentUser = authViewModel.currentUser.value
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        currentUser?.let {
            viewModel.setCurrentOwner(it.username)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Recently Added Meals",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                val recentRecipes = recipes.reversed().take(5)
                if (recentRecipes.isEmpty()) {
                    Text("No recipes yet. Add your first meal!", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray)
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentRecipes) { recipe ->
                            RecipeCard(recipe = recipe, onClick = { selectedRecipeState.value = recipe })
                        }
                    }
                }
            }

            val favoriteRecipes = recipes.filter { it.isFavorite }
            if (favoriteRecipes.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(favoriteRecipes) { recipe ->
                            RecipeCard(recipe = recipe, onClick = { selectedRecipeState.value = recipe })
                        }
                    }
                }
            }

            item {
                Text(
                    text = "All Recipes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (recipes.isEmpty()) {
                item {
                    Text("Your collection is empty.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                }
            } else {
                items(recipes.reversed()) { recipe ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        RecipeItemRow(
                            recipe = recipe,
                            onDelete = { viewModel.deleteRecipe(recipe) },
                            onClick = { selectedRecipeState.value = recipe }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        
        IconButton(
            onClick = { 
                currentUser?.let { 
                    viewModel.refreshData(it.username)
                    Toast.makeText(context, "Refreshing data...", Toast.LENGTH_SHORT).show()
                } 
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    selectedRecipeState.value?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            onDismiss = { selectedRecipeState.value = null },
            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
            onEdit = {
                recipeToEditState.value = recipe
                selectedRecipeState.value = null
            }
        )
    }

    recipeToEditState.value?.let { recipe ->
        EditRecipeDialog(
            recipe = recipe,
            viewModel = viewModel,
            onDismiss = { recipeToEditState.value = null },
            onRecipeUpdated = { updatedRecipe ->
                viewModel.updateRecipe(updatedRecipe)
                recipeToEditState.value = null
            }
        )
    }
}

@Composable
fun ExploreScreen(viewModel: RecipeViewModel, query: String) {
    val selectedRecipeState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToEditState = remember { mutableStateOf<Recipe?>(null) }
    val recipesState = viewModel.recipes.collectAsState()
    val recipes = recipesState.value
    
    val filteredRecipes = if (query.isEmpty()) {
        recipes
    } else {
        recipes.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (query.isEmpty()) "All Recipes" else "Search Results",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        if (filteredRecipes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isEmpty()) "No recipes added yet." else "No recipes match your search.",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredRecipes) { recipe ->
                    RecipeItemRow(
                        recipe = recipe,
                        onDelete = { viewModel.deleteRecipe(recipe) },
                        onClick = { selectedRecipeState.value = recipe }
                    )
                }
            }
        }
    }

    selectedRecipeState.value?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            onDismiss = { selectedRecipeState.value = null },
            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
            onEdit = {
                recipeToEditState.value = recipe
                selectedRecipeState.value = null
            }
        )
    }

    recipeToEditState.value?.let { recipe ->
        EditRecipeDialog(
            recipe = recipe,
            viewModel = viewModel,
            onDismiss = { recipeToEditState.value = null },
            onRecipeUpdated = { updatedRecipe ->
                viewModel.updateRecipe(updatedRecipe)
                recipeToEditState.value = null
            }
        )
    }
}

@Composable
fun MyRecipesTab(viewModel: RecipeViewModel, owner: String) {
    val selectedCategoryState = remember { mutableStateOf<String?>(null) }
    val showAddCategoryDialogState = remember { mutableStateOf(false) }
    
    val recipesState = viewModel.recipes.collectAsState()
    val categoriesState = viewModel.categories.collectAsState()
    
    val recipes = recipesState.value
    val categories = categoriesState.value

    if (selectedCategoryState.value == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Categories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAddCategoryDialogState.value = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Category")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category.name,
                        onClick = { selectedCategoryState.value = category.name },
                        onDelete = { viewModel.deleteCategory(category) }
                    )
                }
            }
        }
    } else {
        val selectedRecipeForDetailState = remember { mutableStateOf<Recipe?>(null) }
        val recipeToEditState = remember { mutableStateOf<Recipe?>(null) }

        CategoryDetailScreen(
            categoryName = selectedCategoryState.value!!,
            recipes = recipes.filter { it.category == selectedCategoryState.value },
            onBack = { selectedCategoryState.value = null },
            onDeleteRecipe = { recipe -> viewModel.deleteRecipe(recipe) },
            onRecipeClick = { recipe -> selectedRecipeForDetailState.value = recipe }
        )

        selectedRecipeForDetailState.value?.let { recipe ->
            RecipeDetailDialog(
                recipe = recipe,
                onDismiss = { selectedRecipeForDetailState.value = null },
                onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                onEdit = {
                    recipeToEditState.value = recipe
                    selectedRecipeForDetailState.value = null
                }
            )
        }

        recipeToEditState.value?.let { recipe ->
            EditRecipeDialog(
                recipe = recipe,
                viewModel = viewModel,
                onDismiss = { recipeToEditState.value = null },
                onRecipeUpdated = { updatedRecipe ->
                    viewModel.updateRecipe(updatedRecipe)
                    recipeToEditState.value = null
                }
            )
        }
    }

    if (showAddCategoryDialogState.value) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialogState.value = false },
            onCategoryAdded = { name ->
                viewModel.addCategory(name, owner)
                showAddCategoryDialogState.value = false
            }
        )
    }
}

@Composable
fun CategoryItem(category: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun CategoryDetailScreen(categoryName: String, recipes: List<Recipe>, onBack: () -> Unit, onDeleteRecipe: (Recipe) -> Unit, onRecipeClick: (Recipe) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = categoryName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        if (recipes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recipes in this category yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(recipes) { recipe ->
                    RecipeItemRow(recipe = recipe, onDelete = { onDeleteRecipe(recipe) }, onClick = { onRecipeClick(recipe) })
                }
            }
        }
    }
}

@Composable
fun RecipeItemRow(recipe: Recipe, onDelete: () -> Unit, onClick: () -> Unit) {
    val expandedState = remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = recipe.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = recipe.description, style = MaterialTheme.typography.bodySmall, maxLines = if (expandedState.value) Int.MAX_VALUE else 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                        Text(text = " ${recipe.rating}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Recipe", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expandedState.value) {
                recipe.ingredients.let { ingredientsList ->
                    if (ingredientsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Ingredients:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        ingredientsList.forEachIndexed { index, ingredient ->
                            Text(
                                text = "${index + 1}. $ingredient",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    }
                }

                recipe.instructions.let { instructionsList ->
                    if (instructionsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Instructions:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        instructionsList.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onCategoryAdded: (String) -> Unit) {
    val categoryNameState = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            TextField(
                value = categoryNameState.value,
                onValueChange = { categoryNameState.value = it },
                label = { Text("Category Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (categoryNameState.value.isNotBlank()) onCategoryAdded(categoryNameState.value) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search recipes...") },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chefmate_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
            focusedContainerColor = Color.White
        )
    )
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val cardColors = listOf(
        MaterialTheme.colorScheme.secondary,
        Color(0xFF8C7D70), 
        Color(0xFFC2A479), 
        Color(0xFF564335)  
    )
    val bgColor = cardColors[recipe.title.length % cardColors.size]

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(320.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column {
            Box(modifier = Modifier.height(180.dp)) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = recipe.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = recipe.rating.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    recipe.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailDialog(recipe: Recipe, onDismiss: () -> Unit, onToggleFavorite: () -> Unit, onEdit: () -> Unit) {
    val isFavoriteState = remember { mutableStateOf(recipe.isFavorite) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = recipe.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Recipe", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    isFavoriteState.value = !isFavoriteState.value
                    onToggleFavorite()
                }) {
                    Icon(
                        imageVector = if (isFavoriteState.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavoriteState.value) Color.Red else Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = recipe.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(text = recipe.description, style = MaterialTheme.typography.bodyMedium)

                recipe.ingredients.let { ingredientsList ->
                    if (ingredientsList.isNotEmpty()) {
                        Text(text = "Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ingredientsList.forEach { ingredient ->
                            Text(text = "• $ingredient", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                recipe.instructions.let { instructionsList ->
                    if (instructionsList.isNotEmpty()) {
                        Text(text = "Instructions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        instructionsList.forEachIndexed { index, step ->
                            Text(text = "${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeDialog(recipe: Recipe, viewModel: RecipeViewModel, onDismiss: () -> Unit, onRecipeUpdated: (Recipe) -> Unit) {
    val titleState = remember { mutableStateOf(recipe.title) }
    val descriptionState = remember { mutableStateOf(recipe.description) }
    val ingredientsState = remember { mutableStateOf(recipe.ingredients.joinToString("\n")) }
    val instructionsState = remember { mutableStateOf(recipe.instructions.joinToString("\n")) }
    val ratingState = remember { mutableStateOf(recipe.rating.toString()) }
    val tagsState = remember { mutableStateOf(recipe.tags.joinToString(", ")) }
    val selectedCategoryState = remember { mutableStateOf(recipe.category) }
    val imageUriState = remember { mutableStateOf<Uri?>(recipe.imageUri) }
    val currentTempUriState = remember { mutableStateOf<Uri?>(null) }
    val expandedState = remember { mutableStateOf(false) }
    val categoriesState = viewModel.categories.collectAsState()
    val categories = categoriesState.value

    LaunchedEffect(categories) {
        if (selectedCategoryState.value.isBlank() && categories.isNotEmpty()) {
            selectedCategoryState.value = categories.firstOrNull()?.name ?: "General"
        }
    }

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) imageUriState.value = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUriState.value = currentTempUriState.value
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted.", Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recipe") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = titleState.value,
                    onValueChange = { titleState.value = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expandedState.value,
                    onExpandedChange = { expandedState.value = !expandedState.value }
                ) {
                    TextField(
                        value = selectedCategoryState.value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState.value) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedState.value,
                        onDismissRequest = { expandedState.value = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryState.value = category.name
                                    expandedState.value = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Select Image")
                    }
                    Button(
                        onClick = { 
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    val directory = File(context.filesDir, "images")
                                    if (!directory.exists()) directory.mkdirs()
                                    val file = File(directory, "temp_camera_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "com.example.myrecipe.fileprovider", file)
                                    currentTempUriState.value = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Take Photo")
                    }
                }

                imageUriState.value?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                TextField(
                    value = descriptionState.value,
                    onValueChange = { descriptionState.value = it },
                    label = { Text("Description") }
                )
                TextField(
                    value = ingredientsState.value,
                    onValueChange = { ingredientsState.value = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = instructionsState.value,
                    onValueChange = { instructionsState.value = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = ratingState.value,
                    onValueChange = { ratingState.value = it },
                    label = { Text("Rating (e.g. 4.8)") }
                )
                TextField(
                    value = tagsState.value,
                    onValueChange = { tagsState.value = it },
                    label = { Text("Tags (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleState.value.isNotBlank()) {
                        onRecipeUpdated(
                            recipe.copy(
                                title = titleState.value,
                                description = descriptionState.value,
                                ingredients = ingredientsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructionsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUriState.value,
                                rating = ratingState.value.toDoubleOrNull() ?: 0.0,
                                tags = tagsState.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategoryState.value
                            )
                        )
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeDialog(viewModel: RecipeViewModel, onDismiss: () -> Unit, onRecipeAdded: (Recipe) -> Unit) {
    val titleState = remember { mutableStateOf("") }
    val descriptionState = remember { mutableStateOf("") }
    val ingredientsState = remember { mutableStateOf("") }
    val instructionsState = remember { mutableStateOf("") }
    val ratingState = remember { mutableStateOf("4.5") }
    val tagsState = remember { mutableStateOf("") }
    val categoriesState = viewModel.categories.collectAsState()
    val categories = categoriesState.value
    val selectedCategoryState = remember { mutableStateOf("General") }
    val imageUriState = remember { mutableStateOf<Uri?>(null) }
    val currentTempUriState = remember { mutableStateOf<Uri?>(null) }
    val expandedState = remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        if (selectedCategoryState.value == "General" && categories.isNotEmpty()) {
            selectedCategoryState.value = categories.first().name
        }
    }

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) imageUriState.value = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUriState.value = currentTempUriState.value
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted.", Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Recipe") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = titleState.value,
                    onValueChange = { titleState.value = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expandedState.value,
                    onExpandedChange = { expandedState.value = !expandedState.value }
                ) {
                    TextField(
                        value = selectedCategoryState.value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState.value) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedState.value,
                        onDismissRequest = { expandedState.value = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryState.value = category.name
                                    expandedState.value = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Select Image")
                    }
                    Button(
                        onClick = { 
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    val directory = File(context.filesDir, "images")
                                    if (!directory.exists()) directory.mkdirs()
                                    val file = File(directory, "temp_camera_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "com.example.myrecipe.fileprovider", file)
                                    currentTempUriState.value = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Take Photo")
                    }
                }

                imageUriState.value?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                TextField(
                    value = descriptionState.value,
                    onValueChange = { descriptionState.value = it },
                    label = { Text("Description") }
                )
                TextField(
                    value = ingredientsState.value,
                    onValueChange = { ingredientsState.value = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = instructionsState.value,
                    onValueChange = { instructionsState.value = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = ratingState.value,
                    onValueChange = { ratingState.value = it },
                    label = { Text("Rating (e.g. 4.8)") }
                )
                TextField(
                    value = tagsState.value,
                    onValueChange = { tagsState.value = it },
                    label = { Text("Tags (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleState.value.isNotBlank()) {
                        onRecipeAdded(
                            Recipe(
                                title = titleState.value,
                                description = descriptionState.value,
                                ingredients = ingredientsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructionsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUriState.value,
                                rating = ratingState.value.toDoubleOrNull() ?: 0.0,
                                tags = tagsState.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategoryState.value
                            )
                        )
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
