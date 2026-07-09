package com.example.myrecipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.myrecipe.ui.AuthViewModel
import com.example.myrecipe.ui.LoginScreen
import com.example.myrecipe.ui.RecipeApp
import com.example.myrecipe.ui.RecipeViewModel
import com.example.myrecipe.ui.theme.MyRecipeTheme

class MainAppActivity : ComponentActivity() {
    private var authViewModel: AuthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyRecipeTheme {
                val viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val recipeViewModel: RecipeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                authViewModel = viewModel
                
                LaunchedEffect(Unit) {
                    viewModel.initSession()
                }

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
