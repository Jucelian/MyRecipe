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
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class RecipeDTO(
    val id: String,
    val title: String = "",
    val description: String? = "",
    val ingredients: List<String>? = emptyList(),
    val instructions: List<String>? = emptyList(),
    @SerialName("imageUri")
    val imageUri: String? = null,
    val rating: Double? = 0.0,
    val tags: List<String>? = emptyList(),
    val category: String? = "General",
    @SerialName("isFavorite")
    val isFavorite: Boolean = false,
    val owner: String = ""
)

@Serializable
data class UserDTO(val username: String, val password: String)

@Serializable
data class CategoryDTO(
    val id: String,
    val name: String = "",
    val owner: String = ""
)

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

        get("/debug/db") {
            try {
                val recipeCount = transaction { Recipes.selectAll().count() }
                val categoryCount = transaction { Categories.selectAll().count() }
                call.respond(mapOf(
                    "recipes_count" to recipeCount.toString(),
                    "categories_count" to categoryCount.toString(),
                    "database" to "connected"
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown DB error")))
            }
        }

        get("/") {
            call.respondText("Server is running!")
        }

        post("/upload") {
            try {
                val multipart = call.receiveMultipart()
                var fileName = ""
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalName = part.originalFileName ?: "image.jpg"
                        val name = "img_${System.currentTimeMillis()}_${originalName.replace("\\s".toRegex(), "_")}"
                        println("Receiving file: $name")
                        val file = File(uploadDir, name)
                        
                        try {
                            val channel = part.provider()
                            file.outputStream().use { output ->
                                channel.copyTo(output)
                            }
                            fileName = name
                        } catch (writeError: Exception) {
                            println("Disk Write Error: ${writeError.message}")
                            throw writeError
                        }
                    }
                    part.dispose()
                }
                if (fileName.isNotEmpty()) {
                    val url = "/uploads/$fileName"
                    println("File uploaded successfully. Relative URL: $url")
                    call.respond(mapOf("url" to url))
                } else {
                    println("No file found in multipart request")
                    call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                }
            } catch (e: Exception) {
                val errorDetails = e.message ?: "Unknown upload error"
                println("UPLOAD ERROR: $errorDetails")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, errorDetails)
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
                            ingredients = row[Recipes.ingredients]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                            instructions = row[Recipes.instructions]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                            imageUri = row[Recipes.imageUri],
                            rating = row[Recipes.rating],
                            tags = row[Recipes.tags]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                            category = row[Recipes.category],
                            isFavorite = row[Recipes.isFavorite],
                            owner = row[Recipes.owner]
                        )
                    }
                }
                call.respond(recipesList)
            }

            post {
                try {
                    val recipe = call.receive<RecipeDTO>()
                    println("Received recipe for sync: title='${recipe.title}', id='${recipe.id}', imageUri='${recipe.imageUri}'")
                    transaction {
                        println("Database operation for recipe: ${recipe.id}")
                        val exists = Recipes.selectAll().where { Recipes.id eq recipe.id }.any()
                        if (exists) {
                            Recipes.update({ Recipes.id eq recipe.id }) {
                                it[title] = recipe.title
                                it[description] = recipe.description ?: ""
                                it[ingredients] = recipe.ingredients?.joinToString("|") ?: ""
                                it[instructions] = recipe.instructions?.joinToString("|") ?: ""
                                it[imageUri] = recipe.imageUri
                                it[rating] = recipe.rating ?: 0.0
                                it[tags] = recipe.tags?.joinToString("|") ?: ""
                                it[category] = recipe.category ?: "General"
                                it[isFavorite] = recipe.isFavorite
                                it[owner] = recipe.owner
                            }
                            println("Recipe ${recipe.id} updated successfully")
                        } else {
                            Recipes.insert {
                                it[id] = recipe.id
                                it[title] = recipe.title
                                it[description] = recipe.description ?: ""
                                it[ingredients] = recipe.ingredients?.joinToString("|") ?: ""
                                it[instructions] = recipe.instructions?.joinToString("|") ?: ""
                                it[imageUri] = recipe.imageUri
                                it[rating] = recipe.rating ?: 0.0
                                it[tags] = recipe.tags?.joinToString("|") ?: ""
                                it[category] = recipe.category ?: "General"
                                it[isFavorite] = recipe.isFavorite
                                it[owner] = recipe.owner
                            }
                            println("Recipe ${recipe.id} inserted successfully")
                        }
                    }
                    call.respond(mapOf("status" to "success"))
                } catch (e: Exception) {
                    println("ERROR IN POST /recipes: ${e.message}")
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to (e.message ?: "Unknown error")))
                }
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
