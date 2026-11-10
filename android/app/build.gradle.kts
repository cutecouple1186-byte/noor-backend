plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

android {
    namespace = "com.noor.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noor.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "20.0"
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
