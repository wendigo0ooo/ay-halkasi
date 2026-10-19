plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wendigo.ayhalkasi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wendigo.ayhalkasi"
        minSdk = 24
        targetSdk = 34
        versionCode = 18
        versionName = "1.13.2"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            val ksFile = if (ksPath != null) file(ksPath) else null
            if (ksFile != null && ksFile.exists() && ksFile.length() > 0L) {
                storeFile = ksFile
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            val ksFile = if (ksPath != null) file(ksPath) else null
            if (ksFile != null && ksFile.exists() && ksFile.length() > 0L) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("normal") {
            dimension = "edition"
        }
        create("ozel") {
            dimension = "edition"
            applicationIdSuffix = ".ozel"
            versionNameSuffix = "-ozel"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
