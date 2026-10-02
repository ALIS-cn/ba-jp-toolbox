plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bluearchive.toolbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluearchive.toolbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "0.9.1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // 开启 R8 代码压缩 + 资源压缩：自动剔除 material-icons-extended 中未使用的上万个图标类
            isMinifyEnabled = true
            isShrinkResources = true
            // 使用 debug 密钥签名，保证可直接覆盖安装现有版本（签名一致，无需卸载，避免引继码丢失）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 使用本地 JDK 17 直接指定字节码目标，避免 jvmToolchain 触发外部 JDK 下载
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL20.0,LGPL2.1}"
            // kotlinx-coroutines-debug 的调试探针，仅调试时需要，不打进发行包
            excludes += "/DebugProbesKt.bin"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku 客户端 API（服务端为用户自行安装的 Shizuku App，GPL-3.0）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}





