package com.example.myrecipe.model

data class User(
    val username: String,
    val password: String,
    val createdAt: Long? = null,
    val avatarUri: String? = null
)
