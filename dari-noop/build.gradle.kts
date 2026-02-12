plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.easyhooon.dari"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.startup)
    implementation(libs.kotlinx.coroutines.core)
}