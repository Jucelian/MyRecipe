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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.core.net.toUri
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
import java.util.Calendar
import com.example.myrecipe.model.Recipe
import com.example.myrecipe.model.Category
import com.example.myrecipe.model.ShoppingItem
import com.example.myrecipe.model.MealPlan
import com.example.myrecipe.R
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener

@Composable
fun AutoScrollingRecipeRow(meals: List<Recipe>, onRecipeClick: (Recipe) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { meals.size })
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(4000); if (pagerState.pageCount > 0) pagerState.animateScrollToPage((pagerState.currentPage + 1) % pagerState.pageCount) } }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(300.dp), contentPadding = PaddingValues(horizontal = 32.dp), pageSpacing = 16.dp) { page -> RecipeCard(meals[page], { onRecipeClick(meals[page]) }, Modifier.fillMaxSize()) }
}

@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare() } }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { PlayerView(it).apply { player = exoPlayer } }, modifier = modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
}

@Composable
fun YouTubePlayer(url: String, modifier: Modifier = Modifier) {
    val videoId = remember(url) {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#&?\\n]*"
        val compiledPattern = java.util.regex.Pattern.compile(pattern)
        val matcher = compiledPattern.matcher(url)
        if (matcher.find()) matcher.group() else null
    }

    if (videoId != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 11f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        
                        // Force a Desktop User Agent to bypass "Open App" mobile banners and scaling issues
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                        
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        // Hardware acceleration is crucial for smooth video playback
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        
                        // Reset scrollbars to prevent white space gutters
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
                        
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        
                        setBackgroundColor(android.graphics.Color.BLACK)
                        
                        loadUrl("https://www.youtube.com/embed/$videoId?modestbranding=1&rel=0&iv_load_policy=3&controls=1")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        val context = LocalContext.current
        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color.Gray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            Spacer(Modifier.width(8.dp))
            Text("Open External Video")
        }
    }
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
                NavigationBarItem(selected = selectedTabState.intValue == 5, onClick = { selectedTabState.intValue = 5; activeCategoryState.value = null }, icon = { Icon(Icons.Default.AutoFixHigh, null) }, label = { Text("Fridge AI") })
                NavigationBarItem(selected = selectedTabState.intValue == 2, onClick = { selectedTabState.intValue = 2 }, icon = { Icon(Icons.Default.RestaurantMenu, null) }, label = { Text("My Recipes", textAlign = TextAlign.Center) })
                NavigationBarItem(selected = selectedTabState.intValue == 3, onClick = { selectedTabState.intValue = 3; activeCategoryState.value = null }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Planner") })
                NavigationBarItem(selected = selectedTabState.intValue == 4, onClick = { selectedTabState.intValue = 4; activeCategoryState.value = null }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
            }
        },
        floatingActionButton = {
            if (selectedTabState.intValue != 3 && selectedTabState.intValue != 4 && selectedTabState.intValue != 5) {
                FloatingActionButton(onClick = { showAddDialogState.value = true }) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(targetState = selectedTabState.intValue, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "TabTransition") { tab ->
                when (tab) {
                    0 -> HomeScreen(viewModel, authViewModel, onScheduleRecipe = { recipeToScheduleState.value = it }, onStartCooking = { recipeToCookState.value = it })
                    1 -> ExploreScreen(viewModel, searchQueryState.value, onScheduleRecipe = { recipeToScheduleState.value = it }, onStartCooking = { recipeToCookState.value = it })
                    2 -> MyRecipesTab(viewModel, currentUser?.username ?: "", onCategorySelected = { activeCategoryState.value = it }, onScheduleRecipe = { recipeToScheduleState.value = it }, onStartCooking = { recipeToCookState.value = it })
                    3 -> PlannerScreen(viewModel)
                    4 -> ProfileScreen(authViewModel, viewModel)
                    5 -> FridgeAIScreen(viewModel, onStartCooking = { recipeToCookState.value = it })
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
fun HomeScreen(viewModel: RecipeViewModel, authViewModel: AuthViewModel, onScheduleRecipe: (Recipe) -> Unit, onStartCooking: (Recipe) -> Unit) {
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
    selectedRecipeState.value?.let { r -> RecipeDetailDialog(r, viewModel, { selectedRecipeState.value = null }, { viewModel.toggleFavorite(r) }, { recipeToEditState.value = r; selectedRecipeState.value = null }, { recipeToSaveState.value = r; selectedRecipeState.value = null }, { onScheduleRecipe(r); selectedRecipeState.value = null }, { onStartCooking(r); selectedRecipeState.value = null }) }
    recipeToSaveState.value?.let { r -> SaveRecipeWithCategoryDialog(viewModel.categories.value, { recipeToSaveState.value = null }, { cat -> viewModel.savePublicRecipe(r, cat); recipeToSaveState.value = null; Toast.makeText(context, "Saved to $cat!", Toast.LENGTH_SHORT).show() }) }
    recipeToEditState.value?.let { r -> EditRecipeDialog(r, viewModel, { recipeToEditState.value = null }, { updated -> viewModel.updateRecipe(updated); recipeToEditState.value = null }) }
}

@Composable
fun ExploreScreen(viewModel: RecipeViewModel, query: String, onScheduleRecipe: (Recipe) -> Unit, onStartCooking: (Recipe) -> Unit) {
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
    selectedRecipeState.value?.let { r -> RecipeDetailDialog(r, viewModel, { selectedRecipeState.value = null }, { viewModel.toggleFavorite(r) }, { /* restricted */ }, { recipeToSaveState.value = r; selectedRecipeState.value = null }, { onScheduleRecipe(r); selectedRecipeState.value = null }, { onStartCooking(r); selectedRecipeState.value = null }) }
    recipeToSaveState.value?.let { r -> SaveRecipeWithCategoryDialog(viewModel.categories.value, { recipeToSaveState.value = null }, { cat -> viewModel.savePublicRecipe(r, cat); recipeToSaveState.value = null; Toast.makeText(context, "Saved to $cat!", Toast.LENGTH_SHORT).show() }) }
}

@Composable
fun RecipeItemRow(recipe: Recipe, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (recipe.imageUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(recipe.imageUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.chefmate_logo))
            else Box(modifier = Modifier.size(90.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) }
            Spacer(Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(recipe.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)); Text(" ${recipe.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); recipe.category?.takeIf { it.isNotBlank() }?.let { Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f)) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) } } } }
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
            Column(modifier = Modifier.padding(16.dp)) { Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1); Text(recipe.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { recipe.tags?.filter { it.isNotBlank() }?.take(2)?.forEach { tag -> Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)) { Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) } } } }
        }
    }
}

