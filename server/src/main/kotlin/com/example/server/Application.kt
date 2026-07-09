package com.example.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
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
    initDatabase() // Initialize DB before starting server
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // DB already initialized in main
    val uploadDir = File("uploads")
    if (!uploadDir.exists()) uploadDir.mkdirs()

    routing {
        staticFiles("/uploads", uploadDir)

        get("/") {
            call.respondText("Server is running!")
        }

        post("/upload") {
            val multipart = call.receiveMultipart()
            var fileName = ""
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val name = "img_${System.currentTimeMillis()}_${part.originalFileName}"
                    val file = File(uploadDir, name)
                    part.streamProvider().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    fileName = name
                }
                part.dispose()
            }
            if (fileName.isNotEmpty()) {
                call.respond(mapOf("url" to "/uploads/$fileName"))
            } else {
                call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }
        }
        
        get("/health") {
            try {
                transaction {
                    Users.selectAll().limit(1).toList()
                }
                call.respond(mapOf("status" to "up", "database" to "connected"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to e.message))
            }
        }

        post("/signup") {
            val user = call.receive<UserDTO>()
            val cleanUsername = user.username.trim().lowercase()
            transaction {
                Users.insert {
                    it[Users.username] = cleanUsername
                    it[Users.password] = user.password
                }
            }
            call.respond(mapOf("status" to "success"))
        }

        post("/login") {
            val credentials = call.receive<UserDTO>()
            val cleanUsername = credentials.username.trim().lowercase()
            val userRow = transaction {
                Users.selectAll().where { (Users.username.lowerCase() eq cleanUsername) and (Users.password eq credentials.password) }
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
                val owner = call.parameters["owner"]?.lowercase() ?: return@get call.respond(emptyList<RecipeDTO>())
                val recipesList = transaction {
                    Recipes.selectAll().where { Recipes.owner.lowerCase() eq owner }.map { row ->
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
                    val exists = Recipes.selectAll().where { Recipes.id eq recipe.id }.any()
                    if (exists) {
                        Recipes.update({ Recipes.id eq recipe.id }) {
                            it[title] = recipe.title
                            it[description] = recipe.description
                            it[ingredients] = recipe.ingredients.joinToString("|")
                            it[instructions] = recipe.instructions.joinToString("|")
                            it[imageUri] = recipe.imageUri
                            it[rating] = recipe.rating
                            it[tags] = recipe.tags.joinToString("|")
                            it[category] = recipe.category
                            it[isFavorite] = recipe.isFavorite
                            it[owner] = recipe.owner
                        }
                    } else {
                        Recipes.insert {
                            it[id] = recipe.id
                            it[title] = recipe.title
                            it[description] = recipe.description
                            it[ingredients] = recipe.ingredients.joinToString("|")
                            it[instructions] = recipe.instructions.joinToString("|")
                            it[imageUri] = recipe.imageUri
                            it[rating] = recipe.rating
                            it[tags] = recipe.tags.joinToString("|")
                            it[category] = recipe.category
                            it[isFavorite] = recipe.isFavorite
                            it[owner] = recipe.owner
                        }
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
                    val exists = Categories.selectAll().where { Categories.id eq cat.id }.any()
                    if (exists) {
                        Categories.update({ Categories.id eq cat.id }) {
                            it[name] = cat.name
                            it[owner] = cat.owner
                        }
                    } else {
                        Categories.insert {
                            it[id] = cat.id
                            it[name] = cat.name
                            it[owner] = cat.owner
                        }
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
