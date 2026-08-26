# -*- coding: utf-8 -*-
"""Frontline Defender UI art pack -> optimize edilmis WebP drawable uretimi.

KURAL: metin gomulu hicbir gorsel uretilmez. Yalnizca `assets/ui_components/`
altindaki METINSIZ sablonlar islenir; yazi Compose'da ustune cizilir.

Her bileşen:
  1) alfa esigi 16 ile ICERIK KUTUSUNA kirpilir (sablon dosyalarinda 2172x724
     tuvalin ~%30-50'si tamamen seffaf dolgu),
  2) hedef genislige LANCZOS ile indirgenir,
  3) WebP q=82 method=6 olarak yazilir (alfa korunur).
"""
import os
from PIL import Image, ImageEnhance

SRC = r"C:\Users\bhdre\APPDeveloper\projects\Frontline Defender\asset-pack\Frontline_Defender_assets_individual_full\assets\ui_components"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "drawable-nodpi")
assert os.path.isdir(OUT), OUT

ALPHA_CROP = 16
QUALITY = 82

# (kaynak, hedef ad, hedef genislik px, notlar)
JOBS = [
    ("header_plate_large.png",              "ui_plate_header",     900),
    # ("level_nameplate_template.png",      "ui_plate_nameplate",  760),
    #   ^ KULLANILMIYOR: konulacagi tek yer (UnlockConfirmOverlay) cihazda
    #     pencerenin butonlarini gorunmez yapiyordu. Bkz. LevelSelectScreen.kt.
    ("act_banner_template.png",             "ui_plate_act_banner", 900),
    ("button_primary_start_large.png",      "ui_btn_primary",      880),
    ("button_secondary_generic_medium.png", "ui_btn_secondary",    620),
    ("panel_modal_settings_large.png",      "ui_panel_modal",     1040),
    ("icon_button_settings_gear.png",       "ui_ic_gear",          200),
    ("toggle_switch_on.png",                "ui_toggle_on",        200),
    ("selector_language_segmented_dual.png","ui_selector_dual",    840),
]

def crop_content(im, thr=ALPHA_CROP):
    a = im.getchannel("A").point(lambda v: 255 if v > thr else 0)
    b = a.getbbox()
    return im.crop(b) if b else im

def emit(im, name):
    p = os.path.join(OUT, name + ".webp")
    im.save(p, "WEBP", quality=QUALITY, method=6)
    kb = os.path.getsize(p) / 1024.0
    print(f"  {name:22s} {im.size[0]}x{im.size[1]}  ar={im.size[0]/im.size[1]:.2f}  {kb:7.1f} KB")
    return kb

total = 0.0
print("=== bilesenler ===")
for src, name, w in JOBS:
    im = Image.open(os.path.join(SRC, src)).convert("RGBA")
    im = crop_content(im)
    h = max(1, round(im.size[1] * w / im.size[0]))
    im = im.resize((w, h), Image.LANCZOS)
    total += emit(im, name)
    if name == "ui_toggle_on":
        # KAPALI durum TUREVDIR, ayri bir sanat dosyasi yok.
        # (a) yatay AYNA -> topuz sola gecer, sevronlar sola bakar,
        # (b) doygunluk 0.16 + parlaklik 0.55 -> yesil isilti sonuyor.
        off = im.transpose(Image.FLIP_LEFT_RIGHT)
        off = ImageEnhance.Color(off).enhance(0.16)
        off = ImageEnhance.Brightness(off).enhance(0.55)
        total += emit(off, "ui_toggle_off")

print("=== arka plan ===")
bg = Image.open(os.path.join(SRC, "bg_camo_tactical_16x9.png")).convert("RGB")
p = os.path.join(OUT, "bg_camo.webp")
bg.save(p, "WEBP", quality=80, method=6)
kb = os.path.getsize(p)/1024.0; total += kb
print(f"  {'bg_camo':22s} {bg.size[0]}x{bg.size[1]}  {kb:7.1f} KB")

print(f"\nTOPLAM: {total:.1f} KB ({total/1024:.2f} MB)")

