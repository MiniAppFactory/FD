package com.miniappfactory.frontlinedefender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.miniappfactory.frontlinedefender.game.ads.AdMobAdHost
import com.miniappfactory.frontlinedefender.game.ads.LoggingAdRewardBridge
import com.miniappfactory.frontlinedefender.game.ui.GameScreen
import com.miniappfactory.frontlinedefender.ui.theme.FrontlineDefenderTheme

class MainActivity : ComponentActivity() {

    /**
     * Faz 5 — reklam katmaninin SAHIBI Activity'dir, Composable degil.
     *
     * Sebep: UMP riza formu ve tam ekran reklamlar Activity ister, ve
     * `MobileAds.initialize` uygulama basina **bir kez** cagrilmalidir.
     * Composable icinde `remember` edilseydi her yapilandirma degisikliginde
     * (donme, dil, karanlik mod) yeni bir host uretme riski olurdu.
     */
    private val adHost = AdMobAdHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // UMP riza + SDK baslatma + on-yukleme. Oyunu BLOKLAMAZ: bu cagri
        // aninda doner, sonuc callback ile gelir ve `setContent` beklemez.
        // Riza alinamazsa reklam istenmez, oyun reklamsiz tam oynanir
        // (offline oynanabilir bir oyundur).
        adHost.initialize(this)

        setContent {
            FrontlineDefenderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen(
                        adHost = adHost,
                        // EKONOMI BAGLANINCA DEGISECEK TEK SATIR:
                        // rewardBridge = EconomyAdRewardBridge(...)
                        // (bkz. docs/ADMOB_INTEGRATION.md §5)
                        rewardBridge = LoggingAdRewardBridge
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        // Bekleyen timeout handler'lari ve on-yuklenmis reklam referanslari
        // birakilir; aksi halde Activity sizar.
        adHost.dispose()
        super.onDestroy()
    }
}
