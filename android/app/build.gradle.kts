plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

android {
    namespace = "dz.latchi.arena"
    compileSdk = 34

    defaultConfig {
        applicationId = "dz.latchi.arena"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
        resourceConfigurations += listOf("ar", "en")
    }

    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksFile = ksProps.getProperty("storeFile")?.let { rootProject.file(it) }
    val hasRelease = ksFile != null && ksFile.exists()

    signingConfigs {
        if (hasRelease) {
            create("release") {
                storeFile = ksFile
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasRelease) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
        debug { applicationIdSuffix = ".debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
