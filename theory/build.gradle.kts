plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// Emits the cavaquinho G-chord shapes JSON consumed by tools/build_cavaco_chord_pdf.py
// (the reproducible chord-sheet PDF pipeline): .\gradlew :theory:emitCavacoShapes
// Renders a drum-machine pattern to a WAV with the SAME engine the in-app export uses:
//   .\gradlew :theory:renderPercussion --args="--bpm 90 --track surdo --out C:\path\surdo.wav"
// Run with no --args to see the option list in the error.
tasks.register<JavaExec>("renderPercussion") {
    group = "tools"
    description = "Render a percussion pattern (or one track of it) to a WAV file"
    mainClass.set("app.guitar.theory.tools.RenderPercussion")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

tasks.register<JavaExec>("emitCavacoShapes") {
    group = "tools"
    description = "Emit cavaquinho G-chord shapes as JSON for the PDF generator"
    mainClass.set("app.guitar.theory.tools.EmitCavacoShapesKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(rootDir.resolve("tools/cavaco_g_shapes.json").absolutePath)
}
