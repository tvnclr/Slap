import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing lives outside the repository (C:\Claude folder\Slap-signing).
val signingProps = rootProject.file("../Slap-signing/keystore.properties")

android {
  namespace = "dev.tvnclr.slap"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.tvnclr.slap"
    minSdk = 28
    targetSdk = 36
    versionCode = 2
    versionName = "1.1.0"
  }

  signingConfigs {
    if (signingProps.exists()) {
      val p = Properties().apply { signingProps.inputStream().use { load(it) } }
      create("slap") {
        storeFile = file(p.getProperty("storeFile"))
        storePassword = p.getProperty("storePassword")
        keyAlias = p.getProperty("keyAlias")
        keyPassword = p.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      if (signingProps.exists()) signingConfig = signingConfigs.getByName("slap")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
  androidResources { generateLocaleConfig = true }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

base { archivesName.set("slap-${android.defaultConfig.versionName}") }

dependencies {
  implementation(project(":tvnclr-ui"))
  implementation(platform("androidx.compose:compose-bom:2025.10.01"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("androidx.navigation:navigation-compose:2.9.5")
  // the only feature library: on-device AI cutout (model comes from Google Play on first use)
  implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
  testImplementation("junit:junit:4.13.2")
}
