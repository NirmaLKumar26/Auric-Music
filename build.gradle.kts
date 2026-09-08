fun pinSecurityPatches(strategy: org.gradle.api.artifacts.ResolutionStrategy) {
    strategy.eachDependency {
        when (requested.group) {
            "io.netty" -> {
                val current = requested.version.orEmpty()
                useVersion(
                    if (current.startsWith("4.2")) "4.2.16.Final" else "4.1.136.Final",
                )
                because("CVE fixes for Netty HTTP/codec modules")
            }
            "org.bouncycastle" -> {
                useVersion("1.85")
                because("CVE fix for Bouncy Castle GOST CTR keystream reuse")
            }
            "org.apache.commons" -> {
                if (requested.name == "commons-compress") {
                    useVersion("1.28.0")
                    because("Apache Commons Compress security update")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.hilt) apply (false)
    alias(libs.plugins.kotlin.ksp) apply (false)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobufPlugin) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
        classpath("com.google.gms:google-services:4.4.4")
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.6")
    }
    configurations.classpath {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "io.netty" -> {
                    val current = requested.version.orEmpty()
                    useVersion(
                        if (current.startsWith("4.2")) "4.2.16.Final" else "4.1.136.Final",
                    )
                }
                "org.bouncycastle" -> useVersion("1.85")
                "org.apache.commons" -> {
                    if (requested.name == "commons-compress") useVersion("1.28.0")
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.add("-P")
                    freeCompilerArgs.add("plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${project.layout.buildDirectory}/compose_metrics")
                }
            }
        }
    }
}

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            pinSecurityPatches(this)
            force("com.google.protobuf:protobuf-javalite:4.35.0")
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
        }
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
}
