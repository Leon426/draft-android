plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sameerasw.draft"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sameerasw.draft"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
//        optimized dev build

        //   debug {
        //      isMinifyEnabled = true
        //      isShrinkResources = true
        //      isDebuggable = false

        //      proguardFiles(
        //          getDefaultProguardFile("proguard-android-optimize.txt"),
        //          "proguard-rules.pro"
        //      )
        //   }

        // end

        release {
            optimization {
                enable = false
            }
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

configurations.all {
    resolutionStrategy {
        force("androidx.compose.material3:material3:1.4.0-alpha08")
    }
}

dependencies {
    implementation("androidx.compose.material3:material3:1.4.0-alpha08")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // JGit for pure Java Git integration
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
    // Encrypted SharedPreferences for PAT and credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // ViewModel Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Markwon for Markdown rendering in Android
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}