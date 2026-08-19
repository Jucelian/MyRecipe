package com.example.myrecipe.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shopping_list")
data class ShoppingItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String = "",
    val category: String = "Other",
    val isChecked: Boolean = false,
    val owner: String = "",
    val isSynced: Boolean = false
)
