plugins {
    kotlin("jvm") version "2.3.21"
    id("com.google.cloud.tools.jib") version "3.5.3"
    application
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktgbotapiVersion = "33.1.0"
val flywayVersion = "12.6.2"
val mcpSdkVersion = "0.12.0"
val ktorVersion = "3.3.3"

dependencies {
    implementation("dev.inmo:tgbotapi:$ktgbotapiVersion")
    implementation("com.github.centralhardware:ktgbotapi-commons:8b9e69dd")

    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.github.seratch:kotliquery:1.9.1")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("dev.inmo:krontab:2.9.0")

    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("MainKt")
}

jib {
    from {
        image = System.getenv("JIB_FROM_IMAGE") ?: "eclipse-temurin:24-jre"
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