# -----------------------------------------------------------------------------
# BOLUM KARTI KUCUK RESIMLERI
# -----------------------------------------------------------------------------
#
# Kartlarda harita gorseli gosterilir. Kaynak olarak savas alani arka planlari
# (`bg_level_01..11.webp`, 1920x1081) DOGRUDAN KULLANILAMAZ:
#
#   1920 x 1081 x 4 bayt = 7,92 MB / harita  ->  11 harita = **87 MB**
#
# Bolum seridi `horizontalScroll` (lazy DEGIL), yani 55 kartin hepsi ayni anda
# besteleniyor ve 11 benzersiz bitmap'in tamami bellege girerdi. Galaxy S8'de
# bu kesin OOM. Bu yuzden ayri, kucuk kupurler uretiliyor:
#
#   320 x 132 x 4 = 169 KB / harita  ->  11 harita = **1,86 MB**  (46 kat az)
#
# Kupur haritanin DIKEY ORTA seridinden alinir: rota oradan gecer, ustteki ve
# alttaki dekor kenarlari kartta zaten okunmazdi.

THUMB_W, THUMB_H = 320, 132

def build_level_thumbs():
    print("=== bolum karti kucuk resimleri ===")
    total = 0.0
    for i in range(1, 12):
        src = os.path.join(OUT, f"bg_level_{i:02d}.webp")
        if not os.path.isfile(src):
            print(f"  ATLANDI (kaynak yok): {src}")
            continue
        im = Image.open(src).convert("RGB")
        w, h = im.size
        band = round(w * THUMB_H / THUMB_W)
        top = max(0, (h - band) // 2)
        im = im.crop((0, top, w, min(h, top + band))).resize((THUMB_W, THUMB_H), Image.LANCZOS)
        name = f"thumb_level_{i:02d}"
        path = os.path.join(OUT, name + ".webp")
        im.save(path, "WEBP", quality=78, method=6)
        kb = os.path.getsize(path) / 1024.0
        total += kb
        print(f"  {name:22s} {THUMB_W}x{THUMB_H}  {kb:7.1f} KB")
    print(f"  kupur toplami: {total:.1f} KB")

# build_level_thumbs()  # DEVRE DISI (2026-08-26): harita-bazli 11 kupurun
# yerini 55 kart paketinin LEVELID-bazli, biyomlu 55 kupuru aldi (asagida).

# -----------------------------------------------------------------------------
# 55 KART PAKETI (Frontline_Defender_55_Cards_COMPLETE, 2026-08-26)
# -----------------------------------------------------------------------------
#
# Kullanicinin GitHub Release uzerinden gonderdigi 198 MB'lik paket:
#   assets/templates/    4 durum sablonu (1086x1448 RGBA, METINSIZ)
#   assets/thumbnails/   55 BENZERSIZ harita gorseli (820x410) — biyomlu
#   preview_cards/       metin gomulu ornekler (KULLANILMAZ, referans)
#
# Sablon 560 px'e indirilir (126 dp kart @4x = 504 px + pay). Kupurler 384x192
# (sablonun kupur penceresi 126 dp kartta 96x48 dp -> @4x 384x192).
#
# DOLU YILDIZ SPRITE'i completed sablonundan KESILIR (completed ile available
# arasindaki piksel farkinin orta kumesi): 1-2 yildizli bolumlerde available
# sablonunun bos konturlarinin uzerine kazanilan sayida bindirilir. Elle
# cizilmis bir yildiz DEGIL — sablonla ayni sanattan geldigi icin birebir
# ayni gorunur.

# SRC = <asset-pack>/Frontline_Defender_assets_individual_full/assets/ui_components
# 55 kart paketi <asset-pack>/Frontline_Defender_55_Cards_COMPLETE altinda.
CARDS = os.path.normpath(os.path.join(SRC, "..", "..", "..", "Frontline_Defender_55_Cards_COMPLETE"))

def build_55_cards():
    tpl_dir = os.path.join(CARDS, "assets", "templates")
    th_dir = os.path.join(CARDS, "assets", "thumbnails")
    if not os.path.isdir(tpl_dir):
        print("55 kart paketi yok, atlandi:", tpl_dir)
        return
    print("=== 55 kart paketi ===")
    total = 0.0
    for state in ("active", "available", "completed", "locked"):
        im = Image.open(os.path.join(tpl_dir, f"card_template_{state}.png")).convert("RGBA")

        # KUPUR PENCERESI SEFFAFLASTIRILIR (sablonda opak koyu doku geliyordu).
        # Boylece Compose'da kupur sablonun ALTINA cizilir ve cerceve, ic
        # golgeler ve NUMARA DAIRESI kupurun ustunde kalir — daire ayrica
        # thumb'larin kendi gomulu kose rozetini de orter (cift rozet olmaz).
        # Pencere: x 126-954, y 133-545 (olculdu). KORUNAN DISK: numara
        # dairesi merkez (214, 200) r=100 — pencereyle ortusen alt yarisi
        # silinmesin diye alfa dokunulmadan birakilir.
        W0, H0 = im.size
        pxs = im.load()
        for y in range(133, 545):
            for x in range(126, 954):
                if (x - 214) ** 2 + (y - 200) ** 2 <= 100 * 100:
                    continue
                r, g, b, a = pxs[x, y]
                pxs[x, y] = (r, g, b, 0)

        w = 560
        h = round(im.size[1] * w / im.size[0])
        im = im.resize((w, h), Image.LANCZOS)
        pth = os.path.join(OUT, f"ui_card_{state}.webp")
        im.save(pth, "WEBP", quality=82, method=6)
        kb = os.path.getsize(pth) / 1024.0; total += kb
        print(f"  ui_card_{state:10s} {w}x{h}  {kb:7.1f} KB")

    # CEPHANELIK SABLONU: available sablonunun YILDIZSIZ turevi.
    #
    # Dukkan kartinda yildiz anlamsiz (kademe 4-9 arasi, 3 yildiza esleyemez);
    # gomulu konturlar kalirsa oyuncu "bu kartta da mi yildiz var" diye okur.
    # Yildiz bandi (x 300-790, y 940-1140) ayni bandin SOLUNDAKI temiz doku
    # seridiyle (x 130-300) dosenerek yamalanir — sablonla ayni malzeme,
    # dikis fark edilmez.
    shop = Image.open(os.path.join(tpl_dir, "card_template_available.png")).convert("RGBA")
    # UCUNCU (dogru) deneme. Ilk ikisi yanildi:
    #   1. soldaki dusey serit -> yildiz UST uclari kaldi (konturlar y 885'te
    #      basliyormus) ve serit o yukseklikte kalkan ikonu iceriyordu;
    #   2. "bandin alt kenarindan yatay serit" -> meger butonun PARLAK ust
    #      kenariymis, bant acik yesil cizgilerle doldu ve dongu wave
    #      satirini da ezdi.
    # Simdi kaynak, sablonun KUPUR PENCERESININ ic dokusu (genis, duz, koyu)
    # ve hedef yalnizca YILDIZLARIN KENDI KUTUSU (x 174-912, y 995-1150 —
    # wave satirinin ALTINDAN baslar, satiri ezmez; konturlarin y 995 ustu
    # ucu yoktur, olculdu: dolu yildiz kutusu 950-1125 ama KONTURLAR 995'te
    # basliyor... guvenli olmak icin 985'ten baslanir, wave metni 950'de
    # bitmisti).
    patch_src = shop.crop((250, 180, 900, 500))
    patch = patch_src.resize((800, 186), Image.LANCZOS)
    shop.paste(patch, (128, 966))
    # kupur penceresi burada da seffaflastirilir (ayni gerekce)
    pxs = shop.load()
    for y in range(133, 545):
        for x in range(126, 954):
            if (x - 214) ** 2 + (y - 200) ** 2 <= 100 * 100:
                continue
            r, g, b, a = pxs[x, y]
            pxs[x, y] = (r, g, b, 0)
    w = 560
    h = round(shop.size[1] * w / shop.size[0])
    shop = shop.resize((w, h), Image.LANCZOS)
    pth = os.path.join(OUT, "ui_card_shop.webp")
    shop.save(pth, "WEBP", quality=82, method=6)
    kb = os.path.getsize(pth) / 1024.0; total += kb
    print(f"  ui_card_shop       {w}x{h}  {kb:7.1f} KB")

    # Dolu yildiz: completed sablonundan SABIT kutuyla kesilir.
    #
    # Fark-tabanli otomatik kesim DENENDI ve YANILDI (14x96'lik dikey serit
    # kesti): iki sablon yildiz bandinin disinda da (buton parlamasi, doku)
    # farklilasiyor ve kume secimi yanlis merkeze kilitlendi. Kutu, orta
    # yildizin OLCULMUS sinirlarindan sabitlendi: x 434-652 (fark taramasi,
    # y=1056 satirinda), y 950-1125 (bant gorselinden; alt uclar 1056-1109
    # olculdu, tepe ~950). Sanat degisirse bu dort sayi yeniden olculur.
    comp = Image.open(os.path.join(tpl_dir, "card_template_completed.png")).convert("RGBA")
    box = (434, 964, 652, 1125)
    star = comp.crop(box)
    # ZEMIN SEFFAFLASTIRMA: kesilen kutu sablonda opak; oldugu gibi bindirilse
    # koseli koyu bir kutu gorunurdu. Yildiz ALTIN (r yuksek, r-b buyuk),
    # zemin zeytin (r-b kucuk) — alfa "altinlik"tan turetilir, kenarlar
    # kademeli oldugu icin kesim yumusak kalir.
    px = star.load()
    for y in range(star.size[1]):
        for x in range(star.size[0]):
            r, g, b, a = px[x, y]
            gold = max(0, min(255, (r - b - 15) * 4))
            px[x, y] = (r, g, b, min(a, gold))
    star.thumbnail((96, 96), Image.LANCZOS)
    pth = os.path.join(OUT, "ui_card_star.webp")
    star.save(pth, "WEBP", quality=85, method=6)
    kb = os.path.getsize(pth) / 1024.0; total += kb
    print(f"  ui_card_star      {star.size[0]}x{star.size[1]}  {kb:7.1f} KB  (kaynak kutu {box})")

    # 55 kupur. ESKI harita-bazli thumb_level_01..11 uretimi ARTIK KULLANILMIYOR;
    # ayni adlar LEVELID bazli 55 dosya olarak yeniden dolduruluyor.
    for i in range(1, 56):
        src = os.path.join(th_dir, f"level_{i:02d}_thumb.png")
        im = Image.open(src).convert("RGB")
        # MERKEZ-BANT KIRPMA. Paketin thumb'lari TUTARSIZ cikti (cihazda
        # goruldu, orneklem md5 degil GOZLE dogrulandi):
        #   · L02/L12 temiz,
        #   · L34/L42/L50 ALT bantta cift dilli GOMULU ISIM ("Çayır Geçidi /
        #     Meadow Pass") — kart basligiyla cift isim,
        #   · L25 kenarlarinda komsu kart cercevesi artigi,
        #   · L50 gorselinin rozetinde YANLIS NUMARA ("51").
        # Sol-ust kirpma yalnizca rozeti atiyordu; isim bandi ve kenar
        # artiklari kaliyordu. Merkez bant hepsini birden disarida birakir:
        #   y: 0,30H-0,72H (rozet ustte, isim altta), x: merkezden 2:1 kutu.
        # 55'ine UNIFORM uygulanir — temiz olanlar da ayni kadraji alir ki
        # serit boyunca kadraj ritmi tutarli kalsin.
        # KADRAJ BUYUTULDU + NATIVE COZUNURLUK (kullanici: "thumbnaillerin
        # kalitesi bozulmus" — hakliydi). Onceki merkez-bant %42'lik dar bir
        # seritti ve 344x172'den 384x192'ye BUYUTULUYORDU; az alan + upscale
        # birlesince kupurler yumusadi. Kirlilikler aslinda uc ayri KENARDA:
        #   gomulu rozet SOL SERIT (x < ~160), gomulu isim ALT BANT (y > ~0,71),
        #   cerceve artigi UST/KENAR (birkac px). Ortadaki temiz alan cok daha
        #   buyuk: x 214-766, y 16-292 -> 552x276 (tam 2:1), oncekinin 2,5 kati
        #   alan ve SIFIR upscale (cikti native).
        im = im.crop((214, 16, 766, 292))
        # resize YOK: 552x276 dogrudan yazilir; pencere 96x48 dp (@4x = 384px)
        # icin fazlasiyla yeterli ve buyutme kaynakli bulanma olusamaz.

        pth = os.path.join(OUT, f"thumb_level_{i:02d}.webp")
        im.save(pth, "WEBP", quality=82, method=6)
        total += os.path.getsize(pth) / 1024.0
    print(f"  55 kupur (552x276 native) yazildi")
    print(f"  55-kart toplami: {total:.1f} KB ({total/1024:.2f} MB)")

build_55_cards()

