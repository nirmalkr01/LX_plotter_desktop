import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.lxplotter"
// --- MASTER VERSION CONTROL ---
// Increment this (e.g., 1.0.2) when you are ready to push the UI fixes
version = "1.0.1"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.apache.pdfbox:pdfbox:2.0.30")
    // Added JSON parsing for the update check logic in Main.kt
    implementation("org.json:json:20231013")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "LXPlotter"
            packageVersion = version.toString()

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // CRITICAL: upgradeUuid remains constant to allow the new MSI
                // to overwrite the old installation on the laptop.
                upgradeUuid = "68d37446-2491-4c2f-87d4-8d48606c4570"
                iconFile.set(project.file("src/main/resources/logo.ico"))
            }
        }
    }
}

// --- AUTOMATION TASK: DEPLOYMENT PREPARATION ---
// This task builds the MSI, then moves all necessary files to your Vercel repo.
tasks.register("releaseToVercel") {
    group = "distribution"
    description = "Packages MSI and updates the Vercel distribution folder correctly."
    dependsOn("packageMsi")

    doLast {
        val vVersion = version.toString()
        val distDir = File("D:/lxplotter-dist")
        val publicDir = File(distDir, "public") // Files here will be accessible at your-url.com/file

        if (!publicDir.exists()) publicDir.mkdirs()

        // 1. Locate and Copy MSI to the 'public' folder
        val msiName = "LXPlotter-$vVersion.msi"
        val sourceMsi = file("build/compose/binaries/main/msi/$msiName")
        val destMsi = File(publicDir, msiName)

        if (sourceMsi.exists()) {
            Files.copy(sourceMsi.toPath(), destMsi.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("MSI Copied to: ${destMsi.absolutePath}")
        } else {
            throw GradleException("Build failed: MSI not found at ${sourceMsi.absolutePath}")
        }

        // 2. Update version.json INSIDE the 'public' folder (Fixes 404 Error)
        val versionFile = File(publicDir, "version.json")
        val vJson = """
        {
          "version": "$vVersion",
          "msiUrl": "https://lx-plotter-app-mxd1.vercel.app/$msiName",
          "notes": "Version $vVersion: Stabilized Vercel deployment and fixed 404 errors."
        }
        """.trimIndent()
        versionFile.writeText(vJson)
        println("version.json updated in public folder.")

        // 3. Update package.json in the root (D:/lxplotter-dist)
        val pkgFile = File(distDir, "package.json")
        val pJson = """
        {
          "name": "lxplotter-dist",
          "version": "$vVersion"
        }
        """.trimIndent()
        pkgFile.writeText(pJson)

        println("package.json updated in root folder.")
        println("SUCCESS: Version $vVersion is fully prepared for Vercel push.")
    }
}