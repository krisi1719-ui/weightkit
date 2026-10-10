# Data verification

All section tables are marked `"verified": false` in `shared/build_data.py` (except rebar, which is computed from nominal diameter). They were entered from the standards below and pass the internal consistency check (area ⇄ tabulated mass ⇄ geometry), but **must be checked against the official tables before release**. Tick each line once checked, then set `verified: True` in `build_data.py`.

| ✔ | Family | Source | Items | What to check |
|---|---|---|---|---|
| ☐ | IPE 80–600 | EN 10365 / DIN 1025-5 | 18 | h, b, tw, tf, r, A, kg/m |
| ☐ | HEA 100–500 | EN 10365 / DIN 1025-3 | 17 | h, b, tw, tf, r, A, kg/m |
| ☐ | HEB 100–500 | EN 10365 / DIN 1025-2 | 17 | h, b, tw, tf, r, A, kg/m |
| ☐ | UPN 80–300 | EN 10365 / DIN 1026-1 | 12 | A, kg/m |
| ☐ | W4–W24 (38 sizes) | AISC Manual / ASTM A6 | 38 | A (in²); weight is the nominal lb/ft in the name |
| ☐ | ISMB 100–600 | IS 808:1989 | 14 | A, kg/m (IS 808:2021 values differ slightly – decide which edition to use) |
| ☐ | ISMC 75–400 | IS 808:1989 | 11 | A, kg/m |
| ☐ | Pipe NPS ½–12 SCH 40 / 80 | ASME B36.10M | 28 | OD, wall |
| ✔ | Rebar Ø6–40 | nominal: π/4·d²·7850 | 14 | – |

Material densities are typical values (EN 10088 for stainless, EN 573/AA for aluminium, supplier datasheets for the rest).

## Not included yet (left out instead of guessing)
- GOST profiles (ГОСТ 8239 / 8240 / 8509 / 8732) – important for the RU/CIS market.
- EN HEM, IPN, equal / unequal angles (EN 10056-1), EN 10219 hollow sections with corner radii.
- AISC S, C, MC, L, HSS.
- IS ISA, ISLB.
- Checker / tread plate (pattern mass differs by manufacturer).

## Known approximations
- Custom rectangular tubes use sharp corners (≈2–4 % heavier than EN 10219 tables for thin walls).
- Custom angle / channel / I / T ignore root and toe radii (≈1–5 % lighter).
- Surface area is not shown for I/U standard profiles (no tabulated perimeter yet).

## Spot-check 2026-10-05 (20 random items, seed 20261004)
Compared against public tables; full table with source URLs in `test-artifacts/data-spotcheck.md`.

| Family | Items checked | Result | Source |
|---|---|---|---|
| HEB | 280, 360, 140, 300 | ✔ (A rounded to whole cm²: 131.4→131, 180.6→181, ≤0.3 %) | structolution.com, piping-world.com |
| HEA | 400, 220 | ✔ | structolution.com, piping-world.com |
| IPE | 400 | ✔ | structolution.com |
| UPN | 100, 120, 300 | ✔ | structolution.com |
| W (AISC) | W10x15, W24x55, W8x10, W14x90, W6x20, W24x76, W6x12 | ✔ (A in in²) | optimalbeam.com (AISC Shapes DB v16) – aisc.org blocked automated access, single source |
| Pipe SCH 40 / 80 | NPS 2½ SCH 40, NPS 8 SCH 80 | ✔ | projectmaterials.com ASME B36.10 chart – single source |
| ISMC | 200 | ✔ (A, kg/m only) | aesteiron.com |

**Mismatches: none.** No values were changed. A spot-check of 20 items is not a full verification, so all
families stay `verified: False` until the remaining items are checked against the official documents.
