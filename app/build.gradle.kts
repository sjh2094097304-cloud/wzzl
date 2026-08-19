plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 注意：即使不使用 kotlin.plugin.compose，只要用 composeOptions 也能编译
    id("org.jetbrains.kotlin.plugin.compose")  // 保留，无妨
}

android {
    namespace = "com.autumn.s44tool"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autumn.s44tool"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val keyStorePath = System.getenv("KEYSTORE_PATH")
            if (!keyStorePath.isNullOrEmpty()) {
                storeFile = file(keyStorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                println("⚠️  Using debug keystore")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ========== 使用旧的 kotlinOptions（如果不想用 compilerOptions） ==========
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true          // 显式启用 Compose
        buildConfig = true
    }

    // ========== 使用 composeOptions 设置编译器扩展版本 ==========
    composeOptions {
        // 与 Kotlin 2.0.21 对应的最新稳定版（自动匹配，也可留空）
        // kotlinCompilerExtensionVersion = "2.0.21" // 通常自动，但明确指定也可
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}

// 移除顶层 compose {} 块，以免引发解析错误

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}