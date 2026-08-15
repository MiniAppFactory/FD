// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Faz 1: google-services, KSP, roborazzi ve secrets plugin'leri kaldirildi.
// Dordu de kodda hic kullanilmiyordu (grep ile teyit edildi); AGP 9.1.1 ile
// uyum riski tasiyorlardi ve build suresini uzatiyorlardi.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
