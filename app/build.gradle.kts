plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  // Faz 1: AI Studio sablonunun biraktigi "com.example" namespace'i marka
  // kimligiyle degistirildi. R sinifi da bu namespace'ten uretiliyor.
  namespace = "com.miniappfactory.frontlinedefender"

  // Blok formundaki compileSdk DSL'i AGP 9.x gerektirir. Ayni yapilandirma
  // Boom-Blocks projesinde (AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10) calisir
  // durumda oldugu icin AGP 8.x'e dusurulmedi.
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Faz 1: uygulama henuz yayinlanmadi, applicationId'yi degistirmek icin
    // son firsat (yayin sonrasi degistirilemez).
    applicationId = "com.miniappfactory.frontlinedefender"
    // minSdk 24 KORUNUYOR: birincil test cihazi Galaxy S8 / Android 7.0 = API 24.
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Faz 1: eski "debugConfig" blogu var olmayan ${rootDir}/debug.keystore
    // dosyasini isaret ediyordu ve assembleDebug'i kesin olarak patlatiyordu.
    // Blok tamamen kaldirildi; debug buildType artik AGP'nin varsayilan debug
    // imzasini (~/.android/debug.keystore, gerekirse otomatik uretilir) kullanir.
    //
    // Release imzasi ise KOSULLU: keystore dosyasi gercekten mevcutsa
    // olusturulur. Boylece keystore olmayan bir makinede assembleRelease
    // "Keystore file not found" ile patlamak yerine imzasiz cikti uretir.
    // Keystore uretimi ayri bir karar (kullanici onayi gerekir).
    val keystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
    if (keystoreFile.exists()) {
      create("release") {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    // ADMOB APP ID BUILD TIPINE GORE.
    //
    // Manifest meta-data'si DERLEME ZAMANINDA sabitlenir, yani `AdIds` gibi
    // calisma zamaninda dallanamaz. Placeholder olmadan tek bir deger yazmak
    // zorunda kalirdik ve iki kotu secenekten birini secerdik: release'de test
    // App ID (gercek birimler calismaz) ya da debug'da gercek App ID
    // (gelistirici kendi canli reklamina tiklar -> AdMob gecersiz trafik).
    //
    // Degerler `AdIds.PRODUCTION_APP_ID` / `TEST_APP_ID` ile AYNI olmak
    // zorunda; `AdIdsConsistencyTest` bunu kilitliyor.
    debug {
      manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }
    release {
      manifestPlaceholders["admobAppId"] = "ca-app-pub-8582550349019790~1660929933"
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Yalnizca yukaridaki kosul saglandiysa signingConfig atanir.
      signingConfig = signingConfigs.findByName("release")
    }
    // debug: signingConfig ATANMAZ -> AGP varsayilan debug imzasi.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Faz 1: secrets ve googleServices yapilandirma bloklari, ilgili plugin'lerle
// birlikte kaldirildi (.env dosyasi yoktu, google-services.json yoktu,
// uretilen BuildConfig alani yoktu).
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  // Faz 3: material-icons-core ve -extended KALDIRILDI.
  //
  // Faz 1'de extended korunmustu cunku 7 yerde yalnizca-extended ikon
  // kullaniliyordu (Pause, VolumeUp, VolumeOff, Adjust, LocalFireDepartment,
  // FlashOn, AcUnit). Faz 3'te tum HUD/panel/modal ikonlari asset pack
  // sprite'lariyla degistirildi; `grep -rn "material.icons" app/src` artik
  // sifir sonuc veriyor, yani bu iki kutuphane olu agirlik.
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Faz 5 — AdMob (Google Mobile Ads SDK) + UMP riza SDK'si.
  // Ikisinin de AAR'i minSdkVersion 23 bildirir; projenin minSdk 24 hedefiyle
  // uyumlu (dogrulama: docs/ADMOB_INTEGRATION.md §1).
  implementation(libs.play.services.ads)
  implementation(libs.user.messaging.platform)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
