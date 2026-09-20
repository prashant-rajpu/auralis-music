import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from a gitignored keystore.properties or AURALIS_KEYSTORE_* env vars (CI secrets).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigning(key: String, envVar: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envVar)?.takeIf { it.isNotBlank() }

// Jamendo needs a free developer client id (https://devportal.jamendo.com); the source is disabled without one.
val jamendoClientId: String = (project.findProperty("auralis.jamendoClientId") as String?)
    ?: System.getenv("AURALIS_JAMENDO_CLIENT_ID") ?: ""

// Where this build's Together relay lives (see docs/RELAY.md). Deployment-specific, so it is
// supplied rather than guessed: a wrong default is worse than none, because it fails as a socket
// error rather than as "you have not set this up yet".
val relayUrl: String = (project.findProperty("auralis.relayUrl") as String?)
    ?: System.getenv("AURALIS_RELAY_URL") ?: ""

android {
    namespace = "com.auralis.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.auralis.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0-pro"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"$jamendoClientId\"")
        buildConfigField("String", "RELAY_URL", "\"$relayUrl\"")
    }

    // `play` is Play-Store safe: it compiles none of the unofficial-endpoint code, which lives
    // entirely in src/plus. `plus` is the sideload build and keeps YouTube Music and JioSaavn.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "HAS_SCRAPED_SOURCES", "false")
        }
        create("plus") {
            dimension = "distribution"
            // A distinct id lets both editions coexist and stops Play from replacing a
            // sideloaded plus build with the play one
            applicationIdSuffix = ".plus"
            versionNameSuffix = "-plus"
            buildConfigField("boolean", "HAS_SCRAPED_SOURCES", "true")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseStoreFile = releaseSigning("storeFile", "AURALIS_KEYSTORE_FILE")
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseSigning("storePassword", "AURALIS_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning("keyAlias", "AURALIS_KEY_ALIAS")
                keyPassword = releaseSigning("keyPassword", "AURALIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            // android.util.Log is a stub on the JVM; let it no-op instead of throwing
            isReturnDefaultValues = true
        }
    }
}

ksp {
    // Checked-in schema JSON makes every future migration reviewable
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    // The call. A prebuilt WebRTC for Android — the same engine a browser uses, which is why a
    // call between these two phones can go direct when the networks allow it and fall back to a
    // TURN relay when they do not. It carries native libraries for every ABI, so it is the
    // largest single thing in the APK.
    implementation(libs.webrtc)

    implementation(libs.coil.compose)
    implementation(libs.guava)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // A real SQLite engine on the JVM, so the Room migration can be run and checked in CI
    // without an emulator — MigrationTestHelper needs a device, and this container has none.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
