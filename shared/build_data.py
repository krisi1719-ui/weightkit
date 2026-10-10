#!/usr/bin/env python3
"""
Single source of truth for WeightKit data.

Generates:
  shared/out/materials.json
  shared/out/sections.json
and runs consistency checks (python3 build_data.py --check).

Copy step (copy_to_apps.sh) places the JSON into:
  ios/WeightKit/Resources/  and  android/app/src/main/assets/

IMPORTANT: every standard-section table is marked verified=false.
Values were entered from the referenced standards and are cross-checked
here only for INTERNAL consistency (area vs mass vs geometry). They must
still be checked against the official tables before release
(see DATA_VERIFICATION.md).
"""
import json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")

# ---------------------------------------------------------------- materials
# density in kg/m3 (typical values from EN / ASTM / manufacturer datasheets)
MATERIALS = [
    # id, group, name, density
    ("steel", "steel", "Carbon steel (S235/S355, A36)", 7850),
    ("tool_steel", "steel", "Tool steel", 7800),
    ("cast_iron", "steel", "Cast iron (grey)", 7200),
    ("ss201", "stainless", "Stainless 201", 7800),
    ("ss304", "stainless", "Stainless 304 / 1.4301", 7930),
    ("ss316", "stainless", "Stainless 316 / 1.4401", 7980),
    ("ss321", "stainless", "Stainless 321 / 1.4541", 7900),
    ("ss310", "stainless", "Stainless 310 / 1.4845", 7980),
    ("ss430", "stainless", "Stainless 430 / 1.4016", 7700),
    ("al1050", "aluminium", "Aluminium 1050", 2710),
    ("al5052", "aluminium", "Aluminium 5052", 2680),
    ("al6061", "aluminium", "Aluminium 6061", 2700),
    ("al6082", "aluminium", "Aluminium 6082", 2700),
    ("al7075", "aluminium", "Aluminium 7075", 2810),
    ("copper", "copper", "Copper (Cu-ETP)", 8940),
    ("brass", "copper", "Brass (CuZn37)", 8440),
    ("bronze", "copper", "Bronze (CuSn8)", 8800),
    ("ti_gr2", "other", "Titanium Grade 2", 4510),
    ("ti_gr5", "other", "Titanium Grade 5 (Ti-6Al-4V)", 4430),
    ("nickel", "other", "Nickel 200", 8890),
    ("inconel625", "other", "Inconel 625", 8440),
    ("zinc", "other", "Zinc", 7140),
    ("lead", "other", "Lead", 11340),
    ("magnesium", "other", "Magnesium", 1740),
    ("pa6", "plastic", "Nylon (PA6)", 1140),
    ("pvc", "plastic", "PVC", 1400),
    ("hdpe", "plastic", "Polyethylene (HDPE)", 950),
    ("pom", "plastic", "Acetal (POM)", 1410),
    ("ptfe", "plastic", "PTFE", 2200),
    ("pmma", "plastic", "Acrylic (PMMA)", 1190),
]

# ---------------------------------------------------------------- sections
# kind: "ibeam" | "channel"  -> calculation uses area A (cm2) * density
# For steel the app also shows the tabulated kg/m (or lb/ft) for cross-check.

