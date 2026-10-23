import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "amd.anik.ocdroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "amd.anik.ocdroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.3.5"

        // No default server: the tunnel/hostname belongs to whichever machine runs
        // OpenCode, so it must be discovered or paired on first launch.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
        buildConfigField("String", "DEFAULT_SERVER_USERNAME", "\"opencode\"")
    }

    val hasPropertiesFile = File("app/keystore/signing.properties").exists()
    if (hasPropertiesFile) {
        val props = Properties()
        props.load(FileInputStream(file("keystore/signing.properties")))
        val alias = props["keystore.alias"] as String
        signingConfigs {
            create("release") {
                storeFile = file(props["keystore"] as String)
                storePassword = props["keystore.password"] as String
                keyAlias = alias
                keyPassword = props["keystore.password"] as String
            }
        }
        println("[Signature] -> Build will be signed with: $alias")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "OCDroid Dev"
        }
        release {
            manifestPlaceholders["appLabel"] = "@string/app_name"
            if (hasPropertiesFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Native pairing scanner used by the welcome screen before any web login exists.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
