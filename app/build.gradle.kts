plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.guitar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.guitar"
        minSdk = 26
        targetSdk = 34
        // major.minor.patch versioning. Bump MINOR for new features, PATCH for
        // bug fixes of existing features, MAJOR for breaking redesigns. Previous
        // releases are kept in /releases — never delete them. versionCode is a
        // monotonically increasing integer: major*10000 + minor*100 + patch
        // (e.g. 1.2.0 = 10200, 1.2.1 = 10201, 2.0.0 = 20000).
        versionCode = 10902
        versionName = "1.9.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Name the built APK after the app + version instead of the default
    // "app-debug.apk" — e.g. Chorect_beta_V1.0.apk. Tracks versionName, so the
    // next build (1.1, 2.0, …) is named automatically.
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "Chorect_beta_V${variant.versionName}.apk"
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
    }
}

dependencies {
    implementation(project(":theory"))
    implementation(project(":audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
