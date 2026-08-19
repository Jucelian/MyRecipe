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
import androidx.compose.foundation.border
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myrecipe.ui.theme.ChefBackground
import com.example.myrecipe.ui.theme.ChefSecondary
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myrecipe.R
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan
import java.io.File
import java.util.Calendar

@Composable
fun AutoScrollingRecipeRow(meals: List<Recipe>, onRecipeClick: (Recipe) -> Unit) {
    if (meals.isEmpty()) return
    val totalPages = meals.size * 1000
    val pagerState = rememberPagerState(pageCount = { totalPages }, initialPage = totalPages / 2)
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(4000); if (!pagerState.isScrollInProgress) pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
    HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 40.dp), pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth().height(310.dp)) { page ->
        val meal = meals[page % meals.size]
        RecipeCard(recipe = meal, onClick = { onRecipeClick(meal) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VideoPlayer(videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(videoUri)); prepare() } }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = true } }, modifier = modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeApp(viewModel: RecipeViewModel, authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val showAddDialogState = remember { mutableStateOf(false) }
    val activeCategoryState = remember { mutableStateOf<String?>(null) }
    val searchQueryState = remember { mutableStateOf("") }
    val selectedTabState = remember { mutableIntStateOf(0) }
    val currentUser = authViewModel.currentUser.value
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val recipeToScheduleState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToCookState = remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) currentUser?.let { viewModel.refreshData(it.username) } }
        lifecycleOwner.lifecycle.addObserver(observer)
    }
    LaunchedEffect(errorMessage) { errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(currentUser) { currentUser?.let { viewModel.setCurrentOwner(it.username) } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = selectedTabState.intValue != 4, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTabState.intValue == 1 || selectedTabState.intValue == 2) {
                        SearchBar(query = searchQueryState.value, onQueryChange = { searchQueryState.value = it }, modifier = Modifier.weight(1f))
                    } else {
                        Text(text = "ChefMate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { authViewModel.logout() }, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTabState.intValue == 0, onClick = { selectedTabState.intValue = 0; activeCategoryState.value = null }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = selectedTabState.intValue == 1, onClick = { selectedTabState.intValue = 1; activeCategoryState.value = null }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Explore") })
                NavigationBarItem(selected = selectedTabState.intValue == 2, onClick = { selectedTabState.intValue = 2 }, icon = { Icon(Icons.Default.RestaurantMenu, null) }, label = { Text("My Recipes") })
                NavigationBarItem(selected = selectedTabState.intValue == 3, onClick = { selectedTabState.intValue = 3; activeCategoryState.value = null }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Planner") })
                NavigationBarItem(selected = selectedTabState.intValue == 4, onClick = { selectedTabState.intValue = 4; activeCategoryState.value = null }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
            }
        },
        floatingActionButton = { if (selectedTabState.intValue != 4) FloatingActionButton(onClick = { showAddDialogState.value = true }) { Icon(Icons.Default.Add, null) } }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(targetState = selectedTabState.intValue, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "TabTransition") { tab ->
                when (tab) {
                    0 -> HomeScreen(viewModel, authViewModel, onScheduleRecipe = { recipeToScheduleState.value = it })
                    1 -> ExploreScreen(viewModel, searchQueryState.value, onScheduleRecipe = { recipeToScheduleState.value = it })
                    2 -> MyRecipesTab(viewModel, currentUser?.username ?: "", onCategorySelected = { activeCategoryState.value = it }, onScheduleRecipe = { recipeToScheduleState.value = it })
                    3 -> PlannerScreen(viewModel)
                    4 -> ProfileScreen(authViewModel, viewModel)
                }
            }
        }
        if (showAddDialogState.value) { AddRecipeDialog(viewModel = viewModel, initialCategory = activeCategoryState.value, onDismiss = { showAddDialogState.value = false }, onRecipeAdded = { viewModel.addRecipe(it.copy(owner = currentUser?.username ?: "")); showAddDialogState.value = false }) }
        if (recipeToScheduleState.value != null) { ScheduleMealDialog(onDismiss = { recipeToScheduleState.value = null }, onSchedule = { d, t -> viewModel.addMealPlan(recipeToScheduleState.value!!, d, t); recipeToScheduleState.value = null; Toast.makeText(context, "Meal scheduled!", Toast.LENGTH_SHORT).show() }) }
        if (recipeToCookState.value != null) { CookingModeDialog(recipe = recipeToCookState.value!!, onDismiss = { recipeToCookState.value = null }) }
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: RecipeViewModel, authViewModel: AuthViewModel, onScheduleRecipe: (Recipe) -> Unit) {
    val context = LocalContext.current
    val selectedRecipeState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToEditState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToDeleteState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToSaveState = remember { mutableStateOf<Recipe?>(null) }
    val recipes by viewModel.recipes.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val dailyRecipes by viewModel.dailyRecipes.collectAsState()
    val isDailyPicksExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("daily_picks")) }
    val isRecentMealsExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("recent_meals")) }
    val isFavoritesExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("favorites")) }
    val isExploreAllExpanded = remember { mutableStateOf(authViewModel.isSectionExpanded("explore_all")) }
    val currentUser = authViewModel.currentUser.value
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 0..11 -> "Good Morning"; in 12..17 -> "Good Afternoon"; else -> "Good Evening" }
    LaunchedEffect(Unit) { currentUser?.let { viewModel.setCurrentOwner(it.username) } }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { currentUser?.let { viewModel.refreshData(it.username, true) } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (dailyRecipes.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().clickable { isDailyPicksExpanded.value = !isDailyPicksExpanded.value; authViewModel.setSectionExpanded("daily_picks", isDailyPicksExpanded.value) }.padding(start = 20.dp, top = 24.dp, bottom = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Daily Chef's Picks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Today's Inspiration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground) }
                        Icon(if (isDailyPicksExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isDailyPicksExpanded.value) {
                    dailyRecipes.forEach { (category, meals) ->
                        item { Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.secondary) }
                        item { AutoScrollingRecipeRow(meals = meals, onRecipeClick = { selectedRecipeState.value = it }) }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().clickable { isRecentMealsExpanded.value = !isRecentMealsExpanded.value; authViewModel.setSectionExpanded("recent_meals", isRecentMealsExpanded.value) }.padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("$greeting, Chef!", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Recently Added Meals", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground) }
                    Icon(if (isRecentMealsExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (isRecentMealsExpanded.value) {
                item {
                    val recent = recipes.take(5)
                    if (recent.isEmpty()) EmptyState("No recipes yet.\nStart by adding your first meal!", Icons.Default.RestaurantMenu)
                    else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(recent, key = { it.id }) { RecipeCard(it, { selectedRecipeState.value = it }, Modifier.animateItem()) } }
                }
            }
            val favorites = recipes.filter { it.isFavorite }
            if (favorites.isNotEmpty()) {
                item { Row(modifier = Modifier.fillMaxWidth().clickable { isFavoritesExpanded.value = !isFavoritesExpanded.value; authViewModel.setSectionExpanded("favorites", isFavoritesExpanded.value) }.padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Your Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground); Icon(if (isFavoritesExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary) } }
                if (isFavoritesExpanded.value) { item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(favorites, key = { it.id }) { RecipeCard(it, { selectedRecipeState.value = it }, Modifier.animateItem()) } } } }
            }
            item { Row(modifier = Modifier.fillMaxWidth().clickable { isExploreAllExpanded.value = !isExploreAllExpanded.value; authViewModel.setSectionExpanded("explore_all", isExploreAllExpanded.value) }.padding(start = 20.dp, top = 24.dp, bottom = 12.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Explore All", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground); Icon(if (isExploreAllExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary) } }
            if (isExploreAllExpanded.value) {
                if (recipes.isEmpty() && !isRefreshing) item { EmptyState("Your collection is empty.", Icons.Default.Inventory2) }
                else items(recipes, key = { it.id }) { recipe -> Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateItem()) { RecipeItemRow(recipe, { recipeToDeleteState.value = recipe }, { selectedRecipeState.value = recipe }) } }
            }
        }
    }
    recipeToDeleteState.value?.let { r -> DeleteRecipeConfirmationDialog(r.title, { viewModel.deleteRecipe(r); recipeToDeleteState.value = null }, { recipeToDeleteState.value = null }) }
    selectedRecipeState.value?.let { r -> RecipeDetailDialog(r, viewModel, { selectedRecipeState.value = null }, { viewModel.toggleFavorite(r) }, { recipeToEditState.value = r; selectedRecipeState.value = null }, { recipeToSaveState.value = r; selectedRecipeState.value = null }, { onScheduleRecipe(r); selectedRecipeState.value = null }, { /* onStartCooking */ }) }
    recipeToSaveState.value?.let { r -> SaveRecipeWithCategoryDialog(viewModel.categories.value, { recipeToSaveState.value = null }, { cat -> viewModel.savePublicRecipe(r, cat); recipeToSaveState.value = null; Toast.makeText(context, "Saved to $cat!", Toast.LENGTH_SHORT).show() }) }
    recipeToEditState.value?.let { r -> EditRecipeDialog(r, viewModel, { recipeToEditState.value = null }, { updated -> viewModel.updateRecipe(updated); recipeToEditState.value = null }) }
}

@Composable
fun ExploreScreen(viewModel: RecipeViewModel, query: String, onScheduleRecipe: (Recipe) -> Unit) {
    val context = LocalContext.current
    val selectedRecipeState = remember { mutableStateOf<Recipe?>(null) }
    val recipeToSaveState = remember { mutableStateOf<Recipe?>(null) }
    val publicResults by viewModel.publicSearchResults.collectAsState()
    val isSearchingPublic by viewModel.isSearchingPublic.collectAsState()
    val communityRecipes by viewModel.communityRecipes.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    LaunchedEffect(query) { if (query.length >= 3) viewModel.searchPublicRecipes(query) }
    val filteredLocal = if (query.isEmpty()) recipes else recipes.filter { it.title.contains(query, true) || it.description?.contains(query, true) == true || it.tags?.any { t -> t.contains(query, true) } == true }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(if (query.isEmpty()) "Discover Recipes" else "Search Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) }
        if (filteredLocal.isNotEmpty()) {
            item { Text("Your Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
            items(filteredLocal) { r -> Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { RecipeItemRow(r, null, { selectedRecipeState.value = r }) } }
        }
        if (communityRecipes.isNotEmpty() && query.isEmpty()) {
            item { Text("Community Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.tertiary) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(communityRecipes) { r -> RecipeCard(r, { selectedRecipeState.value = r }, Modifier.width(180.dp)) } } }
        }
        if (query.length >= 3) {
            item { Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Online Recipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f)); if (isSearchingPublic) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) } }
            if (publicResults.isEmpty() && !isSearchingPublic) item { Text("No results found for '$query'", modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = Color.Gray) }
            else items(publicResults) { r -> Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { RecipeItemRow(r, null, { selectedRecipeState.value = r }) } }
        }
        if (filteredLocal.isEmpty() && (query.length < 3 || (publicResults.isEmpty() && !isSearchingPublic))) item { EmptyState(if (query.isEmpty()) "No recipes yet." else "Keep typing...", if (query.isEmpty()) Icons.Default.Kitchen else Icons.Default.Search) }
    }
    selectedRecipeState.value?.let { r -> RecipeDetailDialog(r, viewModel, { selectedRecipeState.value = null }, { viewModel.toggleFavorite(r) }, { /* restricted */ }, { recipeToSaveState.value = r; selectedRecipeState.value = null }, { onScheduleRecipe(r); selectedRecipeState.value = null }) }
    recipeToSaveState.value?.let { r -> SaveRecipeWithCategoryDialog(viewModel.categories.value, { recipeToSaveState.value = null }, { cat -> viewModel.savePublicRecipe(r, cat); recipeToSaveState.value = null; Toast.makeText(context, "Saved to $cat!", Toast.LENGTH_SHORT).show() }) }
}

@Composable
fun RecipeItemRow(recipe: Recipe, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (recipe.imageUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(recipe.imageUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.chefmate_logo))
            else Box(modifier = Modifier.size(90.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) }
            Spacer(Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(recipe.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)); Text(" ${recipe.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); recipe.category?.let { Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f)) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) } } } }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.7f)) }
            else Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary.copy(0.4f), modifier = Modifier.padding(8.dp).size(20.dp))
        }
    }
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.width(220.dp).height(300.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                if (recipe.imageUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(recipe.imageUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.chefmate_logo))
                else Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(48.dp)) }
                Surface(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(0.9f)) { Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(recipe.rating.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) } }
            }
            Column(modifier = Modifier.padding(16.dp)) { Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1); Text(recipe.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { recipe.tags?.take(2)?.forEach { tag -> Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)) { Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) } } } }
        }
    }
}

