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
        // releases are kept in /releases â€” never delete them. versionCode is a
        // monotonically increasing integer: major*10000 + minor*100 + patch
        // (e.g. 1.2.0 = 10200, 1.2.1 = 10201, 2.0.0 = 20000).
        versionCode = 29001
        versionName = "2.90.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Name the built APK after the app + version instead of the default
    // "app-debug.apk" â€” e.g. Chorect_beta_V1.0.apk. Tracks versionName, so the
    // next build (1.1, 2.0, â€¦) is named automatically.
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "Chorect_beta_V${variant.versionName}.apk"
        }
        // The output folder must only ever hold the MOST RECENT APK (it is synced
        // to the phone): after assembling, delete any stale Chorect_beta_V*.apk
        // left over from earlier versions. Historical APKs live in /releases.
        val current = "Chorect_beta_V${variant.versionName}.apk"
        variant.assembleProvider.get().doLast {
            variant.outputs.forEach { out ->
                out.outputFile.parentFile
                    ?.listFiles { f -> f.name.startsWith("Chorect_beta_V") && f.name.endsWith(".apk") && f.name != current }
                    ?.forEach { it.delete() }
                // Nadav wants the newest APK on his Desktop, and only the newest.
                val desktop = File(System.getProperty("user.home"), "Desktop")
                if (desktop.isDirectory && out.outputFile.isFile) {
                    desktop.listFiles { f -> f.name.startsWith("Chorect_beta_V") && f.name.endsWith(".apk") && f.name != current }
                        ?.forEach { it.delete() }
                    out.outputFile.copyTo(File(desktop, current), overwrite = true)
                }
            }
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
}

dependencies {
    implementation(project(":theory"))
    implementation(project(":audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    // SAF folder access for the sideloaded song pack (SongPackStore).
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
