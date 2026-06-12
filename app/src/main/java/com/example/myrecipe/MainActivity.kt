package com.example.myrecipe

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.ui.AuthViewModel
import com.example.myrecipe.ui.LoginScreen
import com.example.myrecipe.ui.ProfileScreen
import com.example.myrecipe.ui.RecipeViewModel
import com.example.myrecipe.ui.theme.MyRecipeTheme
import java.io.File
import java.util.Objects

class MainActivity : ComponentActivity() {
    private var authViewModel: AuthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyRecipeTheme {
                val viewModel: AuthViewModel = viewModel()
                val recipeViewModel: RecipeViewModel = viewModel()
                authViewModel = viewModel
                if (viewModel.isLoggedIn.value) {
                    val user = viewModel.currentUser.value
                    LaunchedEffect(user) {
                        user?.let {
                            recipeViewModel.refreshData(it.username)
                        }
                    }
                    RecipeApp(viewModel = recipeViewModel, authViewModel = viewModel)
                } else {
                    LoginScreen(authViewModel = viewModel, onLoginSuccess = { })
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        authViewModel?.resetInactivityTimer()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeApp(
    viewModel: RecipeViewModel = viewModel(),
    authViewModel: AuthViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentUser = authViewModel.currentUser.value

    LaunchedEffect(currentUser) {
        currentUser?.let {
            viewModel.refreshData(it.username)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selectedTab != 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
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
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Explore") },
                    label = { Text("Explore") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "My Recipes") },
                    label = { Text("My Recipes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab != 3) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Recipe")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel, authViewModel)
                1 -> ExploreScreen(viewModel, searchQuery)
                2 -> MyRecipesTab(viewModel, currentUser?.username ?: "")
                3 -> ProfileScreen(authViewModel)
            }
        }

        if (showAddDialog) {
            AddRecipeDialog(
                viewModel = viewModel,
                onDismiss = { showAddDialog = false },
                onRecipeAdded = { recipe ->
                    viewModel.addRecipe(recipe.copy(owner = currentUser?.username ?: ""))
                    showAddDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: RecipeViewModel, authViewModel: AuthViewModel) {
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeToEdit by remember { mutableStateOf<Recipe?>(null) }
    val recipes by viewModel.recipes.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currentUser = authViewModel.currentUser.value
    val context = LocalContext.current

    // Initial refresh when screen opens
    LaunchedEffect(Unit) {
        currentUser?.let {
            viewModel.refreshData(it.username)
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
                // Show the 5 most recent recipes regardless of category
                val recentRecipes = recipes.reversed().take(5)
                if (recentRecipes.isEmpty()) {
                    Text("No recipes yet. Add your first meal!", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray)
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentRecipes) { recipe ->
                            RecipeCard(recipe = recipe, onClick = { selectedRecipe = recipe })
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
                            RecipeCard(recipe = recipe, onClick = { selectedRecipe = recipe })
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
                // Show all recipes in a list
                items(recipes.reversed()) { recipe ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        RecipeItemRow(
                            recipe = recipe,
                            onDelete = { viewModel.deleteRecipe(recipe) },
                            onClick = { selectedRecipe = recipe }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        
        // Manual Refresh Button (Simpler alternative to SwipeRefresh for stability)
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

    selectedRecipe?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            onDismiss = { selectedRecipe = null },
            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
            onEdit = {
                recipeToEdit = recipe
                selectedRecipe = null
            }
        )
    }

    recipeToEdit?.let { recipe ->
        EditRecipeDialog(
            recipe = recipe,
            viewModel = viewModel,
            onDismiss = { recipeToEdit = null },
            onRecipeUpdated = { updatedRecipe ->
                viewModel.updateRecipe(updatedRecipe)
                recipeToEdit = null
            }
        )
    }
}

@Composable
fun ExploreScreen(viewModel: RecipeViewModel, query: String) {
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeToEdit by remember { mutableStateOf<Recipe?>(null) }
    val recipes by viewModel.recipes.collectAsState()
    
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
                        onClick = { selectedRecipe = recipe }
                    )
                }
            }
        }
    }

    selectedRecipe?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            onDismiss = { selectedRecipe = null },
            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
            onEdit = {
                recipeToEdit = recipe
                selectedRecipe = null
            }
        )
    }

    recipeToEdit?.let { recipe ->
        EditRecipeDialog(
            recipe = recipe,
            viewModel = viewModel,
            onDismiss = { recipeToEdit = null },
            onRecipeUpdated = { updatedRecipe ->
                viewModel.updateRecipe(updatedRecipe)
                recipeToEdit = null
            }
        )
    }
}

@Composable
fun MyRecipesTab(viewModel: RecipeViewModel, owner: String) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    val recipes by viewModel.recipes.collectAsState()
    val categories by viewModel.categories.collectAsState()

