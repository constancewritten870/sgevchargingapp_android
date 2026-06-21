import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Read secrets from local.properties (not committed). Falls back to empty strings.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: "").trim()

android {
    namespace = "com.alfredang.sgevcharging"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alfredang.sgevcharging"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // LTA DataMall account key surfaced as BuildConfig.LTA_DATAMALL_ACCOUNT_KEY
        buildConfigField(
            "String",
            "LTA_DATAMALL_ACCOUNT_KEY",
            "\"${secret("LTA_DATAMALL_ACCOUNT_KEY")}\""
        )
        // Google Maps key injected into the manifest meta-data placeholder
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
