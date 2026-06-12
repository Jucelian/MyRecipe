package com.example.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class RecipeDTO(
    val id: String,
    val title: String,
    val description: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUri: String?,
    val rating: Double,
    val tags: List<String>,
    val category: String,
    val isFavorite: Boolean,
    val owner: String
)

@Serializable
data class UserDTO(val username: String, val password: String)

@Serializable
data class CategoryDTO(val id: String, val name: String, val owner: String)

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }
    
    initDatabase()

    routing {
        post("/signup") {
            val user = call.receive<UserDTO>()
            transaction {
                Users.insert {
                    it[Users.username] = user.username
                    it[Users.password] = user.password
                }
            }
            call.respond(mapOf("status" to "success"))
        }

        post("/login") {
            val credentials = call.receive<UserDTO>()
            val userRow = transaction {
                Users.selectAll().where { (Users.username eq credentials.username) and (Users.password eq credentials.password) }
                    .singleOrNull()
            }
            if (userRow != null) {
                call.respond(mapOf("status" to "success"))
            } else {
                call.respond(mapOf("status" to "failure"))
            }
        }

        route("/recipes") {
            get("/{owner}") {
                val owner = call.parameters["owner"] ?: return@get call.respond(emptyList<RecipeDTO>())
                val recipesList = transaction {
                    Recipes.selectAll().where { Recipes.owner eq owner }.map { row ->
                        RecipeDTO(
                            id = row[Recipes.id],
                            title = row[Recipes.title],
                            description = row[Recipes.description],
                            ingredients = row[Recipes.ingredients].split("|").filter { it.isNotEmpty() },
                            instructions = row[Recipes.instructions].split("|").filter { it.isNotEmpty() },
                            imageUri = row[Recipes.imageUri],
                            rating = row[Recipes.rating],
                            tags = row[Recipes.tags].split("|").filter { it.isNotEmpty() },
                            category = row[Recipes.category],
                            isFavorite = row[Recipes.isFavorite],
                            owner = row[Recipes.owner]
                        )
                    }
                }
                call.respond(recipesList)
            }

            post {
                val recipe = call.receive<RecipeDTO>()
                transaction {
                    Recipes.insert {
                        it[Recipes.id] = recipe.id
                        it[Recipes.title] = recipe.title
                        it[Recipes.description] = recipe.description
                        it[Recipes.ingredients] = recipe.ingredients.joinToString("|")
                        it[Recipes.instructions] = recipe.instructions.joinToString("|")
                        it[Recipes.imageUri] = recipe.imageUri
                        it[Recipes.rating] = recipe.rating
                        it[Recipes.tags] = recipe.tags.joinToString("|")
                        it[Recipes.category] = recipe.category
                        it[Recipes.isFavorite] = recipe.isFavorite
                        it[Recipes.owner] = recipe.owner
                    }
                }
                call.respond(mapOf("status" to "success"))
            }

            delete("/{id}") {
                val recipeId = call.parameters["id"] ?: return@delete call.respond(mapOf("status" to "error"))
                transaction {
                    Recipes.deleteWhere { Recipes.id eq recipeId }
                }
                call.respond(mapOf("status" to "success"))
            }
        }
        
        route("/categories") {
            get("/{owner}") {
                val ownerParam = call.parameters["owner"] ?: ""
                val cats = transaction {
                    Categories.selectAll().where { Categories.owner eq ownerParam }.map { row ->
                        CategoryDTO(row[Categories.id], row[Categories.name], row[Categories.owner])
                    }
                }
                call.respond(cats)
            }
            
            post {
                val cat = call.receive<CategoryDTO>()
                transaction {
                    Categories.insert {
                        it[Categories.id] = cat.id
                        it[Categories.name] = cat.name
                        it[Categories.owner] = cat.owner
                    }
                }
                call.respond(mapOf("status" to "success"))
            }
            
            delete("/{id}") {
                val catId = call.parameters["id"] ?: ""
                transaction {
                    Categories.deleteWhere { Categories.id eq catId }
                }
                call.respond(mapOf("status" to "success"))
            }
        }
    }
}