@Composable
fun MyRecipesTab(viewModel: RecipeViewModel, owner: String, onCategorySelected: (String?) -> Unit = {}, onScheduleRecipe: (Recipe) -> Unit, onStartCooking: (Recipe) -> Unit) {
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
        selRec?.let { r -> RecipeDetailDialog(r, viewModel, { selRec = null }, { viewModel.toggleFavorite(r) }, { recToEdit = r; selRec = null }, null, { onScheduleRecipe(r); selRec = null }, { onStartCooking(r); selRec = null }) }
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
fun CategoryDetailScreen(category: String, recipes: List<Recipe>, onBack: () -> Unit, onDeleteRecipe: (Recipe) -> Unit, onRecipeClick: (Recipe) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Spacer(Modifier.width(12.dp)); Text(category, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        if (recipes.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("No recipes in this category.", Icons.Default.Inventory2) }
        else LazyColumn(contentPadding = PaddingValues(16.dp)) { items(recipes) { r -> RecipeItemRow(r, { onDeleteRecipe(r) }, { onRecipeClick(r) }) } }
    }
}

@Composable
fun PlannerScreen(viewModel: RecipeViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage, containerColor = MaterialTheme.colorScheme.surface, indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]), color = MaterialTheme.colorScheme.primary) }) {
            Tab(selected = pagerState.currentPage == 0, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, text = { Text("Meal Plan") })
            Tab(selected = pagerState.currentPage == 1, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, text = { Text("Shopping List") })
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page -> if (page == 0) MealPlanScreen(viewModel) else ShoppingListScreen(viewModel) }
    }
}