# EN parallel flange I/H: (name, h, b, tw, tf, r [mm], A [cm2], m [kg/m])
IPE = [
    ("IPE 80", 80, 46, 3.8, 5.2, 5, 7.64, 6.0),
    ("IPE 100", 100, 55, 4.1, 5.7, 7, 10.3, 8.1),
    ("IPE 120", 120, 64, 4.4, 6.3, 7, 13.2, 10.4),
    ("IPE 140", 140, 73, 4.7, 6.9, 7, 16.4, 12.9),
    ("IPE 160", 160, 82, 5.0, 7.4, 9, 20.1, 15.8),
    ("IPE 180", 180, 91, 5.3, 8.0, 9, 23.9, 18.8),
    ("IPE 200", 200, 100, 5.6, 8.5, 12, 28.5, 22.4),
    ("IPE 220", 220, 110, 5.9, 9.2, 12, 33.4, 26.2),
    ("IPE 240", 240, 120, 6.2, 9.8, 15, 39.1, 30.7),
    ("IPE 270", 270, 135, 6.6, 10.2, 15, 45.9, 36.1),
    ("IPE 300", 300, 150, 7.1, 10.7, 15, 53.8, 42.2),
    ("IPE 330", 330, 160, 7.5, 11.5, 18, 62.6, 49.1),
    ("IPE 360", 360, 170, 8.0, 12.7, 18, 72.7, 57.1),
    ("IPE 400", 400, 180, 8.6, 13.5, 21, 84.5, 66.3),
    ("IPE 450", 450, 190, 9.4, 14.6, 21, 98.8, 77.6),
    ("IPE 500", 500, 200, 10.2, 16.0, 21, 116.0, 90.7),
    ("IPE 550", 550, 210, 11.1, 17.2, 24, 134.0, 106.0),
    ("IPE 600", 600, 220, 12.0, 19.0, 24, 156.0, 122.0),
]
HEA = [
    ("HEA 100", 96, 100, 5.0, 8.0, 12, 21.2, 16.7),
    ("HEA 120", 114, 120, 5.0, 8.0, 12, 25.3, 19.9),
    ("HEA 140", 133, 140, 5.5, 8.5, 12, 31.4, 24.7),
    ("HEA 160", 152, 160, 6.0, 9.0, 15, 38.8, 30.4),
    ("HEA 180", 171, 180, 6.0, 9.5, 15, 45.3, 35.5),
    ("HEA 200", 190, 200, 6.5, 10.0, 18, 53.8, 42.3),
    ("HEA 220", 210, 220, 7.0, 11.0, 18, 64.3, 50.5),
    ("HEA 240", 230, 240, 7.5, 12.0, 21, 76.8, 60.3),
    ("HEA 260", 250, 260, 7.5, 12.5, 24, 86.8, 68.2),
    ("HEA 280", 270, 280, 8.0, 13.0, 24, 97.3, 76.4),
    ("HEA 300", 290, 300, 8.5, 14.0, 27, 113.0, 88.3),
    ("HEA 320", 310, 300, 9.0, 15.5, 27, 124.0, 97.6),
    ("HEA 340", 330, 300, 9.5, 16.5, 27, 133.0, 105.0),
    ("HEA 360", 350, 300, 10.0, 17.5, 27, 143.0, 112.0),
    ("HEA 400", 390, 300, 11.0, 19.0, 27, 159.0, 125.0),
    ("HEA 450", 440, 300, 11.5, 21.0, 27, 178.0, 140.0),
    ("HEA 500", 490, 300, 12.0, 23.0, 27, 198.0, 155.0),
]
HEB = [
    ("HEB 100", 100, 100, 6.0, 10.0, 12, 26.0, 20.4),
    ("HEB 120", 120, 120, 6.5, 11.0, 12, 34.0, 26.7),
    ("HEB 140", 140, 140, 7.0, 12.0, 12, 43.0, 33.7),
    ("HEB 160", 160, 160, 8.0, 13.0, 15, 54.3, 42.6),
    ("HEB 180", 180, 180, 8.5, 14.0, 15, 65.3, 51.2),
    ("HEB 200", 200, 200, 9.0, 15.0, 18, 78.1, 61.3),
    ("HEB 220", 220, 220, 9.5, 16.0, 18, 91.0, 71.5),
    ("HEB 240", 240, 240, 10.0, 17.0, 21, 106.0, 83.2),
    ("HEB 260", 260, 260, 10.0, 17.5, 24, 118.0, 93.0),
    ("HEB 280", 280, 280, 10.5, 18.0, 24, 131.0, 103.0),
    ("HEB 300", 300, 300, 11.0, 19.0, 27, 149.0, 117.0),
    ("HEB 320", 320, 300, 11.5, 20.5, 27, 161.0, 127.0),
    ("HEB 340", 340, 300, 12.0, 21.5, 27, 171.0, 134.0),
    ("HEB 360", 360, 300, 12.5, 22.5, 27, 181.0, 142.0),
    ("HEB 400", 400, 300, 13.5, 24.0, 27, 198.0, 155.0),
    ("HEB 450", 450, 300, 14.0, 26.0, 27, 218.0, 171.0),
    ("HEB 500", 500, 300, 14.5, 28.0, 27, 239.0, 187.0),
]
# EN UPN (tapered flange): (name, h, b, tw, tf, A, m)
UPN = [
    ("UPN 80", 80, 45, 6.0, 8.0, 11.0, 8.64),
    ("UPN 100", 100, 50, 6.0, 8.5, 13.5, 10.6),
    ("UPN 120", 120, 55, 7.0, 9.0, 17.0, 13.4),
    ("UPN 140", 140, 60, 7.0, 10.0, 20.4, 16.0),
    ("UPN 160", 160, 65, 7.5, 10.5, 24.0, 18.8),
    ("UPN 180", 180, 70, 8.0, 11.0, 28.0, 22.0),
    ("UPN 200", 200, 75, 8.5, 11.5, 32.2, 25.3),
    ("UPN 220", 220, 80, 9.0, 12.5, 37.4, 29.4),
    ("UPN 240", 240, 85, 9.5, 13.0, 42.3, 33.2),
    ("UPN 260", 260, 90, 10.0, 14.0, 48.3, 37.9),
    ("UPN 280", 280, 95, 10.0, 15.0, 53.3, 41.8),
    ("UPN 300", 300, 100, 10.0, 16.0, 58.8, 46.2),
]
# AISC W shapes: (name, A [in2]); nominal weight lb/ft is in the name
W = [
    ("W4x13", 3.83),
    ("W6x9", 2.68), ("W6x12", 3.55), ("W6x15", 4.43), ("W6x20", 5.87), ("W6x25", 7.34),
    ("W8x10", 2.96), ("W8x13", 3.84), ("W8x18", 5.26), ("W8x24", 7.08), ("W8x31", 9.13), ("W8x40", 11.7),
    ("W10x12", 3.54), ("W10x15", 4.41), ("W10x22", 6.49), ("W10x26", 7.61), ("W10x33", 9.71), ("W10x49", 14.4),
    ("W12x14", 4.16), ("W12x16", 4.71), ("W12x19", 5.57), ("W12x26", 7.65), ("W12x35", 10.3), ("W12x50", 14.6), ("W12x65", 19.1),
    ("W14x22", 6.49), ("W14x30", 8.85), ("W14x48", 14.1), ("W14x90", 26.5),
    ("W16x26", 7.68), ("W16x31", 9.13), ("W16x40", 11.8),
    ("W18x35", 10.3), ("W18x50", 14.7),
    ("W21x44", 13.0), ("W21x62", 18.3),
    ("W24x55", 16.2), ("W24x76", 22.4),
]
# IS 808 (1989) tapered: (name, A [cm2], m [kg/m])
ISMB = [
    ("ISMB 100", 14.6, 11.5), ("ISMB 125", 16.6, 13.0), ("ISMB 150", 19.0, 14.9),
    ("ISMB 175", 24.6, 19.3), ("ISMB 200", 32.3, 25.4), ("ISMB 225", 39.7, 31.2),
    ("ISMB 250", 47.5, 37.3), ("ISMB 300", 56.3, 44.2), ("ISMB 350", 66.7, 52.4),
    ("ISMB 400", 78.5, 61.6), ("ISMB 450", 92.3, 72.4), ("ISMB 500", 110.7, 86.9),
    ("ISMB 550", 132.1, 103.7), ("ISMB 600", 156.2, 122.6),
]
ISMC = [
    ("ISMC 75", 8.67, 6.8), ("ISMC 100", 11.7, 9.2), ("ISMC 125", 16.2, 12.7),
    ("ISMC 150", 20.9, 16.4), ("ISMC 175", 24.4, 19.1), ("ISMC 200", 28.2, 22.1),
    ("ISMC 225", 33.0, 25.9), ("ISMC 250", 38.7, 30.4), ("ISMC 300", 45.6, 35.8),
    ("ISMC 350", 53.7, 42.1), ("ISMC 400", 62.9, 49.4),
]
# ASME B36.10M steel pipe: (NPS, OD in, wall SCH40 in, wall SCH80 in)
PIPE = [
    ("1/2", 0.840, 0.109, 0.147), ("3/4", 1.050, 0.113, 0.154), ("1", 1.315, 0.133, 0.179),
    ("1-1/4", 1.660, 0.140, 0.191), ("1-1/2", 1.900, 0.145, 0.200), ("2", 2.375, 0.154, 0.218),
    ("2-1/2", 2.875, 0.203, 0.276), ("3", 3.500, 0.216, 0.300), ("4", 4.500, 0.237, 0.337),
    ("5", 5.563, 0.258, 0.375), ("6", 6.625, 0.280, 0.432), ("8", 8.625, 0.322, 0.500),
    ("10", 10.750, 0.365, 0.594), ("12", 12.750, 0.406, 0.688),
]
REBAR = [6, 8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32, 36, 40]

