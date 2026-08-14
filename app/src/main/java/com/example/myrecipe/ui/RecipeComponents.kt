package com.example.myrecipe.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myrecipe.R
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.Category
import java.io.File
import java.util.Calendar

@Composable
fun AutoScrollingRecipeRow(
    meals: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit
) {
    if (meals.isEmpty()) return
    
    // We use a large number of pages to simulate an infinite loop
    val loopCount = 1000
    val totalPages = meals.size * loopCount
    val initialPage = (meals.size * loopCount) / 2
    val pagerState = rememberPagerState(pageCount = { totalPages }, initialPage = initialPage)
    
    // Auto-scroll effect
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000) // Scroll every 4 seconds
            if (!pagerState.isScrollInProgress) {
                val nextPage = pagerState.currentPage + 1
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 40.dp),
        pageSpacing = 16.dp,
        modifier = Modifier.fillMaxWidth().height(310.dp)
    ) { page ->
        val actualIndex = page % meals.size
        val meal = meals[actualIndex]
        
        RecipeCard(
            recipe = meal,
            onClick = { onRecipeClick(meal) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun VideoPlayer(videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeApp(
    viewModel: RecipeViewModel,
    authViewModel: AuthViewModel
) {
    val showAddDialogState = remember { mutableStateOf(false) }
    val activeCategoryState = remember { mutableStateOf<String?>(null) }
    val searchQueryState = remember { mutableStateOf("") }
    val selectedTabState = remember { mutableIntStateOf(0) }
    val currentUser = authViewModel.currentUser.value
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentUser?.let {
                    viewModel.refreshData(it.username)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(currentUser) {
        currentUser?.let {
            viewModel.setCurrentOwner(it.username)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = selectedTabState.intValue != 3,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchBar(
                        query = searchQueryState.value,
                        onQueryChange = { searchQueryState.value = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { authViewModel.logout() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTabState.intValue == 0,
                    onClick = { 
                        selectedTabState.intValue = 0
                        activeCategoryState.value = null
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTabState.intValue == 1,
                    onClick = { 
                        selectedTabState.intValue = 1
                        activeCategoryState.value = null
                    },
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
                    onClick = { 
                        selectedTabState.intValue = 3
                        activeCategoryState.value = null
                    },
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
            AnimatedContent(
                targetState = selectedTabState.intValue,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(viewModel, authViewModel)
                    1 -> ExploreScreen(viewModel, searchQueryState.value)
                    2 -> MyRecipesTab(viewModel, currentUser?.username ?: "", onCategorySelected = { activeCategoryState.value = it })
                    3 -> ProfileScreen(authViewModel, viewModel)
                }
            }
        }

        if (showAddDialogState.value) {
            AddRecipeDialog(
                viewModel = viewModel,
                initialCategory = activeCategoryState.value,
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
    val recipeToDeleteState = remember { mutableStateOf<Recipe?>(null) }
    
    val recipesState = viewModel.recipes.collectAsState()
    val isRefreshingState = viewModel.isRefreshing.collectAsState()
    val dailyRecipesState = viewModel.dailyRecipes.collectAsState()
    
    val recipes = recipesState.value
    val isRefreshing = isRefreshingState.value
    val dailyRecipes = dailyRecipesState.value
    
    val isDailyPicksExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("daily_picks")) }
    val isRecentMealsExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("recent_meals")) }
    val isFavoritesExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("favorites")) }
    val isExploreAllExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("explore_all")) }
    
    val currentUser = authViewModel.currentUser.value

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        else -> "Good Evening"
    }

    LaunchedEffect(Unit) {
        currentUser?.let {
            viewModel.setCurrentOwner(it.username)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { 
            currentUser?.let { viewModel.refreshData(it.username) }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (dailyRecipes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isDailyPicksExpanded.value = !isDailyPicksExpanded.value
                                authViewModel.setSectionExpanded("daily_picks", isDailyPicksExpanded.value)
                            }
                            .padding(start = 20.dp, top = 24.dp, bottom = 8.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Daily Chef's Picks",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Today's Inspiration",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Icon(
                            imageVector = if (isDailyPicksExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDailyPicksExpanded.value) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (isDailyPicksExpanded.value) {
                    dailyRecipes.forEach { (category, meals) ->
                        item {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        item {
                            AutoScrollingRecipeRow(
                                meals = meals,
                                onRecipeClick = { selectedRecipeState.value = it }
                            )
                        }
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            isRecentMealsExpanded.value = !isRecentMealsExpanded.value
                            authViewModel.setSectionExpanded("recent_meals", isRecentMealsExpanded.value)
                        }
                        .padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$greeting, Chef!",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Recently Added Meals",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Icon(
                        imageVector = if (isRecentMealsExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isRecentMealsExpanded.value) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isRecentMealsExpanded.value) {
                item {
                    val recentRecipes = recipes.reversed().take(5)
                    if (recentRecipes.isEmpty()) {
                        Text("No recipes yet. Add your first meal!", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray)
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recentRecipes, key = { it.id }) { recipe ->
                                RecipeCard(
                                    recipe = recipe,
                                    onClick = { selectedRecipeState.value = recipe },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            val favoriteRecipes = recipes.filter { it.isFavorite }
            if (favoriteRecipes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isFavoritesExpanded.value = !isFavoritesExpanded.value
                                authViewModel.setSectionExpanded("favorites", isFavoritesExpanded.value)
                            }
                            .padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your Favorites",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = if (isFavoritesExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isFavoritesExpanded.value) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isFavoritesExpanded.value) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(favoriteRecipes, key = { it.id }) { recipe ->
                                RecipeCard(
                                    recipe = recipe,
                                    onClick = { selectedRecipeState.value = recipe },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            isExploreAllExpanded.value = !isExploreAllExpanded.value
                            authViewModel.setSectionExpanded("explore_all", isExploreAllExpanded.value)
                        }
                        .padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Explore All",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = if (isExploreAllExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExploreAllExpanded.value) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExploreAllExpanded.value) {
                if (recipes.isEmpty() && !isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Your collection is empty.", color = Color.Gray)
                        }
                    }
                } else {
                    items(recipes.reversed(), key = { it.id }) { recipe ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateItem()) {
                            RecipeItemRow(
                                recipe = recipe,
                                onDelete = { recipeToDeleteState.value = recipe },
                                onClick = { selectedRecipeState.value = recipe }
                            )
                        }
                    }
                }
            }
        }
    }

    recipeToDeleteState.value?.let { recipe ->
        DeleteRecipeConfirmationDialog(
            recipeName = recipe.title,
            onConfirm = {
                viewModel.deleteRecipe(recipe)
                recipeToDeleteState.value = null
            },
            onDismiss = { recipeToDeleteState.value = null }
        )
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
    val recipeToDeleteState = remember { mutableStateOf<Recipe?>(null) }
    val recipesState = viewModel.recipes.collectAsState()
    val recipes = recipesState.value
    
    val filteredRecipes = if (query.isEmpty()) {
        recipes
    } else {
        recipes.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true ||
                    it.tags?.any { tag -> tag.contains(query, ignoreCase = true) } == true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (query.isEmpty()) "Discover Recipes" else "Search Results",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            color = MaterialTheme.colorScheme.onBackground
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
                        onDelete = { recipeToDeleteState.value = recipe },
                        onClick = { selectedRecipeState.value = recipe }
                    )
                }
            }
        }
    }

    recipeToDeleteState.value?.let { recipe ->
        DeleteRecipeConfirmationDialog(
            recipeName = recipe.title,
            onConfirm = {
                viewModel.deleteRecipe(recipe)
                recipeToDeleteState.value = null
            },
            onDismiss = { recipeToDeleteState.value = null }
        )
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
fun MyRecipesTab(viewModel: RecipeViewModel, owner: String, onCategorySelected: (String?) -> Unit = {}) {
    val selectedCategoryState = remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(selectedCategoryState.value) {
        onCategorySelected(selectedCategoryState.value)
    }
    
    val showAddCategoryDialogState = remember { mutableStateOf(false) }
    val showDeleteConfirmationDialog = remember { mutableStateOf(false) }
    val categoryToDelete = remember { mutableStateOf<Category?>(null) }
    
    val recipesState = viewModel.recipes.collectAsState()
    val categoriesState = viewModel.categories.collectAsState()
    
    val recipes = recipesState.value
    val categories = categoriesState.value

    if (selectedCategoryState.value == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categories", 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = { showAddCategoryDialogState.value = true },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Category", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    val recipesInCategory = recipes.filter { it.category == category.name }
                    CategoryItem(
                        category = category.name,
                        onClick = { selectedCategoryState.value = category.name },
                        onDelete = {
                            if (recipesInCategory.isNotEmpty()) {
                                categoryToDelete.value = category
                                showDeleteConfirmationDialog.value = true
                            } else {
                                viewModel.deleteCategory(category)
                            }
                        }
                    )
                }
            }
        }
    } else {
        val selectedRecipeForDetailState = remember { mutableStateOf<Recipe?>(null) }
        val recipeToEditState = remember { mutableStateOf<Recipe?>(null) }
        val recipeToDeleteState = remember { mutableStateOf<Recipe?>(null) }

        CategoryDetailScreen(
            categoryName = selectedCategoryState.value!!,
            recipes = recipes.filter { it.category == selectedCategoryState.value },
            onBack = { selectedCategoryState.value = null },
            onDeleteRecipe = { recipe -> recipeToDeleteState.value = recipe },
            onRecipeClick = { recipe -> selectedRecipeForDetailState.value = recipe }
        )

        recipeToDeleteState.value?.let { recipe ->
            DeleteRecipeConfirmationDialog(
                recipeName = recipe.title,
                onConfirm = {
                    viewModel.deleteRecipe(recipe)
                    recipeToDeleteState.value = null
                },
                onDismiss = { recipeToDeleteState.value = null }
            )
        }

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

    if (showDeleteConfirmationDialog.value) {
        categoryToDelete.value?.let { category ->
            DeleteCategoryConfirmationDialog(
                categoryName = category.name,
                onConfirm = {
                    viewModel.deleteCategory(category, deleteRecipes = true)
                    showDeleteConfirmationDialog.value = false
                },
                onDismiss = {
                    showDeleteConfirmationDialog.value = false
                }
            )
        }
    }
}

@Composable
fun DeleteCategoryConfirmationDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category") },
        text = { 
            Text("This category contains recipes. If you delete it, all recipes in this category ($categoryName) will also be deleted. Are you sure permanently delete the data?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteRecipeConfirmationDialog(
    recipeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Recipe") },
        text = { 
            Text("Are you sure you want to delete recipe'$recipeName'? This action will permanently delete the recipe.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryItem(category: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = category,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Category")
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
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
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recipe.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.chefmate_logo),
                        error = painterResource(R.drawable.chefmate_logo)
                    )
                } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = recipe.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " ${recipe.rating}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    recipe.category?.let {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Recipe")
            }
        }
    }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onCategoryAdded: (String) -> Unit) {
    val categoryNameState = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("New Category", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = categoryNameState.value,
                onValueChange = { categoryNameState.value = it },
                label = { Text("Category Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (categoryNameState.value.isNotBlank()) onCategoryAdded(categoryNameState.value) },
                shape = RoundedCornerShape(12.dp)
            ) {
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
        placeholder = { Text("Search your recipes...", color = Color.Gray.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        singleLine = true,
        shape = CircleShape
    )
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(220.dp)
            .height(300.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recipe.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.chefmate_logo),
                        error = painterResource(R.drawable.chefmate_logo)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Floating Rating Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = recipe.rating.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = recipe.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    recipe.tags?.take(2)?.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.title, 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, 
                    modifier = Modifier.weight(1f)
                )
                Row {
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
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (recipe.imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recipe.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.chefmate_logo),
                        error = painterResource(R.drawable.chefmate_logo)
                    )
                }

                if (recipe.videoUri != null) {
                    val isWebLink = recipe.videoUri.toString().startsWith("http")
                    if (isWebLink) {
                        val context = LocalContext.current
                        Button(
                            onClick = { 
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, recipe.videoUri)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)), // YouTube Red
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Watch Video Link")
                        }
                    } else {
                        VideoPlayer(videoUri = recipe.videoUri)
                    }
                }

                recipe.description?.let {
                    Text(
                        text = it, 
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp
                    )
                }

                recipe.ingredients?.let { ingredientsList ->
                    if (ingredientsList.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Ingredients", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            ingredientsList.forEach { ingredient ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Text(text = ingredient, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                recipe.instructions?.let { instructionsList ->
                    if (instructionsList.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Instructions", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            instructionsList.forEachIndexed { index, step ->
                                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = "${index + 1}", 
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                            .size(24.dp)
                                            .wrapContentSize(Alignment.Center)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(text = step, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeDialog(recipe: Recipe, viewModel: RecipeViewModel, onDismiss: () -> Unit, onRecipeUpdated: (Recipe) -> Unit) {
    val titleState = remember { mutableStateOf(recipe.title) }
    val descriptionState = remember { mutableStateOf(recipe.description ?: "") }
    val ingredientsState = remember { mutableStateOf(recipe.ingredients?.joinToString("\n") ?: "") }
    val instructionsState = remember { mutableStateOf(recipe.instructions?.joinToString("\n") ?: "") }
    val ratingState = remember { mutableStateOf(recipe.rating?.toString() ?: "0.0") }
    val tagsState = remember { mutableStateOf(recipe.tags?.joinToString(", ") ?: "") }
    val selectedCategoryState = remember { mutableStateOf(recipe.category ?: "General") }
    val imageUriState = remember { mutableStateOf<Uri?>(recipe.imageUri) }
    val videoUriState = remember { mutableStateOf<Uri?>(recipe.videoUri) }
    val videoLinkState = remember { mutableStateOf(if (recipe.videoUri?.toString()?.startsWith("http") == true) recipe.videoUri.toString() else "") }
    val currentTempUriState = remember { mutableStateOf<Uri?>(null) }
    val currentTempVideoUriState = remember { mutableStateOf<Uri?>(null) }
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

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUriState.value = uri
            videoLinkState.value = "" // Clear link if file selected
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Edit Recipe", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = titleState.value,
                    onValueChange = { titleState.value = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = expandedState.value,
                    onExpandedChange = { expandedState.value = !expandedState.value }
                ) {
                    OutlinedTextField(
                        value = selectedCategoryState.value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState.value) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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

                Text("Media", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Img Gallery", fontSize = 10.sp)
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Img Camera", fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = { videoLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Video File")
                }

                OutlinedTextField(
                    value = videoLinkState.value,
                    onValueChange = { 
                        videoLinkState.value = it
                        if (it.isNotBlank()) videoUriState.value = null // Clear file if link entered
                    },
                    label = { Text("Or Paste Video Link (YouTube/Web)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("https://...") }
                )

                if (imageUriState.value != null || videoUriState.value != null || videoLinkState.value.isNotBlank()) {
                    Text("Preview Attached", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                OutlinedTextField(
                    value = descriptionState.value,
                    onValueChange = { descriptionState.value = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = ingredientsState.value,
                    onValueChange = { ingredientsState.value = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = instructionsState.value,
                    onValueChange = { instructionsState.value = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ratingState.value,
                        onValueChange = { ratingState.value = it },
                        label = { Text("Rating") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = tagsState.value,
                        onValueChange = { tagsState.value = it },
                        label = { Text("Tags") },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleState.value.isNotBlank()) {
                        val finalVideoUri = if (videoLinkState.value.isNotBlank()) Uri.parse(videoLinkState.value) else videoUriState.value
                        onRecipeUpdated(
                            recipe.copy(
                                title = titleState.value,
                                description = descriptionState.value,
                                ingredients = ingredientsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructionsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUriState.value,
                                videoUri = finalVideoUri,
                                rating = ratingState.value.toDoubleOrNull() ?: 0.0,
                                tags = tagsState.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategoryState.value
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
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
fun AddRecipeDialog(viewModel: RecipeViewModel, initialCategory: String? = null, onDismiss: () -> Unit, onRecipeAdded: (Recipe) -> Unit) {
    val titleState = remember { mutableStateOf("") }
    val descriptionState = remember { mutableStateOf("") }
    val ingredientsState = remember { mutableStateOf("") }
    val instructionsState = remember { mutableStateOf("") }
    val ratingState = remember { mutableStateOf("4.5") }
    val tagsState = remember { mutableStateOf("") }
    val categoriesState = viewModel.categories.collectAsState()
    val categories = categoriesState.value
    val selectedCategoryState = remember { mutableStateOf(initialCategory ?: "General") }
    val imageUriState = remember { mutableStateOf<Uri?>(null) }
    val videoUriState = remember { mutableStateOf<Uri?>(null) }
    val videoLinkState = remember { mutableStateOf("") }
    val currentTempUriState = remember { mutableStateOf<Uri?>(null) }
    val expandedState = remember { mutableStateOf(false) }

    LaunchedEffect(categories, initialCategory) {
        if (initialCategory != null) {
            selectedCategoryState.value = initialCategory
        } else if (selectedCategoryState.value == "General" && categories.isNotEmpty()) {
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

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUriState.value = uri
            videoLinkState.value = ""
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add New Recipe", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = titleState.value,
                    onValueChange = { titleState.value = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = expandedState.value,
                    onExpandedChange = { expandedState.value = !expandedState.value }
                ) {
                    OutlinedTextField(
                        value = selectedCategoryState.value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState.value) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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

                Text("Media", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Img Gallery", fontSize = 10.sp)
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Img Camera", fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = { videoLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Video File")
                }

                OutlinedTextField(
                    value = videoLinkState.value,
                    onValueChange = { 
                        videoLinkState.value = it
                        if (it.isNotBlank()) videoUriState.value = null
                    },
                    label = { Text("Or Paste Video Link (YouTube/Web)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("https://...") }
                )

                if (imageUriState.value != null || videoUriState.value != null || videoLinkState.value.isNotBlank()) {
                    Text("Preview Attached", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                OutlinedTextField(
                    value = descriptionState.value,
                    onValueChange = { descriptionState.value = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = ingredientsState.value,
                    onValueChange = { ingredientsState.value = it },
                    label = { Text("Ingredients (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = instructionsState.value,
                    onValueChange = { instructionsState.value = it },
                    label = { Text("Cooking Steps (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ratingState.value,
                        onValueChange = { ratingState.value = it },
                        label = { Text("Rating") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = tagsState.value,
                        onValueChange = { tagsState.value = it },
                        label = { Text("Tags") },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleState.value.isNotBlank()) {
                        val finalVideoUri = if (videoLinkState.value.isNotBlank()) Uri.parse(videoLinkState.value) else videoUriState.value
                        onRecipeAdded(
                            Recipe(
                                title = titleState.value,
                                description = descriptionState.value,
                                ingredients = ingredientsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                instructions = instructionsState.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                imageUri = imageUriState.value,
                                videoUri = finalVideoUri,
                                rating = ratingState.value.toDoubleOrNull() ?: 0.0,
                                tags = tagsState.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("#")) it else "#$it" },
                                category = selectedCategoryState.value
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
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
