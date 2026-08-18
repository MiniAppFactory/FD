package com.miniappfactory.frontlinedefender.game.audio

/**
 * ---------------------------------------------------------------------------
 * MOTORUN DOKUNSAL GERI BILDIRIM DIKISI
 * ---------------------------------------------------------------------------
 * SAF KOTLIN. Bu dosyada Android importu YOKTUR ve olmamalidir; `GameEngine`
 * yalnizca bunu tanir, `HapticsManager`i (ve dolayisiyla `Vibrator`/`View`i)
 * TANIMAZ.
 *
 * ---------------------------------------------------------------------------
 * NEDEN AKIS (StateFlow) DEGIL
 * ---------------------------------------------------------------------------
 * Ilk oneri motorun bir olay akisi yayinlamasi, Compose tarafinin toplayip
 * titretmesiydi. Iki somut sebeple reddedildi:
 *
 *  1. `MutableStateFlow` AYNI DEGERI TEKRAR YAYMAZ. Zincir kademesi arka
 *     arkaya iki kez 2'ye ciktiginda ikinci tirmanis DUSERDI: oyuncu iki
 *     kademe atlar, tek titresim hisseder. Duzeltmek icin monoton artan bir
 *     sayac ya da `SharedFlow` gerekirdi — akisin sagladigi sadelik zaten
 *     kaybolurdu.
 *
 *  2. Akis toplama EN AZ BIR KARE gecikir. Cevredeki kodun kendi kurali
 *     "uc kanal AYNI KAREDE" (bkz. `GameEngine`deki zincir dali: gorsel
 *     patlama + ses + hit stop). Titresimi bir kare geriye atmak, bu ajanin
 *     kabul kurallarina gore bir OYNANIS HATASIDIR.
 *
 * Test edilebilirlik gerekcesi de gecerli degildi: `GameEngine` zaten
 * `AudioManager(Context)` aliyor ve saf JUnit'te ornek uretilemiyor
 * (bkz. `StarRatingTest`in "TEST EDILEBILIRLIK BORCU" notu). Bu arayuz
 * durumu KOTULESTIRMEZ, iyilestirir: sahte bir uygulama ile "kademe 3 tam
 * bir onay uretti" artik dogrulanabilir.
 */
interface HapticsFeedback {

    /**
     * Oldurme zinciri bir kademe atladi.
     *
     * @param tier ulasilan kademe, 1 tabanli. Siddet kademeyle ARTAR — ses
     *   tarafindaki `sfx_combo_up_1..4` yukselen setiyle ayni merdiven.
     */
    fun onComboTierUp(tier: Int)

    /**
     * Us hasar aldi (dusman sizdi).
     *
     * Oyuncunun HISSETMESI gereken tek olumsuz olay; bu yuzden en sert desen
     * ve cift darbe — iyi haberle karistirilamaz.
     */
    fun onBaseHit()
}