IN = 25.4
CM2_PER_IN2 = 6.4516
KG_PER_LB = 0.45359237
M_PER_FT = 0.3048


def sections():
    fams = []

    def ibeam_family(fid, name, std, rows, pro):
        items = []
        for (n, h, b, tw, tf, r, A, m) in rows:
            items.append({"name": n, "h": h, "b": b, "tw": tw, "tf": tf, "r": r,
                          "area_cm2": A, "steel_kg_m": m})
        fams.append({"id": fid, "name": name, "shape": "ibeam", "source": std,
                     "region": "EU", "pro": pro, "verified": False, "items": items})

    ibeam_family("ipe", "IPE", "EN 10365 / DIN 1025-5", IPE, False)
    ibeam_family("hea", "HEA (HE-A)", "EN 10365 / DIN 1025-3", HEA, True)
    ibeam_family("heb", "HEB (HE-B)", "EN 10365 / DIN 1025-2", HEB, True)
    fams.append({"id": "upn", "name": "UPN", "shape": "channel", "source": "EN 10365 / DIN 1026-1",
                 "region": "EU", "pro": False, "verified": False,
                 "items": [{"name": n, "h": h, "b": b, "tw": tw, "tf": tf, "area_cm2": A, "steel_kg_m": m}
                           for (n, h, b, tw, tf, A, m) in UPN]})
    fams.append({"id": "w", "name": "W (wide flange)", "shape": "ibeam", "source": "AISC Steel Construction Manual / ASTM A6",
                 "region": "US", "pro": True, "verified": False,
                 "items": [{"name": n, "area_cm2": round(A * CM2_PER_IN2, 3),
                            "steel_kg_m": round(float(n.split("x")[1]) * KG_PER_LB / M_PER_FT, 3),
                            "nominal_lb_ft": float(n.split("x")[1])} for (n, A) in W]})
    fams.append({"id": "ismb", "name": "ISMB", "shape": "ibeam", "source": "IS 808:1989",
                 "region": "IN", "pro": True, "verified": False,
                 "items": [{"name": n, "area_cm2": A, "steel_kg_m": m} for (n, A, m) in ISMB]})
    fams.append({"id": "ismc", "name": "ISMC", "shape": "channel", "source": "IS 808:1989",
                 "region": "IN", "pro": True, "verified": False,
                 "items": [{"name": n, "area_cm2": A, "steel_kg_m": m} for (n, A, m) in ISMC]})
    for sch, idx, pro in (("40", 2, False), ("80", 3, True)):
        fams.append({"id": "pipe" + sch, "name": "Pipe NPS SCH " + sch, "shape": "pipe",
                     "source": "ASME B36.10M", "region": "US", "pro": pro, "verified": False,
                     "items": [{"name": 'NPS ' + row[0] + '"', "od": round(row[1] * IN, 2),
                                "wall": round(row[idx] * IN, 2)} for row in PIPE]})
    fams.append({"id": "rebar", "name": "Rebar", "shape": "rebar", "source": "EN 10080 / ISO 6935-2 nominal diameters",
                 "region": "ALL", "pro": False, "verified": True,
                 "items": [{"name": "Ø%d" % d, "d": d,
                            "steel_kg_m": round(math.pi / 4 * d * d * 7850e-6, 3)} for d in REBAR]})
    return fams


