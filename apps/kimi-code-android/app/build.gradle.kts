plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.josemilk.kimicode.android"; compileSdk = 36
    defaultConfig { applicationId = "com.josemilk.kimicode.android"; minSdk = 29; targetSdk = 36; versionCode = 2; versionName = "1.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
