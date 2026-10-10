#!/usr/bin/env python3
"""Compose App Store screenshots (1206×2622) from raw iPhone simulator screens.
usage: compose_appstore.py IOS_RAW_DIR ANDROID_RAW_DIR OUT_DIR
IOS_RAW_DIR/<lang>/01_plate.png … 08_dark.png (from the "iOS tests and screenshots" workflow)
ANDROID_RAW_DIR/<lang>/07_pdf_page.png (PDF page rendered by the Android test; same quote layout)
OUT_DIR/<asc-locale>/NN.png
"""
import asyncio, base64, html, io, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from captions import C
from PIL import Image
from playwright.async_api import async_playwright

IOS, ANDROID, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SHOTS = ["01_plate", "02_profiles", "03_pipe", "04_materials", "05_reverse", "06_list", "07_pdf_page", "08_dark"]
ASC = {"en": "en-US", "de": "de-DE", "fr": "fr-FR", "es": "es-ES", "tr": "tr", "ru": "ru"}
W, H = 1206, 2622


def b64img(im):
    buf = io.BytesIO(); im.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def crop_pdf(p):
    im = Image.open(p).convert("RGB")
    g = im.convert("L").point(lambda v: 255 if v < 245 else 0)
    box = g.getbbox()
    rows = [y for y in range(im.height) if g.crop((0, y, im.width, y + 1)).getbbox()]
    main = [y for y in rows if y < im.height * 0.8]
    bottom = min(im.height, max(main) + 160) if main else im.height
    left, right = (max(0, box[0] - 50), min(im.width, box[2] + 50)) if box else (0, im.width)
    return b64img(im.crop((left, 0, right, bottom)))


CSS = """
*{margin:0;padding:0;box-sizing:border-box}
body{width:%dpx;height:%dpx;overflow:hidden;font-family:'Inter Display','Inter','SF Pro Display',sans-serif;
 background:radial-gradient(1400px 1100px at 50%% -10%%,#2c3850 0%%,#1d2533 55%%,#161c27 100%%);color:#fff;position:relative}
.cap{position:absolute;left:90px;right:90px;top:120px}
.bar{width:100px;height:12px;border-radius:6px;background:#ec7a1f;margin-bottom:34px}
h1{font-weight:800;font-size:82px;line-height:1.05;letter-spacing:-1.8px}
p{margin-top:22px;font-weight:500;font-size:41px;line-height:1.25;color:#b9c2d0}
.phone{position:absolute;left:50%%;transform:translateX(-50%%);top:560px;width:880px;height:1946px;
 border-radius:120px;background:#0b0f15;padding:20px;
 box-shadow:0 70px 140px rgba(0,0,0,.55),0 0 0 3px #3a4558 inset}
.phone img{width:840px;height:1826px;border-radius:100px;display:block;object-fit:cover;object-position:top}
.paper{position:absolute;left:50%%;transform:translateX(-50%%) rotate(-1.5deg);top:640px;width:1060px;
 background:#fff;border-radius:12px;box-shadow:0 60px 130px rgba(0,0,0,.6);overflow:hidden}
.paper img{width:100%%;display:block}
.badge{position:absolute;right:90px;top:585px;transform:rotate(6deg);background:#ec7a1f;color:#fff;
 font-weight:800;font-size:50px;padding:18px 34px;border-radius:20px;box-shadow:0 20px 40px rgba(0,0,0,.4)}
"""


def page(lang, i, name):
    head, sub = C[lang][i]
    if name == "07_pdf_page":
        body = f'<div class="paper"><img src="{crop_pdf(os.path.join(ANDROID, lang, name + ".png"))}"></div><div class="badge">PDF</div>'
    else:
        body = f'<div class="phone"><img src="{b64img(Image.open(os.path.join(IOS, lang, name + ".png")).convert("RGB"))}"></div>'
    return f"""<html><head><meta charset="utf-8"><style>{CSS % (W, H)}</style></head><body>
<div class="cap"><div class="bar"></div><h1>{html.escape(head)}</h1><p>{html.escape(sub)}</p></div>{body}</body></html>"""


async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": W, "height": H}, device_scale_factor=1)
        for lang, loc in ASC.items():
            d = os.path.join(OUT, loc); os.makedirs(d, exist_ok=True)
            n = 0
            for i, name in enumerate(SHOTS):
                src = os.path.join(ANDROID if name == "07_pdf_page" else IOS, lang, name + ".png")
                if not os.path.exists(src):
                    print("missing", src); continue
                n += 1
                await pg.set_content(page(lang, i, name)); await pg.wait_for_timeout(150)
                await pg.screenshot(path=os.path.join(d, f"{n:02d}.png"))
            print("done", loc, n)
        await b.close()

asyncio.run(main())
