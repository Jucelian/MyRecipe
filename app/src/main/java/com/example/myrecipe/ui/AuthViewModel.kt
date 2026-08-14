package com.example.myrecipe.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.myrecipe.model.User
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        application,
        "secure_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val _isLoggedIn = mutableStateOf(sharedPreferences.getString("auth_token", null) != null)
    val isLoggedIn: State<Boolean> = _isLoggedIn

    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser

    private val _savedUsername = mutableStateOf(sharedPreferences.getString("saved_username", "") ?: "")
    val savedUsername: State<String> = _savedUsername
    
    val authToken: String? get() = sharedPreferences.getString("auth_token", null)
    private val refreshToken: String? get() = sharedPreferences.getString("refresh_token", null)

    init {
        // Restore user if logged in
        if (_isLoggedIn.value) {
            val username = sharedPreferences.getString("saved_username", "") ?: ""
            val createdAt = sharedPreferences.getLong("created_at", 0L)
            if (username.isNotBlank()) {
                _currentUser.value = User(username, "", if (createdAt != 0L) createdAt else null)
            }
        }
        initSession()
    }

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
                val user = User(username, password)
                val response = RetrofitClient.instance.signup(user)
                val accessToken = response.accessToken
                val refreshToken = response.refreshToken
                if (response.status == "success" && accessToken != null && refreshToken != null) {
                    val createdAt = response.createdAt ?: System.currentTimeMillis()
                    saveAuthData(username, accessToken, refreshToken, createdAt)
                    _currentUser.value = User(username, "", createdAt)
                    _isLoggedIn.value = true
                    resetInactivityTimer()
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
                val user = User(username, password)
                val response = RetrofitClient.instance.login(user)
                val accessToken = response.accessToken
                val refreshToken = response.refreshToken
                if (response.status == "success" && accessToken != null && refreshToken != null) {
                    val createdAt = response.createdAt ?: 0L
                    saveAuthData(username, accessToken, refreshToken, createdAt)
                    _currentUser.value = User(username, "", if (createdAt != 0L) createdAt else null)
                    _isLoggedIn.value = true
                    resetInactivityTimer()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    suspend fun tryTokenRefresh(): Boolean {
        val currentRefresh = refreshToken ?: return false
        return try {
            val response = RetrofitClient.instance.refreshToken(mapOf("refreshToken" to currentRefresh))
            val newAccess = response.accessToken
            if (response.status == "success" && newAccess != null) {
                sharedPreferences.edit().putString("auth_token", newAccess).apply()
                true
            } else {
                logout()
                false
            }
        } catch (e: Exception) {
            logout()
            false
        }
    }

    private fun saveAuthData(username: String, accessToken: String, refreshToken: String, createdAt: Long) {
        sharedPreferences.edit()
            .putString("saved_username", username)
            .putString("auth_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("created_at", createdAt)
            .apply()
        _savedUsername.value = username
    }

    fun logout() {
        sharedPreferences.edit()
            .remove("auth_token")
            .remove("refresh_token")
            .apply()
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    fun setSectionExpanded(sectionKey: String, isExpanded: Boolean) {
        sharedPreferences.edit().putBoolean("section_$sectionKey", isExpanded).apply()
    }

    fun isSectionExpanded(sectionKey: String, defaultValue: Boolean = true): Boolean {
        return sharedPreferences.getBoolean("section_$sectionKey", defaultValue)
    }
}