@Composable
fun MyRecipesTab(viewModel: RecipeViewModel, owner: String, onCategorySelected: (String?) -> Unit = {}, onScheduleRecipe: (Recipe) -> Unit) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedCategory) { onCategorySelected(selectedCategory) }
    var showAddCat by remember { mutableStateOf(false) }
    var showEditCat by remember { mutableStateOf(false) }
    var showDelCat by remember { mutableStateOf(false) }
    var catToEdit by remember { mutableStateOf<Category?>(null) }
    var catToDel by remember { mutableStateOf<Category?>(null) }
    val recipes by viewModel.recipes.collectAsState()
    val categories by viewModel.categories.collectAsState()

    if (selectedCategory == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Categories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); IconButton(onClick = { showAddCat = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Icon(Icons.Default.CreateNewFolder, null) } }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(categories) { cat ->
                    CategoryItem(cat.name, { selectedCategory = cat.name }, { catToEdit = cat; showEditCat = true }, { if (recipes.any { it.category == cat.name }) { catToDel = cat; showDelCat = true } else viewModel.deleteCategory(cat) })
                }
            }
        }
    } else {
        var selRec by remember { mutableStateOf<Recipe?>(null) }
        var recToEdit by remember { mutableStateOf<Recipe?>(null) }
        var recToDel by remember { mutableStateOf<Recipe?>(null) }
        CategoryDetailScreen(selectedCategory!!, recipes.filter { it.category == selectedCategory }, { selectedCategory = null }, { recToDel = it }, { selRec = it })
        recToDel?.let { r -> DeleteRecipeConfirmationDialog(r.title, { viewModel.deleteRecipe(r); recToDel = null }, { recToDel = null }) }
        selRec?.let { r -> RecipeDetailDialog(r, viewModel, { selRec = null }, { viewModel.toggleFavorite(r) }, { recToEdit = r; selRec = null }, null, { onScheduleRecipe(r); selRec = null }, null) }
        recToEdit?.let { r -> EditRecipeDialog(r, viewModel, { recToEdit = null }, { updated -> viewModel.updateRecipe(updated); recToEdit = null }) }
    }
    if (showAddCat) AddCategoryDialog({ showAddCat = false }, { viewModel.addCategory(it, owner); showAddCat = false })
    if (showEditCat && catToEdit != null) EditCategoryDialog(catToEdit!!, { showEditCat = false }, { viewModel.updateCategory(catToEdit!!.name, catToEdit!!.copy(name = it, isSynced = false)); showEditCat = false })
    if (showDelCat && catToDel != null) DeleteCategoryConfirmationDialog(catToDel!!.name, { viewModel.deleteCategory(catToDel!!, true); showDelCat = false }, { showDelCat = false })
}

