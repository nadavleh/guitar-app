plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.guitar.audio"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.all {
            // -Ptags=audio runs only tests carrying one of those @Tag values.
            val tags = (project.findProperty("tags") as String?)?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotEmpty() }
            it.useJUnitPlatform { if (!tags.isNullOrEmpty()) includeTags(*tags.toTypedArray()) }
        }
    }
}

// Only the debug variant is tested: the release variant runs the identical sources a second time.
afterEvaluate {
    tasks.named("test") { setDependsOn(listOf("testDebugUnitTest")) }
}

dependencies {
    implementation(project(":theory"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}
