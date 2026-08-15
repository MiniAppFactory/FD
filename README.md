# Frontline Defender

Android icin 2D **tower defense** oyunu. Oyuncu sabit bir haritada yol kenarindaki
build pad'lere kule yerlestirir, dalga dalga gelen dusmanlari us'e ulasmadan durdurur.
Kule yerlestirme, yukseltme ve satis kararlarini kisitli bir altin ekonomisi ile verir;
her bolumun hedefi tum dalgalari kaybedilen can sayisina gore 1-3 yildizla tamamlamaktir.

- **22 bolumluk kampanya**, olculmus geometriyle baglanmis **11 harita**
- Compose Canvas uzerinde cizim, sabit adimli (fixed timestep) oyun dongusu
- 34 WebP sprite + 15 OGG ses efektinden olusan varlik paketi
- AdMob iskeleti (banner / interstitial / rewarded) — su anda **yalnizca test kimlikleri**

## Teknik ozet

| | |
|---|---|
| Paket adi | `com.miniappfactory.frontlinedefender` |
| minSdk | **24** (Android 7.0 Nougat) |
| Dil / UI | Kotlin + Jetpack Compose |
| Yonelim | **Yalnizca landscape** (yatay) |
| Build sistemi | Gradle (Kotlin DSL) + AGP, version catalog: `gradle/libs.versions.toml` |

## Build

Depo Gradle wrapper icerir; ayrica Gradle kurmak gerekmez.

```bat
git clone <repo-url>
cd source
.\gradlew.bat assembleDebug
```

Cikti: `app\build\outputs\apk\debug\app-debug.apk`

Diger yararli komutlar:

```bat
.\gradlew.bat lint            :: Android Lint
.\gradlew.bat testDebugUnitTest  :: birim testler
.\gradlew.bat installDebug    :: bagli cihaza kur
```

### `local.properties` zorunludur

`local.properties` **gitignore'ludur** (makineye ozgu yol icerir, asla commit edilmez).
Depoyu yeni klonlayan makinede build'in calismasi icin bu dosyayi kendiniz olusturun:

```properties
sdk.dir=C\:\\Users\\<kullanici>\\AppData\\Local\\Android\\Sdk
```

> Ters bolu ve iki nokta karakterleri kacisli yazilir — aksi halde lint `PropertyEscape`
> uyarisi verir. Android Studio projeyi ilk actiginda bu dosyayi otomatik de uretir.

### Release imzalama (opsiyonel)

Release imzasi **kosulludur**: keystore dosyasi yoksa `assembleRelease` patlamaz,
imzasiz cikti uretir. Imzali cikti icin:

```bat
set KEYSTORE_PATH=C:\yol\my-upload-key.jks
set STORE_PASSWORD=...
set KEY_PASSWORD=...
.\gradlew.bat assembleRelease
```

Keystore dosyalari ve sifreler **depoya girmez** (`.gitignore` + global CLAUDE.md §8).

## Reklamlar

`game/ads/AdIds.kt` reklam kimliklerinin tek kaynagidir. `USE_TEST_ADS = true`
oldugu surece tum build tiplerinde Google'in herkese acik test kimlikleri kullanilir.
Gercek (production) kimlikler **ayri bir karardir ve kullanici onayi gerektirir**;
`PRODUCTION_*` sabitleri bilincli olarak bostur.

## Depo duzeni

```
source/                  <- git deposunun KOKU (bu dizin)
  app/src/main/java/.../game/
    engine/    oyun dongusu, carpisma, dalga ilerleyisi
    model/     varliklar, bolum/dalga tanimlari, harita geometrisi
    ui/        Compose Canvas cizimi, HUD, bolum secimi
    ads/       AdMob sarmalayicilari
    audio/     ses calma
    data/      kayit/yukleme
  app/src/main/res/drawable-nodpi/   WebP sprite'lar ve harita arkaplanlari
  app/src/main/res/raw/              OGG ses efektleri
```

### `docs/` neden burada degil

Tasarim dokumanlari (`GDD.md`, `LEVEL_DESIGN.md`, `INTAKE_REPORT.md`, `DECISIONS.md`,
`CHANGELOG.md`, olcum ciktilari) bir ust dizindeki `..\docs\` klasorunde tutulur ve
**bu depoya dahil DEGILDIR**. Ayni sekilde `..\original-ai-studio\` (dokunulmaz orijinal
AI Studio ihraci), `..\asset-pack\` (varlik kaynak dosyalari) ve `..\copied items\` de
depo disindadir. `.gitignore` bu klasor adlarini, `source\` icine bir kopya sizmasi
durumunda commit'e girmemeleri icin ayrica engeller.

Git kurulumu, remote baglama ve rollback talimatlari: `..\docs\GIT_SETUP.md`
