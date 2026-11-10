plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.abidur.dotsalah"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.abidur.dotsalah"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Signing key comes from GitHub Secrets. It is never stored in this public repo.
    val ksFile = file("budget-key.jks")
    val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val hasKey = ksFile.exists() && ksPass.isNotEmpty()

    signingConfigs {
        if (hasKey) {
            create("release") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = "budget"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
