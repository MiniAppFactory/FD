package com.miniappfactory.frontlinedefender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Faz 1: sdk = 36 iken bu test "Failed to create a Robolectric sandbox:
// Android SDK 36 requires Java 21 (have Java 17)" ile patliyordu. Yerel
// toolchain JDK 17 (Boom-Blocks dahil tum projeler bu JDK ile yesil derleniyor),
// bu yuzden JDK'yi degistirmek yerine test Java 17 ile uyumlu en yuksek
// seviyeye (API 35) sabitlendi. Bu test bir string kaynagini dogruluyor;
// dogruladigi sey acisindan SDK seviyesi onemsiz.
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Frontline Defender", appName)
  }
}
