#!/usr/bin/env python3
"""6.9" App Store screenshots (1320×2868) from the 6.3" ones (1206×2622, same aspect ratio).
usage: make_appstore_69.py   (store-assets/appstore/<locale>/NN.png -> store-assets/appstore-69/<locale>/NN.png)"""
import glob, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "store-assets")
for f in sorted(glob.glob(os.path.join(ROOT, "appstore", "*", "*.png"))):
    loc = os.path.basename(os.path.dirname(f))
    if loc == "review":
        continue
    im = Image.open(f)
    if im.mode != "RGB":  # no alpha channel in App Store screenshots
        im = im.convert("RGB"); im.save(f, optimize=True)
    w, h = 1320, 2868
    scale = max(w / im.width, h / im.height)
    im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    left, top = (im.width - w) // 2, (im.height - h) // 2
    out = os.path.join(ROOT, "appstore-69", loc, os.path.basename(f))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.crop((left, top, left + w, top + h)).save(out, optimize=True)
    print(out)
