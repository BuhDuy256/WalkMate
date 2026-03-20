plugins {
    alias(libs.plugins.android.application)
}

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

    // --- PHẦN THÊM MỚI TỪ FILE 2 ---
    // Bật tính năng ViewBinding để code giao diện dễ hơn
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- CÁC THƯ VIỆN GỐC CỦA BẠN ---
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // --- CÁC THƯ VIỆN TỪ FILE 2 (Đã chuẩn hoá sang KTS) ---
    // AndroidX Core & CardView
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Avatar tròn & Load ảnh
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Kiến trúc ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")

    // Gọi API (Retrofit) & Xử lý chuỗi JSON (Gson)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Theo dõi log mạng
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}