@Composable
fun MealPlanScreen(viewModel: RecipeViewModel) {
    val plans by viewModel.mealPlans.collectAsState()
    val editMealPlanState = remember { mutableStateOf<MealPlan?>(null) }
    val context = LocalContext.current
    
    // Use ViewModel state for persistence
    val expandedDays = viewModel.expandedDays

    val days = remember {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        (0..6).map { offset ->
            val day = cal.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, offset)
            day.timeInMillis
        }
    }
    
    // Initialize all to expanded by default if not set
    LaunchedEffect(days) {
        days.forEach { if (!expandedDays.containsKey(it)) expandedDays[it] = true }
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { 
                        viewModel.generateShoppingListForWeek()
                        Toast.makeText(context, "Added missing ingredients for the week!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-Generate Weekly Shopping List", fontWeight = FontWeight.Bold)
                }
            }

            days.forEach { dayTimestamp ->
                val dayPlans = plans.filter { plan ->
                    val planCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = plan.date }
                    val dayCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayTimestamp }
                    planCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                    planCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
                }
                
                val isExpanded = expandedDays[dayTimestamp] ?: true

                item(key = "header_$dayTimestamp") {
                    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val dateStr = sdf.format(Date(dayTimestamp))
                    val isToday = dayTimestamp == days[0]
                    
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { expandedDays[dayTimestamp] = !isExpanded }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isToday) "Today - $dateStr" else dateStr,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                if (!isExpanded && dayPlans.isNotEmpty()) {
                                    Text(
                                        text = "${dayPlans.size} meal${if (dayPlans.size > 1) "s" else ""} planned",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                
                if (isExpanded) {
                    if (dayPlans.isEmpty()) {
                        item(key = "empty_$dayTimestamp") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "No meals planned",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(dayPlans, key = { it.id }) { plan ->
                            MealPlanRow(
                                plan = plan,
                                onDelete = { viewModel.deleteMealPlan(plan) },
                                onEdit = { editMealPlanState.value = plan }
                            )
                        }
                    }
                }
            }
        }

        // Custom Scrollbar
        val scrollbarInfo by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems <= 0 || layoutInfo.visibleItemsInfo.size >= totalItems) return@derivedStateOf null
                
                val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf null
                
                val thumbHeightPercent = (layoutInfo.visibleItemsInfo.size.toFloat() / totalItems).coerceIn(0.1f, 1f)
                val scrollPercent = (firstVisibleItem.index.toFloat() / (totalItems - layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)).coerceIn(0f, 1f)
                
                thumbHeightPercent to scrollPercent
            }
        }

        scrollbarInfo?.let { (thumbHeight, scrollPos) ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 64.dp, bottom = 64.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(thumbHeight)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = (size.height - (size.height * thumbHeight)) * scrollPos
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                )
            }
        }
    }

    editMealPlanState.value?.let { plan ->
        ScheduleMealDialog(
            initialDate = plan.date,
            initialType = plan.mealType,
            onDismiss = { editMealPlanState.value = null },
            onSchedule = { d, t ->
                viewModel.updateMealPlan(plan.copy(date = d, mealType = t, isSynced = false))
                editMealPlanState.value = null
            }
        )
    }
}

