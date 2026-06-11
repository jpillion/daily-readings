import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