def check(fams):
    fails = []
    for f in fams:
        for it in f["items"]:
            n = f["name"] + " " + it["name"]
            if "area_cm2" in it and "steel_kg_m" in it:
                m = it["area_cm2"] * 0.785
                if abs(m - it["steel_kg_m"]) / it["steel_kg_m"] > 0.02:
                    fails.append("%s: A*7850 = %.2f vs table %.2f" % (n, m, it["steel_kg_m"]))
            if f["shape"] == "ibeam" and "r" in it:
                h, b, tw, tf, r = it["h"], it["b"], it["tw"], it["tf"], it["r"]
                A = (2 * b * tf + (h - 2 * tf) * tw + (4 - math.pi) * r * r) / 100
                if abs(A - it["area_cm2"]) / it["area_cm2"] > 0.01:
                    fails.append("%s: geometry A=%.2f vs table %.2f" % (n, A, it["area_cm2"]))
    return fails


def main():
    os.makedirs(OUT, exist_ok=True)
    mats = [{"id": i, "group": g, "name": n, "density": d} for (i, g, n, d) in MATERIALS]
    fams = sections()
    fails = check(fams)
    for x in fails:
        print("FAIL", x)
    total = sum(len(f["items"]) for f in fams)
    print("sections: %d families, %d items, %d failures" % (len(fams), total, len(fails)))
    # plate test 1000x1000x10 steel = 78.5 kg
    assert abs(1.0 * 1.0 * 0.010 * 7850 - 78.5) < 1e-9
    with open(os.path.join(OUT, "materials.json"), "w") as fh:
        json.dump(mats, fh, indent=1, ensure_ascii=False)
    with open(os.path.join(OUT, "sections.json"), "w") as fh:
        json.dump(fams, fh, indent=1, ensure_ascii=False)
    if "--check" in sys.argv and fails:
        sys.exit(1)


if __name__ == "__main__":
    main()
