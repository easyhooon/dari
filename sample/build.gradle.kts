plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("io.github.easyhooon.dari")
}

android {
    namespace = "com.easyhooon.dari.sample"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.easyhooon.dari.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Where MainActivity's bridge receives and sends messages; the plugin logs them in debug builds.
dari {
    tag = "Sample"

    val activity = "com.easyhooon.dari.sample.MainActivity"
    val bridge = "$activity.BridgeInterface"

    webToAppRequest(bridge, "onBridgeRequest") {
        handlerName = arg(0)
        requestId = arg(1)
        data = arg(2)
        fireAndForget = arg(0).equalsTo("fireAndForgetPerCall")
    }
    webToAppRequest(bridge, "onBridgeRequestFireAndForget") {
        handlerName = arg(0)
        data = arg(1)
        fireAndForget = constant(true)
    }
    webToAppRequest(bridge, "onProtobufBridgeRequest") {
        handlerName = constant("protobufGreeting")
        requestId = arg(0)
        data = base64(arg(1))
    }
    webToAppResponse(activity, "sendResponseToWeb") {
        handlerName = arg(0)
        requestId = arg(1)
        success = arg(2)
        data = arg(3)
    }
    webToAppResponse(activity, "sendProtobufResponseToWeb") {
        handlerName = constant("protobufGreeting")
        requestId = arg(0)
        success = arg(1)
        data = arg(2)
    }
    appToWebRequest(activity, "sendToWeb") {
        handlerName = arg(0)
        requestId = arg(1)
        data = arg(2)
        fireAndForget = isNull(arg(1))
    }
    appToWebResponse(bridge, "onAppToWebResponse") {
        requestId = arg(0)
        success = arg(1)
        data = arg(2)
    }
}

dependencies {
    debugImplementation(project(":dari"))
    releaseImplementation(project(":dari-noop"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.protobuf.javalite)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
