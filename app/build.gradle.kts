import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 版本号唯一入口：格式 yyyy.MM.dd.当日序号，每次发布（或同日重打）手动递增最后一段。
// 注意：开启 configuration cache 后，配置期读取系统时间在命中缓存时会拿到旧日期，
// 因此版本号用显式常量而非运行时日期，保证 versionCode / versionName / APK 文件名三者一致。
val appVersion = "2026.10.03.01"

// Version Code 由版本号去点生成：2026.09.28.01 -> 2026092801
val appVersionCode = appVersion.replace(".", "").toInt()

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
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "SYNC_SERVER_URL", "\"" + providers.gradleProperty("syncServerUrl").getOrElse("https://app-admin.yingluozhiwei.cn") + "\"")
        ndk.abiFilters.add("arm64-v8a")

        testInstrumentationRunner = "com.example.weight.SyncTestRunner"
    }

    // Release 签名：根目录 key（PKCS12），已被 .gitignore 排除
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("key")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
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
            signingConfig = signingConfigs.getByName("release")
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

// Release 包命名：体重记录V<版本号>.apk（仅 release 变体）
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName = "体重记录V$appVersion.apk"
            }
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
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)
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
    //okhttp：唯一网络栈（AI 聊天 SSE 流式 + 检查更新 + APK 下载）
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    //markdown
    implementation(libs.markdown.editor)
    implementation(libs.markdown.m3)
    //test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // T10 Compose UI 测试基建(Robolectric + createComposeRule);BOM 统一版本
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
