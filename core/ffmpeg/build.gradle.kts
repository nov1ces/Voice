plugins {
  id("voice.library")
}

android {
  namespace = "androidx.media3.decoder.ffmpeg"
  ndkVersion = "28.2.13676358"

  defaultConfig {
    consumerProguardFiles("consumer-rules.pro")
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
    externalNativeBuild {
      cmake {
        arguments += "-DANDROID_STL=c++_shared"
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/jni/CMakeLists.txt")
      version = "3.22.1"
    }
  }
}

dependencies {
  api(libs.media3.decoder)
  implementation(libs.media3.exoplayer)
  compileOnly("org.checkerframework:checker-qual:3.49.5")
}
