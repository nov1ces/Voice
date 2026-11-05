plugins {
  id("voice.library")
}

android {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

dependencies {
  implementation(projects.core.playback.api)
  implementation(projects.core.common)
  implementation(projects.core.strings)
  implementation(projects.core.featureflag)
  implementation(projects.core.sleeptimer.api)
  implementation(projects.core.data.api)
  implementation(projects.core.search)
  implementation(projects.core.analytics.api)

  implementation(libs.coil)
  implementation(libs.coroutines.guava)

  implementation(projects.core.ffmpeg)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  androidTestImplementation(libs.androidX.test.junit)
  androidTestImplementation("androidx.test:runner:1.7.0")

  testImplementation(libs.media3.testUtils.core)
  testImplementation(libs.media3.testUtils.robolectric)
}
