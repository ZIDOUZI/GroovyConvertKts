@file:Suppress("SpellCheckingInspection")

import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    id("org.jetbrains.compose") version "1.0.0"
}

group = "zdz.dou"
version = "1.0"
val customSourceSet = sourceSets.create("customSourceSet")

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

compose.desktop {
    application {
        mainClass = "zdz.groovyconvertkts.ui.main.MainWindowKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "GroovyConvertKts"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(project.file("\\src\\main\\resources\\"))
            windows {
                iconFile.set(project.file("\\src\\main\\resources\\kotlin.ico"))
            }
            from(customSourceSet)
        }
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.6"
}