@Composable
fun CategoryItem(name: String, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(16.dp)); Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary.copy(0.8f)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f)) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CategoryDetailScreen(name: String, recipes: List<Recipe>, onBack: () -> Unit, onDeleteRecipe: (Recipe) -> Unit, onRecipeClick: (Recipe) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (recipes.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recipes here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(recipes) { r -> RecipeItemRow(r, { onDeleteRecipe(r) }, { onRecipeClick(r) }) } }
    }
}

@Composable
fun PlannerScreen(viewModel: RecipeViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab, containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary, indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(it[subTab]), color = MaterialTheme.colorScheme.primary) }) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Shopping List") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Meal Plan") })
        }
        if (subTab == 0) ShoppingListScreen(viewModel) else MealPlanScreen(viewModel)
    }
}

@Composable
fun MealPlanScreen(viewModel: RecipeViewModel) {
    val plans by viewModel.mealPlans.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) { Text("Weekly Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)) }
        if (plans.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("Your planner is empty.", Icons.Default.EventNote) }
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            plans.groupBy { val c = Calendar.getInstance(); c.timeInMillis = it.date; "${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}" }.forEach { (d, p) ->
                item { Text(d, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                items(p) { plan -> MealPlanRow(plan, { viewModel.deleteMealPlan(plan) }) }
            }
        }
    }
}

@Composable
fun MealPlanRow(plan: MealPlan, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f)) { Text(plan.mealType.take(1), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(plan.recipeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(plan.mealType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f)) }
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: RecipeViewModel) {
    val items by viewModel.shoppingList.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Shopping List", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); IconButton(onClick = { viewModel.clearCheckedShoppingItems() }) { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) } }
        if (items.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("Your list is empty.", Icons.Default.ShoppingCartCheckout) }
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.groupBy { it.category }.forEach { (cat, itms) ->
                item { Text(cat, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                items(itms) { i -> ShoppingItemRow(i, { viewModel.toggleShoppingItem(i) }, { viewModel.deleteShoppingItem(i) }) }
            }
        }
    }
}