@Composable
fun MealPlanRow(plan: MealPlan, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(plan.mealType, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp)); Text(plan.recipeTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.5f)) }
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: RecipeViewModel) {
    val items by viewModel.shoppingList.collectAsState()
    val context = LocalContext.current
    val groupedItems = remember(items) { items.groupBy { it.category } }
    
    // Use ViewModel state for persistence
    val expandedCategories = viewModel.expandedCategories
    
    // Initialize all to expanded by default if not set
    LaunchedEffect(groupedItems.keys) {
        groupedItems.keys.forEach { category ->
            if (!expandedCategories.containsKey(category)) {
                expandedCategories[category] = true
            }
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (items.isNotEmpty()) {
                    IconButton(onClick = { shareShoppingList(context, items) }) {
                        Icon(Icons.Default.Share, "Share List", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (items.any { it.isChecked }) {
                    TextButton(onClick = { viewModel.clearCheckedShoppingItems() }) {
                        Text("Clear Completed")
                    }
                }
            }
            
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Shopping list is empty.", Icons.Default.ListAlt)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedItems.forEach { (category, categoryItems) ->
                        val isExpanded = expandedCategories[category] ?: true
                        
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .clickable { expandedCategories[category] = !isExpanded }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        
                        if (isExpanded) {
                            items(categoryItems) { item ->
                                ShoppingItemRow(
                                    item,
                                    onToggle = { viewModel.toggleShoppingItem(item) },
                                    onDelete = { viewModel.deleteShoppingItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Scrollbar
        val scrollbarInfo by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems <= 0 || layoutInfo.visibleItemsInfo.size >= totalItems) return@derivedStateOf null
                
                val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf null
                
                val thumbHeightPercent = (layoutInfo.visibleItemsInfo.size.toFloat() / totalItems).coerceIn(0.1f, 1f)
                val scrollPercent = (firstVisibleItem.index.toFloat() / (totalItems - layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)).coerceIn(0f, 1f)
                
                thumbHeightPercent to scrollPercent
            }
        }

        scrollbarInfo?.let { (thumbHeight, scrollPos) ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 64.dp, bottom = 64.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(thumbHeight)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = (size.height - (size.height * thumbHeight)) * scrollPos
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun ShoppingItemRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (item.isChecked) Color.Gray else Color.Unspecified, textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            Text(
                text = if (!item.recipeTitle.isNullOrBlank()) "Recipe: ${item.recipeTitle}" else "Manual Entry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, null, tint = Color.LightGray) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeDialog(recipe: Recipe, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val steps = recipe.instructions ?: emptyList()
    var isFinished by remember { mutableStateOf(false) }
    
    val timers = remember { mutableStateListOf<CookingTimer>() }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    var isListening by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) { isListening = true; try { speechRecognizer.startListening(speechIntent) } catch(e: Exception) {} }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (isListening) try { speechRecognizer.startListening(speechIntent) } catch(e: Exception) {} }
            override fun onError(error: Int) { if (isListening) try { speechRecognizer.startListening(speechIntent) } catch(e: Exception) {} }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { command ->
                    when {
                        command.contains("next", true) -> if (step < steps.size - 1) step++
                        command.contains("back", true) || command.contains("previous", true) -> if (step > 0) step--
                        command.contains("finish", true) || command.contains("done", true) -> if (step == steps.size -1) isFinished = true else onDismiss()
                    }
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                if (recipe.videoUri != null) {
                    if (recipe.videoUri.toString().startsWith("http")) {
                        YouTubePlayer(recipe.videoUri.toString(), modifier = Modifier.padding(bottom = 16.dp))
                    } else {
                        VideoPlayer(recipe.videoUri, modifier = Modifier.padding(bottom = 16.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Step ${step + 1} of ${steps.size}", color = MaterialTheme.colorScheme.primary)
                    Row {
                        IconButton(onClick = { 
                            if (!isListening) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    isListening = true
                                    speechRecognizer.startListening(speechIntent)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                isListening = false
                                speechRecognizer.stopListening()
                            }
                        }) {
                            Icon(if (isListening) Icons.Default.Mic else Icons.Default.MicNone, null, tint = if (isListening) Color.Red else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    }
                }
                LinearProgressIndicator(progress = { (step + 1).toFloat() / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(8.dp).clip(CircleShape))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (steps.isNotEmpty()) steps[step] else "None", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                }

                // Multiple Timers section
                if (timers.isNotEmpty()) {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(timers) { timer ->
                            TimerCard(timer, onRemove = { timers.remove(timer) })
                        }
                    }
                }

                if (steps.isNotEmpty() && steps[step].contains("min", true)) {
                    Button(
                        onClick = {
                            val label = steps[step].take(20) + "..."
                            val minutes = steps[step].filter { it.isDigit() }.toIntOrNull() ?: 10
                            timers.add(CookingTimer(label = label, timeSeconds = minutes * 60, isRunning = true))
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Timer, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Step Timer")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { step -= 1 }, enabled = step > 0, modifier = Modifier.weight(1f)) { Text("PREVIOUS") }
                    Button(onClick = { if (step < steps.size - 1) step += 1 else isFinished = true }, modifier = Modifier.weight(1f)) { Text(if (step < steps.size - 1) "NEXT" else "FINISH") }
                }
            }
        }
    }

    if (isFinished) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Delicious!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Recipe completed successfully.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("DONE") }
                }
            }
        }
    }
}

@Composable
fun TimerCard(timer: CookingTimer, onRemove: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(timer.timeSeconds) }
    var isRunning by remember { mutableStateOf(timer.isRunning) }

    LaunchedEffect(isRunning) {
        while (isRunning && timeLeft > 0) {
            kotlinx.coroutines.delay(1000)
            timeLeft--
        }
    }

    Card(modifier = Modifier.width(150.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timer.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(16.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
            }
            Text(
                text = "%02d:%02d".format(timeLeft / 60, timeLeft % 60),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            IconButton(onClick = { isRunning = !isRunning }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleMealDialog(initialDate: Long? = null, initialType: String? = null, onDismiss: () -> Unit, onSchedule: (Long, String) -> Unit) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialDate ?: System.currentTimeMillis())
    val type = remember { mutableStateOf(initialType ?: "Lunch") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDate != null) "Update Meal" else "Schedule Meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                                .format(Date(dateState.selectedDateMillis ?: System.currentTimeMillis())),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 3
                ) {
                    listOf("Breakfast", "Lunch", "Dinner", "Sweet Treat").forEach { t ->
                        FilterChip(
                            selected = type.value == t,
                            onClick = { type.value = t },
                            label = { Text(t) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSchedule(dateState.selectedDateMillis ?: System.currentTimeMillis(), type.value) }) {
                Text(if (initialDate != null) "Update" else "Schedule")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val name = remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Category") }, text = { OutlinedTextField(value = name.value, onValueChange = { name.value = it }, label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.value.isNotBlank()) onAdd(name.value) }) { Text("Add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun EditCategoryDialog(category: Category, onDismiss: () -> Unit, onUpdate: (String) -> Unit) {
    val name = remember { mutableStateOf(category.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Category") }, text = { OutlinedTextField(value = name.value, onValueChange = { name.value = it }, label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.value.isNotBlank()) onUpdate(name.value) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = modifier.height(52.dp), placeholder = { Text("Search recipes...", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, null) } }, shape = RoundedCornerShape(26.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent), singleLine = true)
}

@Composable
fun DeleteCategoryConfirmationDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Category?") }, text = { Text("Are you sure you want to delete '$name'? All recipes in this category will also be deleted.") }, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun DeleteRecipeConfirmationDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Recipe?") }, text = { Text("Delete '$title' permanently?") }, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveRecipeWithCategoryDialog(categories: List<Category>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "General") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Save Recipe") }, text = { Column { Text("Select category to save this recipe:"); Spacer(modifier = Modifier.height(16.dp)); ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) { OutlinedTextField(value = selectedCategory, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { selectedCategory = category.name; expanded = false }) } } } } }, confirmButton = { Button(onClick = { onSave(selectedCategory) }, shape = RoundedCornerShape(12.dp)) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun RecipeDetailDialog(recipe: Recipe, viewModel: RecipeViewModel, onDismiss: () -> Unit, onFav: () -> Unit, onEdit: () -> Unit, onSave: (() -> Unit)? = null, onSchedule: (() -> Unit)? = null, onStartCooking: (() -> Unit)? = null) {
    val context = LocalContext.current
    val fav = remember { mutableStateOf(recipe.isFavorite) }
    val isPub = recipe.owner == "Public"
    var servings by remember { mutableIntStateOf(1) }
    AlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(0.92f), title = { Column(modifier = Modifier.fillMaxWidth()) { Text(recipe.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { if (isPub && onSave != null) { IconButton(onClick = onSave) { Icon(Icons.Default.BookmarkAdd, null, tint = MaterialTheme.colorScheme.primary) } } else if (!isPub) { IconButton(onClick = { viewModel.togglePublish(recipe) }) { Icon(if (recipe.isPublic) Icons.Default.Public else Icons.Default.PublicOff, null, tint = if (recipe.isPublic) MaterialTheme.colorScheme.primary else Color.Gray) }; IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) } }; if (!isPub) { IconButton(onClick = { fav.value = !fav.value; onFav() }) { Icon(if (fav.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav.value) Color.Red else Color.Gray) } }; IconButton(onClick = { onSchedule?.invoke() }) { Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary) } } } }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) { if (recipe.imageUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(recipe.imageUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.chefmate_logo));             Button(onClick = { onStartCooking?.invoke() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(12.dp)); Text("START COOKING", fontWeight = FontWeight.ExtraBold) };
            if (recipe.videoUri != null) {
                if (recipe.videoUri.toString().startsWith("http")) {
                    YouTubePlayer(recipe.videoUri.toString())
                } else {
                    VideoPlayer(recipe.videoUri)
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Servings:", style = MaterialTheme.typography.titleMedium) }; Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (servings > 1) servings-- }) { Icon(Icons.Default.Remove, null) }; Text(servings.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); IconButton(onClick = { servings++ }) { Icon(Icons.Default.Add, null) } } } };
recipe.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }; recipe.ingredients?.let { list -> Column { Text("Ingredients", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); list.forEach { i -> val scaledIngredient = scaleIngredient(i, servings.toDouble()); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)); Spacer(Modifier.width(12.dp)); Text(scaledIngredient, Modifier.weight(1f)); IconButton({ viewModel.addIngredientsToShoppingList(Recipe(title = recipe.title, ingredients = listOf(scaledIngredient), owner = recipe.owner, id = recipe.id, category = recipe.category)); Toast.makeText(context, "Added to shopping list", Toast.LENGTH_SHORT).show() }, Modifier.size(24.dp)) { Icon(Icons.Default.AddShoppingCart, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary) } } } } }; recipe.instructions?.let { list -> Column { Text("Instructions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); list.forEachIndexed { idx, s -> Row(Modifier.padding(vertical = 6.dp)) { Text("${idx + 1}", Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).size(24.dp).wrapContentSize(Alignment.Center)); Spacer(Modifier.width(12.dp)); Text(s) } } } } } }, confirmButton = { Button(onClick = onDismiss) { Text("Close") } })
}

private fun formatScaledQuantity(value: Double): String {
    if (value == value.toInt().toDouble()) return value.toInt().toString()
    return "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
}

private fun scaleIngredient(ingredient: String, servings: Double): String {
    val pattern = java.util.regex.Pattern.compile("^([0-9\\./]+)?\\s*(.*)$")
    val matcher = pattern.matcher(ingredient)
    if (matcher.find()) {
        val qtyStr = matcher.group(1)
        val rest = matcher.group(2)
        if (!qtyStr.isNullOrBlank()) {
            val qty = try {
                if (qtyStr.contains("/")) {
                    val parts = qtyStr.split("/")
                    parts[0].toDouble() / parts[1].toDouble()
                } else qtyStr.toDouble()
            } catch (e: Exception) { 1.0 }
            return "${formatScaledQuantity(qty * servings)} $rest"
        }
    }
    return ingredient
}


fun shareShoppingList(context: android.content.Context, items: List<ShoppingItem>) {
    val text = "ChefMate Shopping List:\n\n" + items.joinToString("\n") { 
        "- ${it.name} ${if (it.quantity.isNullOrBlank()) "" else "(${it.quantity})"}" + 
        if (it.recipeTitle.isNullOrBlank()) "" else " [For: ${it.recipeTitle}]"
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Shopping List"))
}

data class CookingTimer(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val timeSeconds: Int,
    val isRunning: Boolean = false
)
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
    }, confirmButton = { Button({ if (title.value.isNotBlank()) onUpdate(recipe.copy(title=title.value, description=desc.value, ingredients=ing.value.split("\n").filter { it.isNotBlank() }, instructions=ins.value.split("\n").filter { it.isNotBlank() }, imageUri=img.value, videoUri=if (vidL.value.isNotBlank()) vidL.value.toUri() else vid.value, rating=rat.value.toDoubleOrNull() ?: 0.0, tags=tags.value.split(",").filter { it.isNotBlank() }, category=cat.value)) }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
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
    }, confirmButton = { Button({ if (title.value.isNotBlank()) onRecipeAdded(Recipe(title=title.value, description=desc.value, ingredients=ing.value.split("\n").filter { it.isNotBlank() }, instructions=ins.value.split("\n").filter { it.isNotBlank() }, imageUri=img.value, videoUri=if (vidL.value.isNotBlank()) vidL.value.toUri() else vid.value, rating=rat.value.toDoubleOrNull() ?: 0.0, tags=tags.value.split(",").filter { it.isNotBlank() }, category=cat.value)) }) { Text("Add") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
