import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp.plugins)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.secrets.gradle.plugin)
}

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.example.weight"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.weight"
        minSdk = 29
        targetSdk = 37
        versionCode = 16
        versionName = "1.6.0"
        ndk.abiFilters.add("arm64-v8a")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 在原始 applicationId 后追加后缀，例如 .debug
            applicationIdSuffix = ".debug"

            // 可选：为 debug 版应用名称添加后缀，方便在手机桌面区分
            resValue("string", "app_name", "体重记录-Debug")
        }
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

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests {
            // Robolectric 需要 resources/manifest 才能起 Compose 宿主(T10)
            isIncludeAndroidResources = true
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
    }
}
room {
    schemaDirectory("$projectDir/schemas")
}
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
    ignoreList.add("keyToIgnore")
    ignoreList.add("sdk.*")
}
ksp {
    arg("room.generateKotlin", "true")
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
    //room
    implementation(libs.androidx.room.runtime)

    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.ktx)
    //work
    implementation(libs.androidx.work.runtime)
    //glance
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    //exif：TakePicture 全尺寸 JPEG 的方向标记读取（BitmapFactory 不读 EXIF）
    implementation(libs.androidx.exifinterface)
    //koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)
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
    //ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    //markdown
    implementation(libs.markdown.editor)
    implementation(libs.markdown.m3)
    //test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.core.ktx)
    // T10 Compose UI 测试基建(Robolectric + createComposeRule);BOM 统一版本
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}