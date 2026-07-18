import java.time.Instant

// Auto-incrementing build counter stored in a file
fun getBuildCounter(): Int {
    val counterFile = File(rootProject.projectDir, ".build_counter")
    if (!counterFile.exists()) {
        counterFile.writeText("0")
    }
    val count = counterFile.readText().toIntOrNull() ?: 0
    counterFile.writeText("${count + 1}")
    return count + 1
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Get build number ONCE - calling getBuildCounter() multiple times increments each time
val buildNum = getBuildCounter()

android {
    namespace = "com.toyrobotworkshop.auspex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toyrobotworkshop.auspex"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNum
        versionName = "0.1.$buildNum"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Build-time diagnostics injected into BuildConfig
        buildConfigField("String", "BUILD_TIME", "\"${Instant.now().toString()}\"")
        buildConfigField("String", "GIT_SHA", "\"${providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()}\"")
        buildConfigField("int", "BUILD_NUMBER", "$buildNum")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    android.applicationVariants.all {
        outputs.forEach { output ->
            val variantName = name.replace("Debug", "debug").replace("Release", "release")
            (output as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "auspex-${variantName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)

    // Material Icons (core + extended for Material Symbols)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Material Components (for XML themes)
    implementation(libs.google.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // UVCCamera library (alexey-pelykh fork)
    implementation(libs.uvccamera.lib)
}
