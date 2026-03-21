import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val envProperties = Properties()
val envFile = file(".env")
if (envFile.exists()) {
    envFile.inputStream().use { envProperties.load(it) }
}
val mapsApiKey: String = envProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.walkmate.frontend"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.walkmate.frontend"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildFeatures {
        buildConfig = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle (LiveData, ViewModel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)

    // GPS Tracking
    implementation(libs.play.services.location)

    // Google Maps & Utils
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}