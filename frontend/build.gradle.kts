import java.util.Properties
import java.net.InetAddress

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

fun getLocalIP(): String {
    return InetAddress.getLocalHost().hostAddress
}

val envProperties = Properties()
val envFile = file(".env")
if (envFile.exists()) {
    envFile.inputStream().use { envProperties.load(it) }
}
val mapsApiKey: String = envProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.walkmate"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.walkmate"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "BASE_URL", "\"http://${getLocalIP()}:8080/\"")
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
    implementation(libs.viewpager2)
    implementation(libs.swiperefreshlayout)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Khối Network (Retrofit, OkHttp, Gson)
    implementation(libs.retrofit)
    implementation(libs.retrofitConverterGson)
    implementation(libs.okhttp)
    implementation(libs.okhttpLoggingInterceptor)
    implementation(libs.gson)

    // GPS Tracking
    implementation(libs.playServicesLocation)

    // Google Maps & Utils
    implementation(libs.playServicesMaps)
    implementation(libs.androidMapsUtils)

    // Khối Architecture Components (ViewModel, LiveData)
    implementation(libs.lifecycleViewmodel)
    implementation(libs.lifecycleLivedata)

    // Secure local token storage
    implementation(libs.securityCrypto)

    // Room (local GPS point storage)
    implementation(libs.roomRuntime)
    annotationProcessor(libs.roomCompiler)

    // Image loading
    implementation(libs.glide)

    // Jetpack Navigation
    implementation(libs.navigationFragment)
    implementation(libs.navigationUi)

    // Firebase Cloud Messaging
    implementation(platform(libs.firebaseBom))
    implementation(libs.firebaseMessaging)
}
