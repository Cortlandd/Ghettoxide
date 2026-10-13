plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.github.Cortlandd"
version = providers.environmentVariable("VERSION").orElse("1.2.0").get()

android {
    namespace = "com.cortlandwalker.ghettoxide"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36

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
    buildFeatures {
        compose = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "Ghettoxide"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}

dependencies {

    // AndroidX fragment / lifecycle for ReducerFragment
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.runtime.saveable)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)

    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Lifecycle / VM
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.fragment.ktx)

    // Coroutines
    api(libs.kotlinx.coroutines.android)

}
