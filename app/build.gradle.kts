import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.metflix"
    compileSdk {
        version = release(37)
    }

val envFile = rootProject.file(".env")
val envProperties = Properties()
if (envFile.exists()) {
    envFile.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val parts = trimmed.split("=", limit = 2)
            envProperties[parts[0].trim()] = parts[1].trim()
        }
    }
}
val defaultDebridApiKey: String = envProperties.getProperty("ALLDEBRID_API_KEY")
    ?: System.getenv("ALLDEBRID_API_KEY") ?: ""
val defaultTorBoxApiKey: String = envProperties.getProperty("TORBOX_API_KEY")
    ?: System.getenv("TORBOX_API_KEY") ?: ""
val defaultTmdbApiKey: String = envProperties.getProperty("TMDB_API_KEY")
    ?: System.getenv("TMDB_API_KEY") ?: ""

    defaultConfig {
        applicationId = "com.example.metflix"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEFAULT_TMDB_KEY", "\"$defaultTmdbApiKey\"")
        buildConfigField("String", "DEFAULT_ALLDEBRID_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_TORBOX_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = envProperties.getProperty("KEYSTORE_PATH")
                ?: System.getenv("KEYSTORE_PATH")
                ?: (project.findProperty("KEYSTORE_PATH") as? String)
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = envProperties.getProperty("KEYSTORE_PASSWORD")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                    ?: (project.findProperty("KEYSTORE_PASSWORD") as? String) ?: ""
                keyAlias = envProperties.getProperty("KEY_ALIAS")
                    ?: System.getenv("KEY_ALIAS")
                    ?: (project.findProperty("KEY_ALIAS") as? String) ?: ""
                keyPassword = envProperties.getProperty("KEY_PASSWORD")
                    ?: System.getenv("KEY_PASSWORD")
                    ?: (project.findProperty("KEY_PASSWORD") as? String) ?: ""
            }
        }
    }

    buildTypes {
        debug {
            // Local developer convenience only: allow .env keys in debug builds
            buildConfigField("String", "DEFAULT_ALLDEBRID_KEY", "\"$defaultDebridApiKey\"")
            buildConfigField("String", "DEFAULT_TORBOX_KEY", "\"$defaultTorBoxApiKey\"")
        }
        create("releaseTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "DEFAULT_ALLDEBRID_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_TORBOX_KEY", "\"\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing when a keystore is configured; otherwise default to debug signing.
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            // Production releases NEVER bundle creator debrid keys
            buildConfigField("String", "DEFAULT_ALLDEBRID_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_TORBOX_KEY", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // TV Material
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Coil (image loading)
    implementation(libs.coil.compose)

    // Networking & DNS-over-HTTPS (DoH)
    implementation(libs.okhttp)

    // Media3 (video player & transformer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Profile Installer
    implementation(libs.androidx.profileinstaller)

    // External Storage & Document Access
    implementation(libs.androidx.documentfile)

    // QR Code generation (ZXing Core - offline & lightweight)
    implementation("com.google.zxing:core:3.5.3")

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit tests
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
