plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.marinov.colegioetapa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marinov.colegioetapa"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "11.1"
        buildConfigField("String", "EAD_URL", "\"${project.properties["EAD_URL"]}\"")
        buildConfigField("String", "GITHUB_PAT", "\"${project.properties["GITHUB_PAT"]}\"")
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
    buildFeatures {
        viewBinding = true
        android.buildFeatures.buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.webkit)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation (libs.material)
    implementation ("androidx.webkit:webkit:1.8.0")
    implementation ("org.jsoup:jsoup:1.14.3")
    implementation ("com.google.code.gson:gson:2.8.9")
    implementation ("androidx.work:work-runtime-ktx:2.8.1")
    implementation ("com.google.guava:listenablefuture:1.0")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation ("com.google.guava:guava:31.0.1-android")
    implementation ("com.github.bumptech.glide:glide:4.13.2")
    implementation ("com.google.code.gson:gson:2.10.1")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")
}