@file:Suppress("UseTomlInstead")

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.abhinav.ownapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abhinav.ownapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "9.0.2"

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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Existing PDFBox library
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Google ML Kit Document Scanner (Scan to PDF Engine)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")


}