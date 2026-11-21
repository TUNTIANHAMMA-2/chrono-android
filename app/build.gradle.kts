plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room") version "2.8.5"
}

val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

fun releaseSigningValue(name: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orNull
        ?.takeIf(String::isNotBlank)
        ?: throw GradleException(
            "Release signing requires $name from an environment variable or external Gradle property. " +
                "Do not put release signing values in this repository."
        )

android {
    namespace = "com.personal.cardvault"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.personal.cardvault"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (releaseRequested) {
            create("release") {
                val storeFilePath = releaseSigningValue("CARDVAULT_RELEASE_STORE_FILE")
                storeFile = file(storeFilePath).also {
                    check(it.isFile && it.canRead()) {
                        "Release signing keystore is not a readable regular file."
                    }
                }
                storePassword = releaseSigningValue("CARDVAULT_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValue("CARDVAULT_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValue("CARDVAULT_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".proto"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseRequested) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")
}
