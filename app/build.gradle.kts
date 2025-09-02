plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.fireflypsandorid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fireflypsandorid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Compose UI
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-graphics:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")

    // Foundation & Animation
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.animation:animation:1.9.0")
    implementation("androidx.compose.animation:animation-core:1.9.0")

    // Material & Material3
    implementation("androidx.compose.material:material:1.9.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.2")

    // Auto updater library
    implementation("com.github.CSAbhiOnline:AutoUpdater:1.0.1")

    // Unit Test
    testImplementation(libs.junit)

    // Android Instrumentation Test
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")

    // Local AAR library
    implementation(files("../library/firefly-go.aar"))
}

