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
import io.ktor.utils.io.*
import kotlinx.io.*
import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Properties
import java.net.URL
import java.net.URLEncoder

@Serializable
data class RecipeDTO(
    val id: String,
    val title: String = "",
    val description: String? = "",
    val ingredients: List<String>? = emptyList(),
    val instructions: List<String>? = emptyList(),
    @SerialName("imageUri")
    val imageUri: String? = null,
    @SerialName("videoUri")
    val videoUri: String? = null,
    val rating: Double? = 0.0,
    val tags: List<String>? = emptyList(),
    val category: String? = "General",
    @SerialName("isFavorite")
    val isFavorite: Boolean = false,
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("isPublic")
    val isPublic: Boolean = false
)

@Serializable
data class UserDTO(
    val username: String, 
    val password: String = "", 
    @SerialName("avatarUri")
    val avatarUri: String? = null
)

@Serializable
data class CategoryDTO(
    val id: String,
    val name: String = "",
    val owner: String = ""
)

@Serializable
data class ShoppingItemDTO(
    val id: String,
    val name: String = "",
    val quantity: String? = "",
    val category: String = "Other",
    @SerialName("isChecked")
    val isChecked: Boolean = false,
    val owner: String = ""
)

@Serializable
data class MealPlanDTO(
    val id: String,
    val recipeId: String = "",
    val recipeTitle: String = "",
    val date: Long = 0L,
    val mealType: String = "",
    val owner: String = ""
)

@Serializable
data class TokenResponse(
    val status: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val createdAt: Long? = null,
    val avatarUri: String? = null,
    val message: String? = null
)

@Serializable
data class AiGenerateRequest(val prompt: String)

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val releaseNotes: String
)

