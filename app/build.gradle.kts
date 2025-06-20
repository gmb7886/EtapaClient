plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.marinov.colegioetapa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marinov.colegioetapa"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "17.0"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.ui.tooling.preview2)
    implementation(libs.androidx.compose.material3.material33)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose.v182)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
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
    implementation(libs.webkit.v180)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.work.runtime.ktx)
    implementation(libs.listenablefuture)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.guava)
    implementation(libs.glide)
    implementation(libs.gson.v2101)
    annotationProcessor(libs.compiler)
}