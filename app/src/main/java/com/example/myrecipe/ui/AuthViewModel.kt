package com.example.myrecipe.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrecipe.model.User
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    
    private val _isLoggedIn = mutableStateOf(false)
    val isLoggedIn: State<Boolean> = _isLoggedIn

    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser

    private val _savedUsername = mutableStateOf(sharedPreferences.getString("saved_username", "") ?: "")
    val savedUsername: State<String> = _savedUsername

    private val INACTIVITY_TIMEOUT = 1800000L // 30 minutes

    fun resetInactivityTimer() {
        sharedPreferences.edit().putLong("last_activity_time", System.currentTimeMillis()).apply()
    }

    fun initSession() {
        viewModelScope.launch {
            while (true) {
                delay(10000)
                val loggedIn = _isLoggedIn.value
                val currentTime = System.currentTimeMillis()
                val lastActivity = sharedPreferences.getLong("last_activity_time", 0L)
                if (loggedIn && (currentTime - lastActivity > INACTIVITY_TIMEOUT)) {
                    logout()
                }
            }
        }
    }

    fun signup(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanUsername = username.trim().lowercase()
                val user = User(cleanUsername, password)
                val response = RetrofitClient.instance.signup(user)
                if (response["status"] == "success") {
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    resetInactivityTimer()
                    saveCredentials(cleanUsername)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun login(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanUsername = username.trim().lowercase()
                val user = User(cleanUsername, password)
                val response = RetrofitClient.instance.login(user)
                if (response["status"] == "success") {
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    resetInactivityTimer()
                    saveCredentials(cleanUsername)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    private fun saveCredentials(username: String) {
        sharedPreferences.edit().putString("saved_username", username).apply()
        _savedUsername.value = username
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = null
    }
}
