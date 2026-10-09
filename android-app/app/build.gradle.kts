plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

@Suppress("UnstableApiUsage")
android {
    namespace = "com.bjmf.sign.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bjmf.sign.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.11.1")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.2")

    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.11.1")
}
