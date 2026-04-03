plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.d2chaofan.emergencycall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.d2chaofan.emergencycall"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // 已移除可能引起问题的 lint 配置，恢复为默认状态。
    // warning("deprecation") 等设置将在 IDE 中通过其他方式处理。
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}