    if (selectedCategory == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Categories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAddCategoryDialog = true }) {
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
                        onClick = { selectedCategory = category.name },
                        onDelete = { viewModel.deleteCategory(category) }
                    )
                }
            }
        }
    } else {
        var selectedRecipeForDetail by remember { mutableStateOf<Recipe?>(null) }
        var recipeToEdit by remember { mutableStateOf<Recipe?>(null) }

        CategoryDetailScreen(
            categoryName = selectedCategory!!,
            recipes = recipes.filter { it.category == selectedCategory },
            onBack = { selectedCategory = null },
            onDeleteRecipe = { recipe -> viewModel.deleteRecipe(recipe) },
            onRecipeClick = { recipe -> selectedRecipeForDetail = recipe }
        )

        selectedRecipeForDetail?.let { recipe ->
            RecipeDetailDialog(
                recipe = recipe,
                onDismiss = { selectedRecipeForDetail = null },
                onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                onEdit = {
                    recipeToEdit = recipe
                    selectedRecipeForDetail = null
                }
            )
        }

        recipeToEdit?.let { recipe ->
            EditRecipeDialog(
                recipe = recipe,
                viewModel = viewModel,
                onDismiss = { recipeToEdit = null },
                onRecipeUpdated = { updatedRecipe ->
                    viewModel.updateRecipe(updatedRecipe)
                    recipeToEdit = null
                }
            )
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onCategoryAdded = { name ->
                viewModel.addCategory(name, owner)
                showAddCategoryDialog = false
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
    var expanded by remember { mutableStateOf(false) }

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
                    Text(text = recipe.description, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                        Text(text = " ${recipe.rating}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Recipe", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expanded) {
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
    var categoryName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            TextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text("Category Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (categoryName.isNotBlank()) onCategoryAdded(categoryName) }) {
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
    // Earthy card colors derived from the palette
    val cardColors = listOf(
        MaterialTheme.colorScheme.secondary,
        Color(0xFF8C7D70), // Tertiary brown
        Color(0xFFC2A479), // Primary tan
        Color(0xFF564335)  // Secondary brown
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
                
                // Rating tag
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
    var isFavorite by remember { mutableStateOf(recipe.isFavorite) }

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
                    isFavorite = !isFavorite
                    onToggleFavorite()
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else Color.Gray
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
    var title by remember { mutableStateOf(recipe.title) }
    var description by remember { mutableStateOf(recipe.description) }
    var ingredients by remember { mutableStateOf(recipe.ingredients.joinToString("\n")) }
    var instructions by remember { mutableStateOf(recipe.instructions.joinToString("\n")) }
    var rating by remember { mutableStateOf(recipe.rating.toString()) }
    var tags by remember { mutableStateOf(recipe.tags.joinToString(", ")) }
    var selectedCategory by remember { mutableStateOf(recipe.category) }
    var imageUri by remember { mutableStateOf<Uri?>(recipe.imageUri) }
    var currentTempUri by remember { mutableStateOf<Uri?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val categories by viewModel.categories.collectAsState()

    // Ensure category selection stays valid if categories load late
    LaunchedEffect(categories) {
        if (selectedCategory.isBlank() && categories.isNotEmpty()) {
            selectedCategory = categories.firstOrNull()?.name ?: "General"
        }
    }

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) imageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUri = currentTempUri
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted. Please click Take Photo again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
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
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category.name
                                    expanded = false
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
                                    currentTempUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
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

                imageUri?.let {
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                TextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = rating,
                    onValueChange = { rating = it },
                    label = { Text("Rating (e.g. 4.8)") }
                )
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onRecipeUpdated(
                            recipe.copy(
                                title = title,
                                description = description,
                                ingredients = ingredients.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructions.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUri,
                                rating = rating.toDoubleOrNull() ?: 0.0,
                                tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategory
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
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("4.5") }
    var tags by remember { mutableStateOf("") }
    val categories by viewModel.categories.collectAsState()
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "General") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var currentTempUri by remember { mutableStateOf<Uri?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Update selection once categories load from DB
    LaunchedEffect(categories) {
        if (selectedCategory == "General" && categories.isNotEmpty()) {
            selectedCategory = categories.first().name
        }
    }

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) imageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUri = currentTempUri
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted. Please click Take Photo again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
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
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category.name
                                    expanded = false
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
                                    currentTempUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
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

                imageUri?.let {
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                TextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3
                )
                TextField(
                    value = rating,
                    onValueChange = { rating = it },
                    label = { Text("Rating (e.g. 4.8)") }
                )
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onRecipeAdded(
                            Recipe(
                                title = title,
                                description = description,
                                ingredients = ingredients.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructions.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUri,
                                rating = rating.toDoubleOrNull() ?: 0.0,
                                tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategory
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
