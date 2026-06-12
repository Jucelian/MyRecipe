package com.example.myrecipe.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.model.User
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    
    private val _isLoggedIn = mutableStateOf(false)
    val isLoggedIn: State<Boolean> = _isLoggedIn

    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser

    private val _savedUsername = mutableStateOf(sharedPreferences.getString("saved_username", "") ?: "")
    val savedUsername: State<String> = _savedUsername

    fun signup(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val user = User(username, password)
                val response = RetrofitClient.instance.signup(user)
                if (response["status"] == "success") {
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    saveCredentials(username)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun login(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val user = User(username, password)
                val response = RetrofitClient.instance.login(user)
                if (response["status"] == "success") {
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    saveCredentials(username)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    private fun saveCredentials(username: String) {
        sharedPreferences.edit()
            .putString("saved_username", username)
            .apply()
        _savedUsername.value = username
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = null
    }
}
