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
