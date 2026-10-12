/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing. The keystore and its passwords never live in the repository: `keystore.properties` (ignored by
// git, see docs/release.md) points at the keystore; the same four values can come from environment variables
// (MB_STORE_FILE, MB_STORE_PASSWORD, MB_KEY_ALIAS, MB_KEY_PASSWORD) for a CI. Without them the release build is
// still produced, unsigned, so nobody else is blocked.
val signingValues: Map<String, String> = run {
    val file = rootProject.file("keystore.properties")
    val fromFile = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    mapOf(
        "storeFile" to "MB_STORE_FILE", "storePassword" to "MB_STORE_PASSWORD",
        "keyAlias" to "MB_KEY_ALIAS", "keyPassword" to "MB_KEY_PASSWORD",
    ).mapNotNull { (key, env) -> (fromFile.getProperty(key) ?: System.getenv(env))?.let { key to it } }.toMap()
}

android {
    namespace = "com.metroboard.ime"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.metroboard.ime"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingValues.size == 4) {
            create("release") {
                storeFile = rootProject.file(signingValues.getValue("storeFile"))
                storePassword = signingValues.getValue("storePassword")
                keyAlias = signingValues.getValue("keyAlias")
                keyPassword = signingValues.getValue("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Unit tests read the shipped dictionaries straight from disk, so a change to them
        // must invalidate the test task (otherwise Gradle reports a stale green result).
        unitTests.all { it.inputs.dir("src/main/assets/dictionaries").withPropertyName("dictionaryAssets") }
    }
}

// AGP 9 supplies Kotlin 2.2.10. No separate Kotlin Android plugin is needed.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
