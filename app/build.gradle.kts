import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// D7 / S9-T5: release signing reads from keystore.properties (untracked) or environment —
// the repo never contains key material. The owner generates and holds the upload key; see
// docs/sprints/sprint-0009-hardening-release.md for the exact steps and secret names.
val keystoreProperties: Properties =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingSecret(
    property: String,
    envVar: String,
): String? = keystoreProperties.getProperty(property) ?: System.getenv(envVar)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.jpillion.dailyreadingplanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jpillion.dailyreadingplanner"
        minSdk = 26
        targetSdk = 37
        // D-S9-3: versionCode = MAJOR*10000 + MINOR*100 + PATCH (1.0.0 -> 10000) — monotonic
        // for Play, derivable from versionName, with room for 99 patch/minor steps each.
        versionCode = 10303
        versionName = "1.3.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = signingSecret("storeFile", "DRP_UPLOAD_KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingSecret("storePassword", "DRP_UPLOAD_STORE_PASSWORD")
                keyAlias = signingSecret("keyAlias", "DRP_UPLOAD_KEY_ALIAS")
                keyPassword = signingSecret("keyPassword", "DRP_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // S9-T5: R8 + resource shrinking on for release; keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Until the owner provisions the upload key, release builds debug-sign so
            // bundleRelease is CI-provable end to end (the Play upload key swaps in via
            // keystore.properties/env with zero build-script changes).
            signingConfig =
                if (signingConfigs.getByName("release").storeFile != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Compose UI tests run under Robolectric in testDebugUnitTest (D-S4-1); they need
        // the merged Android resources (theme, strings) on the unit-test classpath.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // The plan-data verification gate reads the bundled asset straight from the
            // source tree so the test guards the exact file shipped in the APK.
            it.systemProperty(
                "planAssetsDir",
                layout.projectDirectory
                    .dir("src/main/assets")
                    .asFile.absolutePath,
            )
            // Sprint 1 lesson: declare the asset as a test input, otherwise edits to the
            // plan JSON are silently skipped as UP-TO-DATE and the gate never re-runs.
            it.inputs
                .dir(layout.projectDirectory.dir("src/main/assets"))
                .withPropertyName("planAssets")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // D-S9-4: export the Room schema (app/schemas, checked in) so the V2 streak-schema
    // migration has a pinned baseline to migrate *from*.
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    currentProject {
        // Merge the debug Android variant into a named report variant so coverage is
        // actually measured (default Kover reports on Android measure nothing).
        createVariant("appDebug") {
            add("debug")
        }
    }
    reports {
        filters {
            includes {
                // Coverage floor applies to domain/data only (EXECUTION_PLAN S2-T5); UI is not gated.
                classes(
                    "com.jpillion.dailyreadingplanner.domain.*",
                    "com.jpillion.dailyreadingplanner.data.*",
                )
            }
            excludes {
                // Generated DI plumbing (Hilt/Dagger factories, Room @Generated) is not our
                // logic; counting it would dilute the floor on code we actually wrote.
                annotatedBy("dagger.internal.DaggerGenerated", "javax.annotation.processing.Generated")
            }
        }
        verify {
            rule("domain/data line coverage floor") {
                minBound(70)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
