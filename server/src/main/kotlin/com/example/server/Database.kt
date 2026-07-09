package com.example.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Recipes : Table("recipes") {
    val id = varchar("id", 50)
    val title = varchar("title", 255)
    val description = text("description")
    val ingredients = text("ingredients")
    val instructions = text("instructions")
    val imageUri = varchar("imageUri", 1024).nullable()
    val rating = double("rating")
    val tags = text("tags")
    val category = varchar("category", 100)
    val isFavorite = bool("isFavorite")
    val owner = varchar("owner", 50)

    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val username = varchar("username", 50)
    val password = varchar("password", 50)

    override val primaryKey = PrimaryKey(username)
}

object Categories : Table("categories") {
    val id = varchar("id", 50)
    val name = varchar("name", 100)
    val owner = varchar("owner", 50)

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
            SchemaUtils.create(Recipes, Users, Categories)
        }
        println("Database initialized successfully!")
    } catch (e: Exception) {
        println("DATABASE ERROR: ${e.message}")
        e.printStackTrace()
    }
}
