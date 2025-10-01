import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp.plugins)
    alias(libs.plugins.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.weight"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.weight"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions{
            jvmTarget = JvmTarget.fromTarget("21")
        }
    }
    buildFeatures {
        compose = true
    }

    ksp {
        arg("KOIN_USE_COMPOSE_VIEWMODEL","true")
        arg("room.generateKotlin","true")
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    //material3
    implementation(libs.androidx.material3)
    implementation(libs.androidx.icon.extend)
    //navigation
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.navigation)
    //room
    implementation(libs.androidx.room.runtime)

    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.ktx)
    //koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)
    //serialization
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.core)
    //paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    //charts
    implementation(libs.charts)
    //vico
    implementation(libs.vico.compose.m3)
    //mmkv
    implementation(libs.mmkv)
}