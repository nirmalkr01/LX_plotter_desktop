plugins {
    kotlin("jvm") version "2.1.0" // Or your current version
    id("org.jetbrains.compose") version "1.7.0" // The Engine for Desktop UI
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.lxplotter"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // This downloads the UI libraries for Windows, Mac, and Linux automatically
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "MainKt" // Matches your Main.kt file name
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "LXPlotter"
            packageVersion = "1.0.0"
        }
    }
}