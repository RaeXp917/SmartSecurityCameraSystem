plugins {
    id("java")
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    kotlin("jvm") version "1.9.22"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

// --- Version Definitions ---
val javafxVersion = "17.0.6"
val ktor_version = "2.3.11"
val logback_version = "1.4.14"
val kotlin_version = "1.9.22" // Define kotlin version

// Define the main class for the application plugin
application {
    mainClass.set("org.example.SmartRecognitionApp")
}

dependencies {
    // --- CRITICAL FIX: Add the Kotlin Standard Library ---
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")


    // --- Ktor Web Server Dependencies ---
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-gson-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // --- Your Existing Dependencies (Unchanged) ---
    implementation("org.bytedeco:javacv-platform:1.5.10")
    implementation("org.bytedeco:opencv-platform:4.9.0-1.5.10")
    implementation("org.postgresql:postgresql:42.7.7") // The new PostgreSQL driver
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = javafxVersion
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing", "javafx.graphics")
}

tasks.test {
    useJUnitPlatform()
}