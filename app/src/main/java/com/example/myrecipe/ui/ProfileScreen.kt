package com.example.myrecipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myrecipe.BuildConfig
import com.example.myrecipe.R
import com.example.myrecipe.model.UpdateInfo
import com.example.myrecipe.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@Composable
fun ProfileScreen(authViewModel: AuthViewModel, recipeViewModel: RecipeViewModel) {
    val user = authViewModel.currentUser.value
    val recipes by recipeViewModel.recipes.collectAsState()
    
    val recipeCount = recipes.size
    val favoriteCount = recipes.count { it.isFavorite }
    
    val level = when {
        recipeCount >= 50 -> "Expert"
        recipeCount >= 25 -> "Moderate"
        recipeCount >= 10 -> "Intermediate"
        else -> "Novice"
    }

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val biometricHelper = remember { BiometricHelper(context) }
    
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isUploadingAvatar = true
                try {
                    val file = saveUriToTempFile(context, it)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
                        val uploadResponse = RetrofitClient.instance.uploadImage(body)
                        val relativeUrl = uploadResponse["url"]
                        if (relativeUrl != null) {
                            val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
                            val remoteUri = "$baseUrl$relativeUrl"
                            authViewModel.updateAvatar(remoteUri) { success ->
                                if (success) {
                                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    // Re-using the camera logic from RecipeComponents if needed, or keeping it local
    val tempCameraUri = remember { mutableStateOf<Uri?>(null) }
    
    val cameraActionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri.value?.let { uri ->
                scope.launch {
                    isUploadingAvatar = true
                    try {
                        val file = saveUriToTempFile(context, uri)
                        if (file != null) {
                            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
                            val uploadResponse = RetrofitClient.instance.uploadImage(body)
                            val relativeUrl = uploadResponse["url"]
                            if (relativeUrl != null) {
                                val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
                                val remoteUri = "$baseUrl$relativeUrl"
                                authViewModel.updateAvatar(remoteUri) { s ->
                                    if (s) Toast.makeText(context, "Photo updated!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Camera upload failed", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, now we can launch the camera
            val file = File(context.cacheDir, "camera_profile.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri.value = uri
            cameraActionLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show()
        }
    }

    val memberSinceDate = user?.createdAt?.let {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it))
    } ?: "August 2026"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Gradient Header Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colorScheme.primary, colorScheme.primary.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Profile Image
            Surface(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(8.dp, CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable { showAvatarSheet = true },
                shape = CircleShape,
                color = colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    } else if (user?.avatarUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(user.avatarUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.chefmate_logo),
                            error = painterResource(R.drawable.chefmate_logo)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(70.dp),
                            tint = colorScheme.primary
                        )
                    }
                    
                    // Edit Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().height(30.dp)
                        ) {
                            Text(
                                "EDIT", 
                                color = Color.White, 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = user?.username ?: "Chef Mate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onBackground
            )
            
            Text(
                text = "Culinary Explorer",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Stats Row (Modern addition)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(label = "Recipes", value = recipeCount.toString())
                ProfileStat(label = "Favorites", value = favoriteCount.toString())
                ProfileStat(label = "Level", value = level)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Info Section
            Text(
                text = "Account Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = colorScheme.primary
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileInfoRowModern(
                        icon = Icons.Default.Person, 
                        label = "Username", 
                        value = user?.username ?: "Guest User"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colorScheme.onSurface.copy(alpha = 0.08f))
                    ProfileInfoRowModern(
                        icon = Icons.Default.CalendarMonth, 
                        label = "Member Since", 
                        value = memberSinceDate
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Actions Section
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "Check for Updates",
                        subtitle = "Currently v${BuildConfig.VERSION_NAME}",
                        showBadge = isCheckingUpdates,
                        onClick = {
                            if (!isCheckingUpdates) {
                                scope.launch {
                                    isCheckingUpdates = true
                                    val info = updateManager.checkForUpdates()
                                    if (info != null) {
                                        updateInfo = info
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                                    }
                                    isCheckingUpdates = false
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colorScheme.onSurface.copy(alpha = 0.08f))
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "About ChefMate",
                        subtitle = "Learn more about the project",
                        onClick = { showAboutDialog = true }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (authViewModel.isDarkMode.value) Icons.Default.DarkMode else Icons.Default.LightMode, 
                                contentDescription = null, 
                                tint = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = "Dark Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = "Toggle dark/light appearance", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Switch(
                            checked = authViewModel.isDarkMode.value,
                            onCheckedChange = { authViewModel.setDarkMode(it) }
                        )
                    }
                    
                    if (biometricHelper.isBiometricAvailable()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colorScheme.onSurface.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(text = "Biometric Login", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(text = "Use fingerprint or face to sign in", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            Switch(
                                checked = authViewModel.isBiometricEnabled.value,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        Toast.makeText(context, "Please re-login to enable biometric login securely", Toast.LENGTH_LONG).show()
                                    } else {
                                        authViewModel.setBiometricEnabled(false)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            // Logout Button
            Button(
                onClick = { authViewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "App Version: ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (Debug)" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("A new version (${updateInfo!!.versionName}) is ready to download.", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = colorScheme.primary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = updateInfo!!.releaseNotes,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        updateManager.downloadAndInstall(updateInfo!!)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later", color = colorScheme.primary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showAvatarSheet) {
        ProfilePictureSheet(
            onDismiss = { showAvatarSheet = false },
            onAction = { action ->
                showAvatarSheet = false
                when (action) {
                    "camera" -> {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val file = File(context.cacheDir, "camera_profile.jpg")
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            tempCameraUri.value = uri
                            cameraActionLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    "gallery" -> avatarLauncher.launch("image/*")
                    "delete" -> {
                        authViewModel.updateAvatar("") { s ->
                            if (s) Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "ai" -> showAiDialog = true
                    else -> Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showAiDialog) {
        AiAvatarDialog(
            onDismiss = { showAiDialog = false },
            onAvatarSelected = { remoteUri ->
                showAiDialog = false
                authViewModel.updateAvatar(remoteUri) { success ->
                    if (success) Toast.makeText(context, "AI avatar set!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun AiAvatarDialog(onDismiss: () -> Unit, onAvatarSelected: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("AI Avatar Generator", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Describe how you want your avatar to look:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("e.g. A chef with a cat hat") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                if (isGenerating) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (generatedUrl != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AsyncImage(
                        model = generatedUrl,
                        contentDescription = "Generated Avatar",
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            if (generatedUrl != null) {
                Button(onClick = { onAvatarSelected(generatedUrl!!) }) {
                    Text("Set as Profile")
                }
            } else {
                Button(
                    onClick = {
                        if (prompt.isNotBlank()) {
                            scope.launch {
                                isGenerating = true
                                try {
                                    val response = RetrofitClient.instance.generateAiAvatar(mapOf("prompt" to prompt))
                                    val url = response["url"]
                                    if (url != null) {
                                        val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
                                        generatedUrl = "$baseUrl$url"
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Generation failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    enabled = !isGenerating && prompt.isNotBlank()
                ) {
                    Text("Generate")
                }
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
fun ProfilePictureSheet(onDismiss: () -> Unit, onAction: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    "Profile picture",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onAction("delete") }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SheetOption(Icons.Default.PhotoCamera, "Camera") { onAction("camera") }
            SheetOption(Icons.Default.Photo, "Gallery") { onAction("gallery") }
            SheetOption(Icons.Default.Share, "Import from Instagram") { onAction("instagram") }
            SheetOption(Icons.Default.Facebook, "Import from Facebook") { onAction("facebook") }
            SheetOption(Icons.Default.AutoAwesome, "AI images") { onAction("ai") }
        }
    }
}

@Composable
fun SheetOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun saveUriToTempFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.RestaurantMenu,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "ChefMate",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "A modern digital cookbook designed for culinary enthusiasts to organize, discover, and share their favorite recipes.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.1f))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Jucelian Human",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Built with ❤️ using Jetpack Compose and Ktor.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun ProfileInfoRowModern(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (showBadge) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
