plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    id("application")
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.server.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.10")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.10")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.10")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.10")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.10")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation("org.postgresql:postgresql:42.7.2")
}

kotlin {
    jvmToolchain(11)
}
