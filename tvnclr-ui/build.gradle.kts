// tvnclr UI: tvnclr's reusable Compose design system (tokens, themes, components).
plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.tvnclr.ui"
  compileSdk = 36
  defaultConfig { minSdk = 24 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures { compose = true }
}

dependencies {
  api(platform("androidx.compose:compose-bom:2025.10.01"))
  api("androidx.compose.ui:ui")
  api("androidx.compose.material3:material3")
  api("androidx.compose.material:material-icons-extended")
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.datastore:datastore-preferences:1.1.7")
}