@Composable
fun ShoppingItemRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.isChecked, { onToggle() }); Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.bodyLarge, textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, color = if (item.isChecked) MaterialTheme.colorScheme.onSurface.copy(0.5f) else MaterialTheme.colorScheme.onSurface); if (item.quantity.isNotBlank()) Text(item.quantity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp), tint = Color.Gray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeDialog(recipe: Recipe, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val steps = recipe.instructions ?: emptyList()
    var timer by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    // Keep screen on during cooking
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(running, timer) {
        if (running && timer > 0) {
            kotlinx.coroutines.delay(1000)
            timer -= 1
            if (timer == 0) running = false
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Step ${step + 1} of ${steps.size}", color = MaterialTheme.colorScheme.primary); IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } }
                LinearProgressIndicator(progress = { (step + 1).toFloat() / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(8.dp).clip(CircleShape))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(if (steps.isNotEmpty()) steps[step] else "None", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
                if (steps.isNotEmpty() && (steps[step].contains("min"))) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null)
                            Spacer(Modifier.width(12.dp))
                            val minutes = timer / 60
                            val seconds = timer % 60
                            Text(
                                text = if (timer > 0) "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}" else "Start Timer",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (timer == 0) Button(onClick = { timer = 600; running = true }) { Text("10 Min") } 
                            else IconButton(onClick = { running = !running }) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null) } 
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { step -= 1 }, enabled = step > 0, modifier = Modifier.weight(1f)) { Text("PREVIOUS") }
                    Button(onClick = { if (step < steps.size - 1) step += 1 else onDismiss() }, modifier = Modifier.weight(1f)) { Text(if (step < steps.size - 1) "NEXT" else "FINISH") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMealDialog(onDismiss: () -> Unit, onSchedule: (Long, String) -> Unit) {
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var type by remember { mutableStateOf("Lunch") }
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(date)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Schedule") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedCard(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(16.dp)) { Icon(Icons.Default.CalendarToday, null); Spacer(Modifier.width(12.dp)); val c = Calendar.getInstance(); c.timeInMillis = date; Text("${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}") } }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Breakfast", "Lunch", "Dinner").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }, modifier = Modifier.weight(1f)) } }
        }
    }, confirmButton = { Button(onClick = { onSchedule(date, type) }) { Text("Schedule") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (showPicker) DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = { TextButton(onClick = { date = pickerState.selectedDateMillis ?: date; showPicker = false }) { Text("OK") } }) { DatePicker(pickerState) }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onCategoryAdded: (String) -> Unit) {
    val name = remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New Category") }, text = { OutlinedTextField(value = name.value, onValueChange = { name.value = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.value.isNotBlank()) onCategoryAdded(name.value) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun EditCategoryDialog(cat: Category, onDismiss: () -> Unit, onUpdate: (String) -> Unit) {
    val name = remember { mutableStateOf(cat.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit") }, text = { OutlinedTextField(value = name.value, onValueChange = { name.value = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.value.isNotBlank()) onUpdate(name.value) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.surface == ChefSecondary
    TextField(value = query, onValueChange = onQueryChange, placeholder = { Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp)) }, modifier = modifier.height(56.dp).border(if (isDark) 1.dp else 0.dp, if (isDark) MaterialTheme.colorScheme.primary.copy(0.4f) else Color.Transparent, CircleShape).clip(CircleShape), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface), singleLine = true, shape = CircleShape)
}

