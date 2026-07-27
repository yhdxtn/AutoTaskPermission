plugins {
    id("com.android.application")
}

android {
    namespace = "com.autotask.permission"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autotask.permission"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "ACTIVATION_API_BASE_URL", "\"http://82.157.64.38:28081\"")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
