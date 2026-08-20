package com.example.myrecipe.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myrecipe.model.Recipe

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FridgeAIScreen(viewModel: RecipeViewModel, onStartCooking: (Recipe) -> Unit) {
    var ingredientInput by remember { mutableStateOf("") }
    val ingredients = remember { mutableStateListOf<String>() }
    val aiRecipe by viewModel.aiGeneratedRecipe.collectAsState()
    val isGenerating by viewModel.isGeneratingAI.collectAsState()
    var showRecipeDetail by remember { mutableStateOf(false) }

    LaunchedEffect(aiRecipe) {
        if (aiRecipe != null) {
            showRecipeDetail = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What's in my Fridge?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tell me what ingredients you have, and I'll suggest a recipe!",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = ingredientInput,
            onValueChange = { ingredientInput = it },
            label = { Text("Add an ingredient") },
            placeholder = { Text("e.g. Chicken, Tomato, Garlic") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (ingredientInput.isNotBlank()) {
                        val newIngs = ingredientInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        ingredients.addAll(newIngs)
                        ingredientInput = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    if (ingredientInput.isNotBlank()) {
                        val newIngs = ingredientInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        ingredients.addAll(newIngs)
                        ingredientInput = ""
                    }
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Ingredients display - scrollable if many
        Box(modifier = Modifier.heightIn(max = 120.dp)) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ingredients.forEach { ingredient ->
                    InputChip(
                        selected = true,
                        onClick = { ingredients.remove(ingredient) },
                        label = { Text(ingredient) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        val recommendations by viewModel.publicSearchResults.collectAsState()

        // Results area - flexible weight
        Box(modifier = Modifier.weight(1f)) {
            if (recommendations.isNotEmpty()) {
                Column {
                    Text(
                        "Top Matches for your Fridge:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recommendations) { recipe ->
                            RecipeItemRow(
                                recipe = recipe,
                                onDelete = null,
                                onClick = { onStartCooking(recipe) }
                            )
                        }
                    }
                }
            } else if (!isGenerating && ingredients.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyState(
                        message = "No exact matches found.\nTry adding fewer ingredients or more basic ones!",
                        icon = Icons.Default.Kitchen
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.generateRecipeFromIngredients(ingredients.toList()) },
            enabled = ingredients.isNotEmpty() && !isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Searching your fridge...")
            } else {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (recommendations.isEmpty()) "Find Best Matches" else "Refresh Suggestions", fontWeight = FontWeight.Bold)
            }
        }
    }
}
