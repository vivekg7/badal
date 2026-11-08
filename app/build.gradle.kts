import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The release key lives in local/, which is gitignored. Without
// local/keystore.properties release still builds, just unsigned.
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("local/keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

android {
    namespace = "io.github.vivekg7.badal"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vivekg7.badal"
        // Android 15 and newer only (docs/plans/001_wallpaper_rotator.md).
        minSdk = 35
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo { include = false }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources { localeFilters += listOf("en") }

    buildFeatures {
        compose = true
        resValues = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // BatteryLife: Play limits asking to skip battery optimisation, but
        // change-on-lock needs it on Samsung (docs/plans/001_wallpaper_rotator.md).
        // UseKtx: core-ktx is not a direct dependency, and the plain calls read fine.
        // DataExtractionRules: backup is off on purpose (AndroidManifest.xml).
        // AndroidGradlePluginVersion: versions move with galla's, not one by one.
        disable += setOf("BatteryLife", "UseKtx", "DataExtractionRules", "AndroidGradlePluginVersion")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin", "META-INF/*.version")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
