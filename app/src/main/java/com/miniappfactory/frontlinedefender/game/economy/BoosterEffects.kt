package com.miniappfactory.frontlinedefender.game.economy

/**
 * Faz 10 — GUCLENDIRICI ETKILERININ OYNANISA UYGULANMASI.
 *
 * **Android importu YOK.** `BoosterSystem.kt` bir kullanimin izinli olup olmadigina
 * ve *ne kadar* etki uretecegine karar verir; bu dosya o karari savas kaynaklarina
 * cevirir. Ikisinin AYRI olmasinin sebebi test edilebilirlik: `GameEngine`in
 * `SaveManager`/`AudioManager` uzerinden Android bagimliligi var ve birim testi yok,
 * dolayisiyla "Us Tamiri maks cani asti" gibi bir hata motorun icine yazilirsa
 * hicbir test onu yakalayamaz. Motor tarafinda kalan is yalnizca **cagirmak**tir.
 */

/**
 * Bir savasin guclendiriciden etkilenen kaynaklari.
 *
 * @param supply savas ici Tedarik (motorda `GameEngine.gold`).
 * @param lives us cani (motorda `GameEngine.lives`).
 */
data class BattleResources(
    val supply: Int,
    val lives: Int,
)

/**
 * Bir guclendirici aktivasyonunu savas kaynaklarina isler.
 *
 * **REDDEDILEN AKTIVASYON HICBIR SEYI DEGISTIRMEZ.** Motor
 * [BoosterActivation.applied] bayragina bakmak zorunda degildir; reddedilen bir
 * aktivasyon icin bu fonksiyon girdiyi AYNEN dondurur. Bu, "alan degerleri zaten
 * sifirdir" varsayimina guvenmekten daha saglamdir: ileride reddedilen bir kararda
 * bilgi amacli dolu bir alan tasinirsa (ornek: "yetseydi su kadar verecekti")
 * oynanis sessizce bozulmaz.
 *
 * Sira KASITLI: once tahsilat, sonra bagis. Hava Destegi Tedarik HARCAR, Acil
 * Tedarik EKLER; ikisi ayni cagride bulusmasa da tek formulun her ikisini de
 * dogru islemesi motor tarafinda dal birakmaz.
 *
 * @param maxLives us caninin ust siniri (taban + meta yukseltme bonusu). Us Tamiri
 *   bunu ASLA asamaz; asmasi, yildiz notrlugu hesabini ([effectiveStarHealth])
 *   anlamsiz kilardi.
 */
fun applyBoosterToResources(
    res: BattleResources,
    act: BoosterActivation,
    maxLives: Int,
): BattleResources {
    if (!act.applied) return res
    return BattleResources(
        supply = (res.supply - act.supplyCharged + act.supplyGranted).coerceAtLeast(0),
        lives = (res.lives + act.healthRestored).coerceIn(0, maxLives.coerceAtLeast(0)),
    )
}

/**
 * Hava Destegi'nin TEK bir dusmana verecegi hasar.
 *
 * **ZIRHI VE `splashVulnerability`'yi BILEREK YOK SAYAR.** Bu bir eksiklik degil,
 * kisitin ta kendisi: [EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION] = 0,45 degeri
 * "savas basina 2 kullanim x 0,45 = 0,90 < 1,0, yani hava destegi tam canli hicbir
 * dusmani tek basina OLDUREMEZ" garantisi uzerine kalibre edildi ve bu garanti
 * ekonomi testleriyle kilitli. Hasar zirh carpanindan ya da patlama zafiyetinden
 * gecseydi, `splashVulnerability > 1` olan bir dusman tipinde iki kullanim
 * toplam caninin %100'unu asar ve guclendirici tam da engellemek icin var oldugu
 * seye — "dalga temizleme butonu" / pay-to-win — donusurdu. Ust sinir garantisi
 * dusman tablosundaki her degisiklikte yeniden dogrulanmak zorunda kalmasin diye
 * hasar dusman istatistiklerinden BAGIMSIZ tutulur.
 *
 * Oran (sabit hasar degil) olmasi da ayni sebeple kasitli: dusman cani olceklenince
 * kurtarma degeri kendiliginde korunur.
 *
 * @param maxHp hedefin TAM cani (mevcut cani degil) — oran hep maks can uzerinden.
 * @param fraction silinecek maks can orani; 0 veya negatifse hasar yoktur.
 */
fun airSupportDamage(maxHp: Float, fraction: Double): Float {
    if (fraction <= 0.0) return 0f
    return maxHp * fraction.toFloat()
}
