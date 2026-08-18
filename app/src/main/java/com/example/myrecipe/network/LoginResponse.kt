package com.example.myrecipe.network

data class LoginResponse(
    val status: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val createdAt: Long? = null,
    val avatarUri: String? = null,
    val message: String? = null
)
