// settings.gradle.kts
// Names the project and lets Gradle download Java 21 automatically
// if this computer does not already have it installed.

plugins {
    // The foojay resolver finds and downloads a matching JDK for the toolchain.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "multiplayer-game"
