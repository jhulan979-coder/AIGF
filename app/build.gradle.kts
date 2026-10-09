plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aigf.app"
    compileSdk = 35

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}
signingConfigs {
        create("stable") {
            storeFile = file(System.getenv("AIGF_KEYSTORE_PATH") ?: "")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "aigf"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.aigf.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 24
        versionName = "1.2"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
    }

}
