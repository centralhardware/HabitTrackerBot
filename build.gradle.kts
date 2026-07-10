plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("com.google.cloud.tools.jib") version "3.5.3"
    application
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val flywayVersion = "12.11.0"
val mcpSdkVersion = "0.13.0"
val ktorVersion = "3.5.1"

dependencies {
    implementation("com.github.centralhardware:ktgbotapi-commons:d57cb77e")

    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.github.seratch:kotliquery:1.9.1")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("dev.inmo:krontab:2.9.0")

    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    implementation("org.apache.commons:commons-math3:3.6.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("MainKt")
}

jib {
    from {
        image = System.getenv("JIB_FROM_IMAGE") ?: "eclipse-temurin:25-jre"
    }
    container {
        mainClass = "MainKt"
        jvmFlags = listOf("-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0")
        creationTime = "USE_CURRENT_TIMESTAMP"
        user = "10001"
    }
}

tasks.test {
    useJUnitPlatform()
}