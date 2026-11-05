import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// MapTiler key: local.properties (maptiler.key) > env MAPTILER_KEY > built-in default
val mapTilerKey: String = localProperties.getProperty("maptiler.key")
    ?: System.getenv("MAPTILER_KEY")
    ?: "shkdqB1hzngy8nxGoYMA"

android {
    namespace = "com.rohan.raksha.geofence"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rohan.raksha.geofence"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
        buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                localProperties.getProperty("signing.storeFile", "signing/signing-key.jks")
            )
            storePassword = localProperties.getProperty("signing.storePassword", "raksha123")
            keyAlias      = localProperties.getProperty("signing.keyAlias",       "raksha")
            keyPassword   = localProperties.getProperty("signing.keyPassword",    "raksha123")
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs.pickFirsts += setOf("**/*.so")
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // ABI splits: one slim APK per architecture + universal fallback
    // armeabi-v7a  → 32-bit ARM (old phones)
    // arm64-v8a    → 64-bit ARM (modern phones)
    // x86_64       → emulators
    // universalApk → works on all devices
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.maplibre.gl:android-sdk:11.5.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
