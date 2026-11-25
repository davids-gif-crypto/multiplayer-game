// build.gradle.kts
// The build file for the Food Fight server.
// It says which libraries we use, which Java version to compile with,
// and how to build one runnable "fat" jar that holds everything.

plugins {
    application                                   // adds the "run" task
    id("com.gradleup.shadow") version "9.6.1"     // builds the fat jar
}

group = "com.foodfight"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    // Always compile and run with Java 21, even if a newer Java is installed.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Web server: serves the static files and the WebSocket.
    implementation("io.javalin:javalin:6.7.0")
    // Reads and writes JSON messages.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    // A tiny logger so Javalin does not warn about a missing one.
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // Unit tests.
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.foodfight.Main")
}

tasks.test {
    useJUnitPlatform()
    // Print each test result so we can see what passed.
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// ./gradlew tuning prints the knockback tuning table from TuningTest.
tasks.register<Test>("tuning") {
    description = "Prints the knockback tuning table."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.foodfight.sim.TuningTest")
    }
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
    outputs.upToDateWhen { false } // always run, so the table always prints
}

tasks.shadowJar {
    // Name the jar "app.jar" so the Dockerfile can find it easily.
    archiveFileName.set("app.jar")
    mergeServiceFiles()
}