@Composable
fun DeleteCategoryConfirmationDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Category") }, text = { Text("Delete '$name' and all its recipes?") }, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun DeleteRecipeConfirmationDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Recipe") }, text = { Text("Delete '$name'?") }, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveRecipeWithCategoryDialog(categories: List<Category>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "General") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Save Recipe", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select a category for this recipe:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedCategory) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
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
fun RecipeDetailDialog(recipe: Recipe, viewModel: RecipeViewModel, onDismiss: () -> Unit, onFav: () -> Unit, onEdit: () -> Unit, onSave: (() -> Unit)? = null, onSchedule: (() -> Unit)? = null, onStartCooking: (() -> Unit)? = null) {
    val context = LocalContext.current
    val fav = remember { mutableStateOf(recipe.isFavorite) }
    val isPub = recipe.owner == "Public"
    AlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(0.92f), title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(recipe.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Row { if (isPub && onSave != null) IconButton(onClick = onSave) { Icon(Icons.Default.BookmarkAdd, null, tint = MaterialTheme.colorScheme.primary) } else if (!isPub) { IconButton(onClick = { viewModel.togglePublish(recipe) }) { Icon(if (recipe.isPublic) Icons.Default.Public else Icons.Default.PublicOff, null, tint = if (recipe.isPublic) MaterialTheme.colorScheme.primary else Color.Gray) }; IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) } }; if (!isPub) IconButton(onClick = { fav.value = !fav.value; onFav() }) { Icon(if (fav.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav.value) Color.Red else Color.Gray) }; IconButton(onClick = { onSchedule?.invoke() }) { Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary) } } } }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (recipe.imageUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(recipe.imageUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.chefmate_logo))
            Button(onClick = { onStartCooking?.invoke() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(12.dp)); Text("START COOKING", fontWeight = FontWeight.ExtraBold) }
            if (recipe.videoUri != null) { if (recipe.videoUri.toString().startsWith("http")) Button(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, recipe.videoUri)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color.Red)) { Icon(Icons.Default.PlayCircle, null); Text("Watch Video") } else VideoPlayer(recipe.videoUri) }
            recipe.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            recipe.ingredients?.let { list -> Column { Text("Ingredients", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); list.forEach { i -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)); Spacer(Modifier.width(12.dp)); Text(i, Modifier.weight(1f)); IconButton({ viewModel.addIngredientsToShoppingList(Recipe(title="", ingredients=listOf(i), owner=recipe.owner)); Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show() }, Modifier.size(24.dp)) { Icon(Icons.Default.AddShoppingCart, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary) } } } } }
            recipe.instructions?.let { list -> Column { Text("Instructions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); list.forEachIndexed { idx, s -> Row(Modifier.padding(vertical = 6.dp)) { Text("${idx + 1}", Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).size(24.dp).wrapContentSize(Alignment.Center)); Spacer(Modifier.width(12.dp)); Text(s) } } } }
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeDialog(recipe: Recipe, viewModel: RecipeViewModel, onDismiss: () -> Unit, onUpdate: (Recipe) -> Unit) {
    val title = remember { mutableStateOf(recipe.title) }; val desc = remember { mutableStateOf(recipe.description ?: "") }; val ing = remember { mutableStateOf(recipe.ingredients?.joinToString("\n") ?: "") }; val ins = remember { mutableStateOf(recipe.instructions?.joinToString("\n") ?: "") }; val rat = remember { mutableStateOf(recipe.rating?.toString() ?: "0.0") }; val tags = remember { mutableStateOf(recipe.tags?.joinToString(", ") ?: "") }; val cat = remember { mutableStateOf(recipe.category ?: "General") }; val img = remember { mutableStateOf(recipe.imageUri) }; val vid = remember { mutableStateOf(recipe.videoUri) }; val vidL = remember { mutableStateOf(if (recipe.videoUri?.toString()?.startsWith("http") == true) recipe.videoUri.toString() else "") }; val exp = remember { mutableStateOf(false) }; val categories by viewModel.categories.collectAsState(); val context = LocalContext.current; val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) img.value = it }; val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { /* logic */ }; val scroll = rememberScrollState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit") }, text = {
        Column(Modifier.verticalScroll(scroll).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title.value, { title.value = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()); ExposedDropdownMenuBox(exp.value, { exp.value = !exp.value }) { OutlinedTextField(cat.value, {}, label = { Text("Category") }, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp.value) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(exp.value, { exp.value = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { cat.value = c.name; exp.value = false }) } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ gallery.launch("image/*") }, Modifier.weight(1f)) { Text("Gallery") }; Button({ /* camera */ }, Modifier.weight(1f)) { Text("Camera") } }
            if (img.value != null) Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))) { AsyncImage(img.value, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); IconButton({ img.value = null }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            OutlinedTextField(vidL.value, { vidL.value = it; if (it.isNotBlank()) vid.value = null }, label = { Text("Video Link") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(desc.value, { desc.value = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ing.value, { ing.value = it }, label = { Text("Ingredients") }, modifier = Modifier.fillMaxWidth(), minLines = 3); OutlinedTextField(ins.value, { ins.value = it }, label = { Text("Steps") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { OutlinedTextField(rat.value, { rat.value = it }, label = { Text("Rating") }, modifier = Modifier.weight(1f)); OutlinedTextField(tags.value, { tags.value = it }, label = { Text("Tags") }, modifier = Modifier.weight(2f)) }
        }
    }, confirmButton = { Button({ if (title.value.isNotBlank()) onUpdate(recipe.copy(title=title.value, description=desc.value, ingredients=ing.value.split("\n").filter { it.isNotBlank() }, instructions=ins.value.split("\n").filter { it.isNotBlank() }, imageUri=img.value, videoUri=if (vidL.value.isNotBlank()) Uri.parse(vidL.value) else vid.value, rating=rat.value.toDoubleOrNull() ?: 0.0, tags=tags.value.split(",").filter { it.isNotBlank() }, category=cat.value)) }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeDialog(viewModel: RecipeViewModel, initialCategory: String? = null, onDismiss: () -> Unit, onRecipeAdded: (Recipe) -> Unit) {
    val title = remember { mutableStateOf("") }; val desc = remember { mutableStateOf("") }; val ing = remember { mutableStateOf("") }; val ins = remember { mutableStateOf("") }; val rat = remember { mutableStateOf("4.5") }; val tags = remember { mutableStateOf("") }; val cat = remember { mutableStateOf(initialCategory ?: "General") }; val img = remember { mutableStateOf<Uri?>(null) }; val vid = remember { mutableStateOf<Uri?>(null) }; val vidL = remember { mutableStateOf("") }; val exp = remember { mutableStateOf(false) }; val categories by viewModel.categories.collectAsState(); val context = LocalContext.current; val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) img.value = it }; val scroll = rememberScrollState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New Recipe") }, text = {
        Column(Modifier.verticalScroll(scroll).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title.value, { title.value = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()); ExposedDropdownMenuBox(exp.value, { exp.value = !exp.value }) { OutlinedTextField(cat.value, {}, label = { Text("Category") }, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp.value) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(exp.value, { exp.value = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { cat.value = c.name; exp.value = false }) } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ gallery.launch("image/*") }, Modifier.weight(1f)) { Text("Gallery") }; Button({ /* camera */ }, Modifier.weight(1f)) { Text("Camera") } }
            if (img.value != null) Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))) { AsyncImage(img.value, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); IconButton({ img.value = null }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            OutlinedTextField(vidL.value, { vidL.value = it; if (it.isNotBlank()) vid.value = null }, label = { Text("Video Link") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(desc.value, { desc.value = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ing.value, { ing.value = it }, label = { Text("Ingredients") }, modifier = Modifier.fillMaxWidth(), minLines = 3); OutlinedTextField(ins.value, { ins.value = it }, label = { Text("Steps") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { OutlinedTextField(rat.value, { rat.value = it }, label = { Text("Rating") }, modifier = Modifier.weight(1f)); OutlinedTextField(tags.value, { tags.value = it }, label = { Text("Tags") }, modifier = Modifier.weight(2f)) }
        }
    }, confirmButton = { Button({ if (title.value.isNotBlank()) onRecipeAdded(Recipe(title=title.value, description=desc.value, ingredients=ing.value.split("\n").filter { it.isNotBlank() }, instructions=ins.value.split("\n").filter { it.isNotBlank() }, imageUri=img.value, videoUri=if (vidL.value.isNotBlank()) Uri.parse(vidL.value) else vid.value, rating=rat.value.toDoubleOrNull() ?: 0.0, tags=tags.value.split(",").filter { it.isNotBlank() }, category=cat.value)) }) { Text("Add") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
