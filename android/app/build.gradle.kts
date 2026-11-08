plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.noor.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noor.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
