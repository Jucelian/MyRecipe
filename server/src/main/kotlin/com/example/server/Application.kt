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

@Serializable
data class TokenResponse(
    val status: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val createdAt: Long? = null,
    val message: String? = null
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val releaseNotes: String
)

fun main() {
    initDatabase() // Initialize DB before starting server
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "chefmate-secret-key-1234567890"
    val jwtIssuer = "https://chefmate.com/"
    val jwtAudience = "chefmate-users"
    val jwtRealm = "Access to recipes"

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Increase max request size for videos (e.g., 50MB)
    // Note: Render free tier might have its own limits
    // install(RequestSizeLimit) { maxContentLength = 52428800 } 
    // In Ktor 3.x, use double configuration or plugins if needed. 
    // Actually, by default it's usually enough for smaller clips, but let's check.
    // For now I'll leave it as is unless it fails, but I'll add a comment.

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
    
    fun generateAccessToken(username: String): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("username", username)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 900000)) // 15 minutes
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    fun generateRefreshToken(username: String): String {
        val token = java.util.UUID.randomUUID().toString()
        val expiry = System.currentTimeMillis() + 2592000000 // 30 days
        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.username] = username
                it[RefreshTokens.token] = token
                it[RefreshTokens.expiresAt] = expiry
            }
        }
        return token
    }
    
    // DB already initialized in main
    val uploadDir = File("uploads")
    if (!uploadDir.exists()) uploadDir.mkdirs()
    
    fun getUpdateFile(name: String): File? {
        val paths = listOf(
            File("updates", name),
            File("server/updates", name),
            File("../updates", name),
            File("../../updates", name),
            File("server/src/main/resources/updates", name),
            File("src/main/resources/updates", name)
        )
        println("DEBUG: Searching for $name in ${paths.size} locations...")
        for (file in paths) {
            val exists = file.exists()
            println("CHECK: ${file.absolutePath} - Exists: $exists")
            if (exists) return file
        }
        return null
    }

    fun getVersionProps(): Properties {
        val props = Properties()
        try {
            val resourceStream = object {}.javaClass.classLoader.getResourceAsStream("version.properties")
            if (resourceStream != null) {
                props.load(resourceStream)
                println("SUCCESS: Loaded version.properties from resources. Code: ${props.getProperty("versionCode")}")
            } else {
                println("WARNING: version.properties not found in resources. Trying filesystem...")
                val file = File("version.properties")
                if (file.exists()) {
                    file.inputStream().use { props.load(it) }
                }
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load version.properties: ${e.message}")
        }
        return props
    }

    routing {
        get("/app/version") {
            val host = call.request.host()
            val proto = call.request.headers["X-Forwarded-Proto"] ?: "http"
            val baseUrl = "$proto://$host"
            
            val props = getVersionProps()
            val vCode = props.getProperty("versionCode", "1").toInt()
            val vName = props.getProperty("versionName", "1.0")
            val notes = props.getProperty("releaseNotes", "New update available")
            
            call.respond(UpdateInfo(
                versionCode = vCode, 
                versionName = vName,
                updateUrl = "$baseUrl/app/download",
                releaseNotes = notes
            ))
        }

        get("/app/download") {
            val cwd = File(".").absolutePath
            println("DOWNLOAD: Request received. CWD: $cwd")
            
            val releaseFile = getUpdateFile("ChefMate-release.apk")
            val debugFile = getUpdateFile("ChefMate-debug.apk")
            
            val file = releaseFile ?: debugFile
            
            if (file != null && file.exists()) {
                println("DOWNLOAD: Serving file ${file.absolutePath} (${file.length()} bytes)")
                call.respondFile(file)
            } else {
                println("DOWNLOAD ERROR: No APK found in expected locations.")
                call.respond(HttpStatusCode.NotFound, "Update APK not found on server")
            }
        }

        // Custom route for /uploads to handle local vs Supabase fallback
        get("/uploads/{name}") {
            val fileName = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val file = File(uploadDir, fileName)
            
            if (file.exists()) {
                // If it exists locally (Fast), serve it
                call.respondFile(file)
            } else {
                // If missing (Render wiped it), fallback to Supabase
                val supabaseUrl = System.getenv("SUPABASE_URL")?.removeSuffix("/") ?: ""
                if (supabaseUrl.isNotBlank()) {
                    val fallbackUrl = "$supabaseUrl/storage/v1/object/public/recipe-media/$fileName"
                    call.respondRedirect(fallbackUrl)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        get("/debug/apk") {
            val name = "ChefMate-debug.apk"
            val paths = listOf(
                File("updates", name),
                File("server/updates", name),
                File("../updates", name),
                File("../../updates", name),
                File("server/src/main/resources/updates", name),
                File("src/main/resources/updates", name)
            )
            val results = paths.map { it.absolutePath to it.exists() }
            call.respond(mapOf("searching_for" to name, "checks" to results))
        }

        get("/debug/files") {
            fun listAllFiles(dir: File): List<String> {
                return dir.listFiles()?.flatMap { file ->
                    if (file.isDirectory) {
                        if (file.name == ".git" || file.name == ".gradle" || file.name == ".idea" || file.name == "build") {
                            emptyList()
                        } else {
                            listAllFiles(file).map { "${file.name}/$it" }
                        }
                    } else {
                        listOf(file.name)
                    }
                } ?: emptyList()
            }
            val files = listAllFiles(File("."))
            call.respond(mapOf("working_dir" to File(".").absolutePath, "files" to files))
        }

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

        authenticate("auth-jwt") {
            val supabaseService = SupabaseService()
            post("/upload") {
                try {
                    val multipart = call.receiveMultipart()
                    var fileName = ""
                    var fileBytes: ByteArray? = null
                    
                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalName = part.originalFileName ?: "file.bin"
                            val extension = originalName.substringAfterLast(".", "bin")
                            fileName = "file_${java.util.UUID.randomUUID()}.$extension"
                            
                            // Read bytes from the channel
                            val channel = part.provider()
                            fileBytes = channel.readRemaining().readByteArray()
                        }
                        part.dispose()
                    }
                    
                    if (fileName.isNotEmpty() && fileBytes != null) {
                        println("UPLOAD: Received file $fileName (${fileBytes!!.size} bytes)")
                        
                        // 1. Save locally for fast access
                        val file = File(uploadDir, fileName)
                        file.writeBytes(fileBytes!!)
                        println("UPLOAD: Saved locally to ${file.absolutePath}")
                        
                        // 2. Upload to Supabase as backup
                        val supabaseUrl = supabaseService.uploadFile(fileName, fileBytes!!)
                        if (supabaseUrl != null) {
                            println("UPLOAD: Successfully backed up to Supabase: $supabaseUrl")
                        } else {
                            println("UPLOAD ERROR: Supabase backup failed for $fileName")
                        }
                        
                        // 3. Return the LOCAL server URL as primary
                        val url = "/uploads/$fileName"
                        call.respond(mapOf("url" to url))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No file found in request")
                    }
                } catch (e: Exception) {
                    println("UPLOAD ERROR: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                }
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
            val hashedPassword = BCrypt.withDefaults().hashToString(12, user.password.toCharArray())
            val now = System.currentTimeMillis()
            try {
                transaction {
                    Users.insert {
                        it[Users.username] = user.username
                        it[Users.password] = hashedPassword
                        it[Users.createdAt] = now
                    }
                }
                val accessToken = generateAccessToken(user.username)
                val refreshToken = generateRefreshToken(user.username)
                call.respond(TokenResponse("success", accessToken, refreshToken, now))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, TokenResponse("error", message = "Username already exists"))
            }
        }

        post("/login") {
            val credentials = call.receive<UserDTO>()
            val userData = transaction {
                Users.selectAll().where { Users.username eq credentials.username }
                    .map { Triple(it[Users.password], it[Users.createdAt], it[Users.username]) }
                    .singleOrNull()
            }
            
            if (userData != null) {
                val storedPassword = userData.first
                val userCreatedAt = userData.second
                var loginSuccessful = false
                
                // 1. Try BCrypt (New secure way)
                try {
                    if (BCrypt.verifyer().verify(credentials.password.toCharArray(), storedPassword).verified) {
                        loginSuccessful = true
                    }
                } catch (e: Exception) {
                    // Not a BCrypt hash, will try plain text check next
                }

                // 2. Try Plain Text (Old way - for auto-migration)
                if (!loginSuccessful && storedPassword == credentials.password) {
                    loginSuccessful = true
                    // Auto-migrate to secure hash
                    val newHash = BCrypt.withDefaults().hashToString(12, credentials.password.toCharArray())
                    transaction {
                        Users.update({ Users.username eq credentials.username }) {
                            it[password] = newHash
                        }
                    }
                    println("AUTO-MIGRATION: User ${credentials.username} password has been hashed and secured.")
                }

                if (loginSuccessful) {
                    val accessToken = generateAccessToken(credentials.username)
                    val refreshToken = generateRefreshToken(credentials.username)
                    call.respond(TokenResponse("success", accessToken, refreshToken, userCreatedAt))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, TokenResponse("failure", message = "Invalid username or password"))
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, TokenResponse("failure", message = "Invalid username or password"))
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val tokenData = transaction {
                RefreshTokens.selectAll().where { RefreshTokens.token eq request.refreshToken }
                    .map { it[RefreshTokens.username] to it[RefreshTokens.expiresAt] }
                    .singleOrNull()
            }

            if (tokenData != null && tokenData.second > System.currentTimeMillis()) {
                val newAccessToken = generateAccessToken(tokenData.first)
                call.respond(TokenResponse("success", accessToken = newAccessToken))
            } else {
                call.respond(HttpStatusCode.Unauthorized, TokenResponse("error", message = "Invalid or expired refresh token"))
            }
        }

        authenticate("auth-jwt") {
            route("/recipes") {
                get("/{owner}") {
                    val principal = call.principal<JWTPrincipal>()
                    val jwtUsername = principal!!.payload.getClaim("username").asString()
                    val owner = call.parameters["owner"] ?: return@get call.respond(emptyList<RecipeDTO>())
                    
                    if (jwtUsername != owner) {
                        return@get call.respond(HttpStatusCode.Forbidden, "Unauthorized access to recipes")
                    }

                    val recipesList = transaction {
                        Recipes.selectAll().where { Recipes.owner eq owner }.map { row ->
                            RecipeDTO(
                                id = row[Recipes.id],
                                title = row[Recipes.title],
                                description = row[Recipes.description],
                                ingredients = row[Recipes.ingredients]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                                instructions = row[Recipes.instructions]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                                imageUri = row[Recipes.imageUri],
                                videoUri = row[Recipes.videoUri],
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
                        val principal = call.principal<JWTPrincipal>()
                        val jwtUsername = principal!!.payload.getClaim("username").asString()
                        
                        if (jwtUsername != recipe.owner) {
                            return@post call.respond(HttpStatusCode.Forbidden, "Unauthorized owner for recipe")
                        }

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
                                    it[videoUri] = recipe.videoUri
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
                                    it[videoUri] = recipe.videoUri
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
                    val principal = call.principal<JWTPrincipal>()
                    val jwtUsername = principal!!.payload.getClaim("username").asString()

                    val recipeOwner = transaction {
                        Recipes.selectAll().where { Recipes.id eq recipeId }.map { it[Recipes.owner] }.singleOrNull()
                    }

                    if (recipeOwner != null && jwtUsername != recipeOwner) {
                        return@delete call.respond(HttpStatusCode.Forbidden, "Unauthorized deletion")
                    }

                    transaction {
                        Recipes.deleteWhere { Recipes.id eq recipeId }
                    }
                    call.respond(mapOf("status" to "success"))
                }
            }
            
            route("/categories") {
                get("/{owner}") {
                    val principal = call.principal<JWTPrincipal>()
                    val jwtUsername = principal!!.payload.getClaim("username").asString()
                    val ownerParam = call.parameters["owner"] ?: ""
                    
                    if (jwtUsername != ownerParam) {
                        return@get call.respond(HttpStatusCode.Forbidden, "Unauthorized access to categories")
                    }

                    val cats = transaction {
                        Categories.selectAll().where { Categories.owner eq ownerParam }.map { row ->
                            CategoryDTO(row[Categories.id], row[Categories.name], row[Categories.owner])
                        }
                    }
                    call.respond(cats)
                }
                
                post {
                    val cat = call.receive<CategoryDTO>()
                    val principal = call.principal<JWTPrincipal>()
                    val jwtUsername = principal!!.payload.getClaim("username").asString()

                    if (jwtUsername != cat.owner) {
                        return@post call.respond(HttpStatusCode.Forbidden, "Unauthorized owner for category")
                    }

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
                    val principal = call.principal<JWTPrincipal>()
                    val jwtUsername = principal!!.payload.getClaim("username").asString()

                    val catOwner = transaction {
                        Categories.selectAll().where { Categories.id eq catId }.map { it[Categories.owner] }.singleOrNull()
                    }

                    if (catOwner != null && jwtUsername != catOwner) {
                        return@delete call.respond(HttpStatusCode.Forbidden, "Unauthorized deletion")
                    }

                    transaction {
                        Categories.deleteWhere { Categories.id eq catId }
                    }
                    call.respond(mapOf("status" to "success"))
                }
            }
        }
    }
}