fun main() {
    initDatabase()
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "chefmate-secret-key-1234567890"
    val jwtIssuer = "https://chefmate.com/"
    val jwtAudience = "chefmate-users"

    install(ContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Access to recipes"
            verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).withAudience(jwtAudience).withIssuer(jwtIssuer).build())
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") JWTPrincipal(credential.payload) else null
            }
        }
    }
    
    fun generateAccessToken(username: String) = JWT.create()
        .withAudience(jwtAudience).withIssuer(jwtIssuer).withClaim("username", username)
        .withExpiresAt(java.util.Date(System.currentTimeMillis() + 900000)).sign(Algorithm.HMAC256(jwtSecret))

    fun generateRefreshToken(username: String): String {
        val token = java.util.UUID.randomUUID().toString()
        transaction { RefreshTokens.insert { it[RefreshTokens.username] = username; it[RefreshTokens.token] = token; it[RefreshTokens.expiresAt] = System.currentTimeMillis() + 2592000000 } }
        return token
    }
    
    val uploadDir = File("uploads")
    if (!uploadDir.exists()) uploadDir.mkdirs()

    routing {
        get("/") { call.respondText("ChefMate Server is Live!") }
        get("/health") { call.respond(mapOf("status" to "ok")) }
        
        get("/app/version") {
            val props = Properties()
            object {}.javaClass.classLoader.getResourceAsStream("version.properties")?.use { props.load(it) }
            call.respond(UpdateInfo(
                props.getProperty("versionCode", "1").toInt(),
                props.getProperty("versionName", "1.0"),
                "https://${call.request.host()}/app/download",
                props.getProperty("releaseNotes", "New update")
            ))
        }

        get("/uploads/{name}") {
            val file = File(uploadDir, call.parameters["name"] ?: "")
            if (file.exists()) call.respondFile(file) else call.respondRedirect("${System.getenv("SUPABASE_URL")}/storage/v1/object/public/recipe-media/${file.name}")
        }

        post("/signup") {
            val user = call.receive<UserDTO>()
            val now = System.currentTimeMillis()
            try {
                transaction { Users.insert { it[username] = user.username; it[password] = BCrypt.withDefaults().hashToString(12, user.password.toCharArray()); it[createdAt] = now } }
                call.respond(TokenResponse("success", generateAccessToken(user.username), generateRefreshToken(user.username), now))
            } catch (e: Exception) { call.respond(HttpStatusCode.Conflict, mapOf("message" to "Exists")) }
        }

        post("/login") {
            val credentials = call.receive<UserDTO>()
            val userData = transaction { Users.selectAll().where { Users.username eq credentials.username }.map { Triple(it[Users.password], it[Users.createdAt], it[Users.avatarUri]) }.singleOrNull() }
            if (userData != null && BCrypt.verifyer().verify(credentials.password.toCharArray(), userData.first).verified) {
                call.respond(TokenResponse("success", generateAccessToken(credentials.username), generateRefreshToken(credentials.username), userData.second, userData.third))
            } else call.respond(HttpStatusCode.Unauthorized)
        }

        authenticate("auth-jwt") {
            // ALL AUTHENTICATED ROUTES CONSOLIDATED HERE
            
            // 1. User Update
            post("/user/update") {
                val update = call.receive<UserDTO>()
                val username = call.principal<JWTPrincipal>()!!.payload.getClaim("username").asString()
                transaction { Users.update({ Users.username eq username }) { 
                    if (update.avatarUri != null) it[avatarUri] = update.avatarUri
                    if (update.password.isNotBlank()) it[password] = BCrypt.withDefaults().hashToString(12, update.password.toCharArray())
                }}
                call.respond(mapOf("status" to "success"))
            }

            // 2. AI Image Generation
            post("/ai/generate") {
                try {
                    val request = call.receive<AiGenerateRequest>()
                    val seed = (Math.random() * 1000000).toInt()
                    
                    // Ultra-refined vector icon prompt
                    val refinedPrompt = "Clean vector icon of ${request.prompt}, professional logo style, flat design, minimalist, 2D, white background, no humans, no people, no hands, centered, high quality, masterpiece"
                    
                    val imageUrl = "https://image.pollinations.ai/prompt/${URLEncoder.encode(refinedPrompt, "UTF-8")}?width=512&height=512&seed=$seed&nologo=true&model=flux"
                    
                    val bytes = URL(imageUrl).readBytes()
                    val fileName = "ai_${java.util.UUID.randomUUID()}.jpg"
                    File(uploadDir, fileName).writeBytes(bytes)
                    SupabaseService().uploadFile(fileName, bytes)
                    call.respond(mapOf("url" to "/uploads/$fileName"))
                } catch (e: Exception) { call.respond(HttpStatusCode.InternalServerError, e.message ?: "AI Error") }
            }

            // 3. Recipes
            route("/recipes") {
                get("/community") {
                    val list = transaction {
                        Recipes.selectAll().where { Recipes.isPublic eq true }.limit(50).map { row ->
                            RecipeDTO(row[Recipes.id], row[Recipes.title], row[Recipes.description], row[Recipes.ingredients]?.split("|"), row[Recipes.instructions]?.split("|"), row[Recipes.imageUri], row[Recipes.videoUri], row[Recipes.rating], row[Recipes.tags]?.split("|"), row[Recipes.category], row[Recipes.isFavorite], row[Recipes.owner], row[Recipes.createdAt], row[Recipes.isPublic])
                        }
                    }
                    call.respond(list)
                }
                
                get("/{owner}") {
                    val owner = call.parameters["owner"] ?: ""
                    val list = transaction { Recipes.selectAll().where { Recipes.owner eq owner }.map { row ->
                        RecipeDTO(row[Recipes.id], row[Recipes.title], row[Recipes.description], row[Recipes.ingredients]?.split("|"), row[Recipes.instructions]?.split("|"), row[Recipes.imageUri], row[Recipes.videoUri], row[Recipes.rating], row[Recipes.tags]?.split("|"), row[Recipes.category], row[Recipes.isFavorite], row[Recipes.owner], row[Recipes.createdAt], row[Recipes.isPublic])
                    }}
                    call.respond(list)
                }
                post {
                    val r = call.receive<RecipeDTO>()
                    transaction {
                        val exists = Recipes.selectAll().where { Recipes.id eq r.id }.any()
                        if (exists) {
                            Recipes.update({ Recipes.id eq r.id }) { it[title] = r.title; it[description] = r.description ?: ""; it[ingredients] = r.ingredients?.joinToString("|") ?: ""; it[instructions] = r.instructions?.joinToString("|") ?: ""; it[imageUri] = r.imageUri; it[videoUri] = r.videoUri; it[rating] = r.rating ?: 0.0; it[tags] = r.tags?.joinToString("|") ?: ""; it[category] = r.category ?: "General"; it[isFavorite] = r.isFavorite; it[owner] = r.owner; it[createdAt] = r.createdAt; it[isPublic] = r.isPublic }
                        } else {
                            Recipes.insert { it[id] = r.id; it[title] = r.title; it[description] = r.description ?: ""; it[ingredients] = r.ingredients?.joinToString("|") ?: ""; it[instructions] = r.instructions?.joinToString("|") ?: ""; it[imageUri] = r.imageUri; it[videoUri] = r.videoUri; it[rating] = r.rating ?: 0.0; it[tags] = r.tags?.joinToString("|") ?: ""; it[category] = r.category ?: "General"; it[isFavorite] = r.isFavorite; it[owner] = r.owner; it[createdAt] = r.createdAt; it[isPublic] = r.isPublic }
                        }
                    }
                    call.respond(mapOf("status" to "success"))
                }
                delete("/{id}") {
                    transaction { Recipes.deleteWhere { Recipes.id eq (call.parameters["id"] ?: "") } }
                    call.respond(mapOf("status" to "success"))
                }
            }

            // 4. Categories
            route("/categories") {
                get("/{owner}") {
                    val owner = call.parameters["owner"] ?: ""
                    val list = transaction { Categories.selectAll().where { Categories.owner eq owner }.map { CategoryDTO(it[Categories.id], it[Categories.name], it[Categories.owner]) } }
                    call.respond(list)
                }
                post {
                    val c = call.receive<CategoryDTO>()
                    transaction {
                        if (Categories.selectAll().where { Categories.id eq c.id }.any()) Categories.update({ Categories.id eq c.id }) { it[name] = c.name }
                        else Categories.insert { it[id] = c.id; it[name] = c.name; it[owner] = c.owner }
                    }
                    call.respond(mapOf("status" to "success"))
                }
                delete("/{id}") {
                    transaction { Categories.deleteWhere { Categories.id eq (call.parameters["id"] ?: "") } }
                    call.respond(mapOf("status" to "success"))
                }
            }

            // 5. Shopping List
            route("/shopping") {
                get("/{owner}") {
                    val owner = call.parameters["owner"] ?: ""
                    val list = transaction { ShoppingList.selectAll().where { ShoppingList.owner eq owner }.map { row ->
                        ShoppingItemDTO(row[ShoppingList.id], row[ShoppingList.name], row[ShoppingList.quantity], row[ShoppingList.category], row[ShoppingList.isChecked], row[ShoppingList.owner])
                    }}
                    call.respond(list)
                }
                post {
                    val item = call.receive<ShoppingItemDTO>()
                    transaction {
                        val exists = ShoppingList.selectAll().where { ShoppingList.id eq item.id }.any()
                        if (exists) {
                            ShoppingList.update({ ShoppingList.id eq item.id }) {
                                it[name] = item.name
                                it[quantity] = item.quantity
                                it[category] = item.category
                                it[isChecked] = item.isChecked
                            }
                        } else {
                            ShoppingList.insert {
                                it[id] = item.id
                                it[name] = item.name
                                it[quantity] = item.quantity
                                it[category] = item.category
                                it[isChecked] = item.isChecked
                                it[owner] = item.owner
                            }
                        }
                    }
                    call.respond(mapOf("status" to "success"))
                }
                delete("/{id}") {
                    transaction { ShoppingList.deleteWhere { ShoppingList.id eq (call.parameters["id"] ?: "") } }
                    call.respond(mapOf("status" to "success"))
                }
                delete("/clear/{owner}") {
                    val owner = call.parameters["owner"] ?: ""
                    transaction { ShoppingList.deleteWhere { (ShoppingList.owner eq owner) and (ShoppingList.isChecked eq true) } }
                    call.respond(mapOf("status" to "success"))
                }
            }

            // 6. Meal Planner
            route("/planner") {
                get("/{owner}") {
                    val owner = call.parameters["owner"] ?: ""
                    val list = transaction { MealPlans.selectAll().where { MealPlans.owner eq owner }.map { row ->
                        MealPlanDTO(row[MealPlans.id], row[MealPlans.recipeId], row[MealPlans.recipeTitle], row[MealPlans.date], row[MealPlans.mealType], row[MealPlans.owner])
                    }}
                    call.respond(list)
                }
                post {
                    val plan = call.receive<MealPlanDTO>()
                    transaction {
                        val exists = MealPlans.selectAll().where { MealPlans.id eq plan.id }.any()
                        if (exists) {
                            MealPlans.update({ MealPlans.id eq plan.id }) {
                                it[recipeId] = plan.recipeId
                                it[recipeTitle] = plan.recipeTitle
                                it[date] = plan.date
                                it[mealType] = plan.mealType
                            }
                        } else {
                            MealPlans.insert {
                                it[id] = plan.id
                                it[recipeId] = plan.recipeId
                                it[recipeTitle] = plan.recipeTitle
                                it[date] = plan.date
                                it[mealType] = plan.mealType
                                it[owner] = plan.owner
                            }
                        }
                    }
                    call.respond(mapOf("status" to "success"))
                }
                delete("/{id}") {
                    transaction { MealPlans.deleteWhere { MealPlans.id eq (call.parameters["id"] ?: "") } }
                    call.respond(mapOf("status" to "success"))
                }
            }
            
            // 7. Raw File Upload
            post("/upload") {
                val multipart = call.receiveMultipart()
                var name = ""; var bytes: ByteArray? = null
                multipart.forEachPart { if (it is PartData.FileItem) { name = "f_${java.util.UUID.randomUUID()}.${it.originalFileName?.substringAfterLast(".") ?: "bin"}"; bytes = it.provider().readRemaining().readByteArray() }; it.dispose() }
                if (bytes != null) { File(uploadDir, name).writeBytes(bytes!!); SupabaseService().uploadFile(name, bytes!!); call.respond(mapOf("url" to "/uploads/$name")) }
                else call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
