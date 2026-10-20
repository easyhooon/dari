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
        handlerName = arg("handlerName")
        requestId = arg("requestId")
        data = arg("data")
        fireAndForget = arg("handlerName").equalsTo("fireAndForgetPerCall")
    }
    webToAppRequest(bridge, "onBridgeRequestFireAndForget") {
        handlerName = arg("handlerName")
        data = arg("data")
        fireAndForget = constant(true)
    }
    webToAppRequest(bridge, "onProtobufBridgeRequest") {
        handlerName = constant("protobufGreeting")
        requestId = arg("requestId")
        data = base64(arg("base64Data"))
    }
    webToAppResponse(activity, "sendResponseToWeb") {
        handlerName = arg("handlerName")
        requestId = arg("requestId")
        success = arg("success")
        data = arg("data")
    }
    webToAppResponse(activity, "sendProtobufResponseToWeb") {
        handlerName = constant("protobufGreeting")
        requestId = arg("requestId")
        success = arg("success")
        data = arg("data")
    }
    appToWebRequest(activity, "sendToWeb") {
        handlerName = arg("handlerName")
        requestId = arg("requestId")
        data = arg("data")
        fireAndForget = isNull(arg("requestId"))
    }
    appToWebResponse(bridge, "onAppToWebResponse") {
        requestId = arg("requestId")
        success = arg("success")
        data = arg("data")
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
