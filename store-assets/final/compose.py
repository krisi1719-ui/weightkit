#!/usr/bin/env python3
"""Compose Google Play graphics from raw app screenshots.
usage: compose.py RAW_DIR OUT_DIR ICON_PNG
RAW_DIR/<lang>/01_plate.png ... 08_dark.png, 07_pdf_page.png
"""
import asyncio, base64, os, sys, html
sys.path.insert(0, os.path.dirname(__file__))
from captions import C, FG
from playwright.async_api import async_playwright

RAW, OUT, ICON = sys.argv[1], sys.argv[2], sys.argv[3]
SHOTS = ["01_plate", "02_profiles", "03_pipe", "04_materials", "05_reverse", "06_list", "07_pdf_page", "08_dark"]
W, H = 1080, 1920

def b64(p):
    return "data:image/png;base64," + base64.b64encode(open(p, "rb").read()).decode()

from PIL import Image
import io
def b64img(im):
    buf = io.BytesIO(); im.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

def clean_shot(p):
    """Hide status-bar clutter: notification icons after the clock, wifi '!' and network type."""
    im = Image.open(p).convert("RGB")
    bg = im.getpixel((400, 30))
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle((128, 4, 250, 62), fill=bg)
    d.rectangle((745, 4, 868, 62), fill=bg)
    return b64img(im)

def crop_pdf(p):
    im = Image.open(p).convert("RGB")
    g = im.convert("L").point(lambda v: 255 if v < 245 else 0)
    box = g.getbbox()
    bottom = min(im.height, max(box[3] + 140, int(im.width * 0.9))) if box else im.height
    # skip the page footer: content block ends before the footer line near the bottom
    rows = [y for y in range(im.height) if g.crop((0, y, im.width, y + 1)).getbbox()]
    main = [y for y in rows if y < im.height * 0.8]
    if main: bottom = min(im.height, max(main) + 160)
    left, right = (max(0, box[0] - 50), min(im.width, box[2] + 50)) if box else (0, im.width)
    return b64img(im.crop((left, 0, right, bottom)))

CSS = """
*{margin:0;padding:0;box-sizing:border-box}
body{width:%dpx;height:%dpx;overflow:hidden;font-family:'Inter Display','Inter',sans-serif;
 background:radial-gradient(1200px 900px at 50%% -10%%,#2c3850 0%%,#1d2533 55%%,#161c27 100%%);color:#fff;position:relative}
.cap{position:absolute;left:80px;right:80px;top:80px}
.bar{width:88px;height:11px;border-radius:6px;background:#ec7a1f;margin-bottom:30px}
h1{font-weight:800;font-size:70px;line-height:1.06;letter-spacing:-1.5px}
p{margin-top:18px;font-weight:500;font-size:35px;line-height:1.25;color:#b9c2d0}
.phone{position:absolute;left:50%%;transform:translateX(-50%%);top:410px;width:700px;height:1516px;
 border-radius:76px;background:#0b0f15;padding:16px;
 box-shadow:0 60px 120px rgba(0,0,0,.55),0 0 0 2px #3a4558 inset}
.phone img{width:668px;height:1484px;border-radius:62px;display:block}
.paper{position:absolute;left:50%%;transform:translateX(-50%%) rotate(-1.5deg);top:450px;width:960px;
 background:#fff;border-radius:10px;box-shadow:0 50px 110px rgba(0,0,0,.6);overflow:hidden}
.paper img{width:100%%;display:block}
.badge{position:absolute;right:80px;top:405px;transform:rotate(6deg);background:#ec7a1f;color:#fff;
 font-weight:800;font-size:44px;padding:16px 30px;border-radius:18px;box-shadow:0 20px 40px rgba(0,0,0,.4)}
"""

def shot_html(lang, i, name):
    head, sub = C[lang][i]
    img = os.path.join(RAW, lang, name + ".png")
    if name == "07_pdf_page":
        body = f'<div class="paper"><img src="{crop_pdf(img)}"></div><div class="badge">PDF</div>'
    else:
        body = f'<div class="phone"><img src="{clean_shot(img)}"></div>'
    return f"""<html><head><meta charset="utf-8"><style>{CSS % (W, H)}</style></head><body>
<div class="cap"><div class="bar"></div><h1>{html.escape(head)}</h1><p>{html.escape(sub)}</p></div>{body}</body></html>"""

def feature_html(lang):
    title, tag = FG[lang]
    shot = os.path.join(RAW, lang, "01_plate.png")
    return f"""<html><head><meta charset="utf-8"><style>
*{{margin:0;padding:0;box-sizing:border-box}}
body{{width:1024px;height:500px;overflow:hidden;font-family:'Inter Display','Inter',sans-serif;color:#fff;position:relative;
 background:radial-gradient(900px 600px at 20% 0%,#2e3a52 0%,#1d2533 60%,#161c27 100%)}}
.icon{{position:absolute;left:64px;top:70px;width:132px;height:132px;border-radius:30px;box-shadow:0 18px 40px rgba(0,0,0,.45)}}
.brand{{position:absolute;left:220px;top:92px;font-weight:800;font-size:56px;letter-spacing:-1px}}
.brand span{{color:#ec7a1f}}
.t{{position:absolute;left:220px;top:162px;font-weight:600;font-size:26px;color:#d6dce6;width:420px}}
.tag{{position:absolute;left:64px;top:262px;width:560px;font-weight:700;font-size:40px;line-height:1.15;letter-spacing:-.5px}}
.sub{{position:absolute;left:64px;top:400px;width:560px;font-weight:500;font-size:22px;color:#aab4c3}}
.phone{{position:absolute;right:64px;top:46px;width:300px;height:666px;border-radius:40px;background:#0b0f15;padding:9px;
 box-shadow:0 40px 80px rgba(0,0,0,.6),0 0 0 2px #3a4558 inset;transform:rotate(-6deg)}}
.phone img{{width:282px;height:627px;border-radius:32px;display:block}}
</style></head><body>
<img class="icon" src="{b64(ICON)}"><div class="brand">Weight<span>Kit</span></div><div class="t">{html.escape(title)}</div>
<div class="tag">{html.escape(tag.split(' · ')[0])} · {html.escape(tag.split(' · ')[1])}<br>{html.escape(' · '.join(tag.split(' · ')[2:]))}</div>
<div class="phone"><img src="{clean_shot(shot)}"></div></body></html>"""

async def main():
    os.makedirs(OUT, exist_ok=True)
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": W, "height": H}, device_scale_factor=1)
        for lang in C:
            if not os.path.isdir(os.path.join(RAW, lang)):
                print("missing", lang); continue
            d = os.path.join(OUT, lang); os.makedirs(d, exist_ok=True)
            for i, name in enumerate(SHOTS):
                if not os.path.exists(os.path.join(RAW, lang, name + ".png")):
                    print("missing", lang, name); continue
                await pg.set_viewport_size({"width": W, "height": H})
                await pg.set_content(shot_html(lang, i, name)); await pg.wait_for_timeout(100)
                await pg.screenshot(path=os.path.join(d, f"phone_{i+1}.png"))
            await pg.set_viewport_size({"width": 1024, "height": 500})
            await pg.set_content(feature_html(lang)); await pg.wait_for_timeout(100)
            await pg.screenshot(path=os.path.join(d, "feature_graphic.png"))
            print("done", lang)
        await b.close()

asyncio.run(main())
