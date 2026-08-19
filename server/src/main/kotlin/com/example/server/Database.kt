package com.example.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Recipes : Table("recipes") {
    val id = varchar("id", 50)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val ingredients = text("ingredients").nullable()
    val instructions = text("instructions").nullable()
    val imageUri = varchar("imageUri", 1024).nullable()
    val videoUri = varchar("videoUri", 1024).nullable()
    val rating = double("rating").nullable()
    val tags = text("tags").nullable()
    val category = varchar("category", 100).nullable()
    val isFavorite = bool("isFavorite")
    val owner = varchar("owner", 50)
    val createdAt = long("createdAt").default(System.currentTimeMillis())
    val isPublic = bool("is_public").default(false)

    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val username = varchar("username", 50)
    val password = varchar("password", 255)
    val avatarUri = varchar("avatarUri", 1024).nullable()
    val createdAt = long("createdate").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(username)
}

object Categories : Table("categories") {
    val id = varchar("id", 50)
    val name = varchar("name", 100)
    val owner = varchar("owner", 50)

    override val primaryKey = PrimaryKey(id)
}

object ShoppingList : Table("shopping_list") {
    val id = varchar("id", 50)
    val name = varchar("name", 255)
    val quantity = varchar("quantity", 100)
    val category = varchar("category", 100)
    val isChecked = bool("is_checked")
    val owner = varchar("owner", 50)

    override val primaryKey = PrimaryKey(id)
}

object MealPlans : Table("meal_plans") {
    val id = varchar("id", 50)
    val recipeId = varchar("recipe_id", 50)
    val recipeTitle = varchar("recipe_title", 255)
    val date = long("date")
    val mealType = varchar("meal_type", 20)
    val owner = varchar("owner", 50)

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50)
    val token = varchar("token", 512)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}

fun initDatabase() {
    val cloudDbUrl = System.getenv("DATABASE_URL")
    try {
        if (cloudDbUrl != null) {
            println("Connecting to Cloud Database: ${cloudDbUrl.take(20)}...")
            // Explicitly load the driver
            Class.forName("org.postgresql.Driver")
            Database.connect(cloudDbUrl, driver = "org.postgresql.Driver")
        } else {
            println("Connecting to Local Database...")
            val dbPath = System.getenv("DATABASE_PATH") ?: "./data/recipes"
            Database.connect("jdbc:h2:file:$dbPath;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        }

        transaction {
            SchemaUtils.createMissingTablesAndColumns(Recipes, Users, Categories, ShoppingList, MealPlans, RefreshTokens)
        }
        println("Database initialized successfully!")
    } catch (e: Exception) {
        println("DATABASE ERROR: ${e.message}")
        e.printStackTrace()
    }
}
