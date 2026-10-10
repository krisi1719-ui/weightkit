#!/usr/bin/env python3
"""Builds the feature × platform × run table for TEST_REPORT.md from test-artifacts/{unit,ui}/*-runN.txt."""
import os, re, sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
A = os.path.join(ROOT, "test-artifacts")

# flow -> (iOS test name regex, Android test name regex)
FLOWS = [
    ("1 First launch: plate result, no ad/paywall/consent", r"testF01", r"\.f01"),
    ("2 Every shape chip, live result, values kept", r"testF02", r"\.f02_"),
    ("3 Profiles free/Pro in trial and after", r"testF03", r"\.f03"),
    ("4 Rebar, materials (search/select/density), custom material", r"testF04", r"\.f04"),
    ("5 Units toggle (toolbar + Settings)", r"testF05", r"\.f05_"),
    ("6 Price → cost, target → reverse", r"testF06", r"\.f06"),
    ("7 Lists: add 3, edit name/waste/markup, delete", r"testF07", r"\.f07_"),
    ("8 Second list: trial vs free", r"testF08", r"\.f08"),
    ("9 Export PDF/CSV, share sheet, content", r"testF09", r"\.f09"),
    ("10 Settings: theme, currency, company", r"testF10", r"\.f10_"),
    ("11 Paywall: price, buy, restore, refund, pending", r"testF11", r"\.f11_"),
    ("12 Ads (test IDs): consent, banner, interstitial, rewarded", r"testF12", r"\.f12_"),
    ("13 Localization bg/de/fr/es/tr/ru/en, light+dark", r"testF13_", r"\.f13_"),
    ("14 Accessibility: largest text, labels, targets", r"testF14", r"\.f14_"),
    ("15 Background / process death / recreate", r"testF15", r"\.f15_"),
]


def load(path):
    if not os.path.exists(path):
        return None
    res = {}
    for line in open(path, encoding="utf-8"):
        m = re.match(r"(Passed|Failed|Expected Failure|Skipped)\s+(\S+)", line)
        if m:
            res[m.group(2)] = m.group(1)
    return res


def cell(res, pattern):
    if res is None:
        return "–"
    hits = [v for k, v in res.items() if re.search(pattern, k)]
    if not hits:
        return "n/a"
    if any(v == "Failed" for v in hits):
        return f"FAIL ({sum(v == 'Failed' for v in hits)}/{len(hits)})"
    if any(v == "Expected Failure" for v in hits):
        return "pass*"
    if all(v == "Skipped" for v in hits):
        return "skipped"
    return "pass" if len(hits) == 1 else f"pass ({len(hits)})"


def main():
    runs = {(p, i): load(os.path.join(A, "ui", f"{p}-run{i}.txt")) for p in ("ios", "android") for i in (1, 2, 3)}
    out = ["| Flow | iOS 1 | iOS 2 | iOS 3 | Android 1 | Android 2 | Android 3 |", "|---|---|---|---|---|---|---|"]
    for name, ios_re, and_re in FLOWS:
        row = [name] + [cell(runs[("ios", i)], ios_re) for i in (1, 2, 3)] + [cell(runs[("android", i)], and_re) for i in (1, 2, 3)]
        out.append("| " + " | ".join(row) + " |")
    print("\n".join(out))


if __name__ == "__main__":
    sys.exit(main())
