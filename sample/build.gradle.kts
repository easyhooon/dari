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
        // Release-like (minified, not debuggable) but still inspectable with Dari.
        create("staging") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    // The bench bridges live in the debug source set; staging maps them too.
    sourceSets.getByName("staging").kotlin.directories.add("src/debug/java")
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
    nonDebuggableBuildTypes.add("staging")

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

    // Debug-only benchmark bridges (src/debug, BridgeOverheadBenchmark).
    val bench = "com.easyhooon.dari.sample.bench"
    webToAppRequest("$bench.MappedPositionalBridge", "onRequest") {
        tag = "Bench"
        handlerName = arg(0)
        requestId = arg(1)
        data = arg(2)
    }
    webToAppResponse("$bench.MappedPositionalBridge", "respond") {
        tag = "Bench"
        handlerName = arg(0)
        requestId = arg(1)
        success = arg(2)
        data = arg(3)
    }
    webToAppRequest("$bench.MappedJsonBridge", "onMessage") {
        tag = "Bench"
        handlerName = json(arg(0), "handler")
        requestId = json(arg(0), "id")
        data = json(arg(0), "data")
    }
    webToAppResponse("$bench.MappedJsonBridge", "reply") {
        tag = "Bench"
        handlerName = json(arg(0), "handler")
        requestId = json(arg(0), "id")
        success = json(arg(0), "ok")
        data = json(arg(0), "data")
    }
}

dependencies {
    debugImplementation(project(":dari"))
    releaseImplementation(project(":dari-noop"))
    "stagingImplementation"(project(":dari"))

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
