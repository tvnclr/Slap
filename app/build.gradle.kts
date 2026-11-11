import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing lives outside the repository (C:\Claude folder\Slap-signing).
val signingProps = rootProject.file("../Slap-signing/keystore.properties")

// Builds outside main are test builds named "<base>-dev.N", N = commits since the last stable tag
// (dev prerelease tags are skipped so the count does not reset when one is published).
val baseVersion = "1.1.0"

fun git(vararg args: String): String? =
    runCatching { providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

val appVersion: String =
    if (git("rev-parse", "--abbrev-ref", "HEAD") == "main") baseVersion
    else "$baseVersion-dev." + (git("describe", "--tags", "--abbrev=0", "--exclude=*-dev.*")?.let { git("rev-list", "--count", "$it..HEAD") } ?: "0")

android {
  namespace = "dev.tvnclr.slap"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.tvnclr.slap"
    minSdk = 28
    targetSdk = 36
    versionCode = 2
    versionName = appVersion
    // phones (64 and 32 bit ARM) and x86_64 emulators / Chromebooks; 32-bit x86 is not worth its 2.4 MB
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // LiteRT's native library is stored compressed: a smaller APK for one extraction at install
    jniLibs { useLegacyPackaging = true }
  }
  androidResources {
    generateLocaleConfig = true
    // the AI model is memory-mapped straight from the APK
    noCompress += "tflite"
  }
}

base { archivesName.set("slap-${android.defaultConfig.versionName}") }

dependencies {
  implementation(project(":tvnclr-ui"))
  implementation(platform("androidx.compose:compose-bom:2025.10.01"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("androidx.navigation:navigation-compose:2.9.5")
  // AI cutout: U^2-Net on LiteRT (free, bundled) in every build
  implementation("com.google.ai.edge.litert:litert:1.4.2") // classic Interpreter API, Java only (2.x needs Kotlin 2.4)
  // AI cutout: Google ML Kit (model from Google Play). Not in the fdroid branch, with MlKitSegmenter.java
  implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
  testImplementation("junit:junit:4.13.2")
}
