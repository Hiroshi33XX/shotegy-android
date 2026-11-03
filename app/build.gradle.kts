import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shotegy.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.shotegy.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"
    }
    val signingFile = rootProject.file(".signing/signing.properties")
    if (signingFile.exists()) {
        val values = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("official") {
            storeFile = rootProject.file(values.getProperty("storeFile"))
            storePassword = values.getProperty("storePassword")
            keyAlias = values.getProperty("keyAlias")
            keyPassword = values.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("official")
    }
    buildTypes.getByName("release") { isDebuggable = false }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Local audit input; absolute cache paths are never committed or packaged.
tasks.register("writeReleaseDependencyInventory") {
    doLast {
        val target = layout.buildDirectory.file("reports/licenses/dependencies.tsv").get().asFile
        target.parentFile.mkdirs()
        target.writeText(configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .sortedBy { it.moduleVersion.id.toString() }.joinToString("\n") {
                "${it.moduleVersion.id}\t${it.file.absolutePath}"
            })
    }
}
