import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.flibusta.reader.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Deb, TargetFormat.Rpm,
                TargetFormat.Msi, TargetFormat.Exe
            )
            packageName = "FlibApp"
            packageVersion = "1.0.0"
            description = "Book search & download client for Flibusta"
            vendor = "FlibApp"

            macOS {
                bundleID = "com.flibusta.reader"
                dmgPackageVersion = "1.0.0"
                iconFile.set(project.file("src/main/resources/FlibApp.icns"))
            }

            linux {
                debPackageVersion = "1.0.0"
                rpmPackageVersion = "1.0.0"
                iconFile.set(project.file("src/main/resources/FlibApp.png"))
            }

            windows {
                dirChooser = true
                menuGroup = "FlibApp"
                upgradeUuid = "5f4a7c2e-8b1d-4e3f-9a6c-1d2e3f4a5b6c"
                iconFile.set(project.file("src/main/resources/FlibApp.ico"))
            }
        }

        buildTypes.release {
            proguard {
                isEnabled = false
            }
        }
    }
}
