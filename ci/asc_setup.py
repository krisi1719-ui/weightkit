"""App Store Connect setup through the API (everything except prices and App Privacy, which are
done in the website): app info, categories, content rights, age rating, localized texts, version
fields, screenshots, in-app purchases (+ localizations, availability, review screenshot) and app
availability in all countries.

Env: APP_STORE_CONNECT_ISSUER_ID, APP_STORE_CONNECT_KEY_IDENTIFIER, APP_STORE_CONNECT_PRIVATE_KEY,
     ASC_APP_ID, STEPS (comma list: meta,age,shots,iaps,availability; default all)
Data: ci/appstore_metadata.json, store-assets/appstore/<locale>/NN.png, store-assets/appstore/review/paywall.png
"""
import glob, hashlib, json, os, sys, time
import jwt, requests

API = "https://api.appstoreconnect.apple.com"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
META = json.load(open(os.path.join(ROOT, "ci", "appstore_metadata.json"), encoding="utf-8"))
APP = os.environ["ASC_APP_ID"]
_steps_file = os.path.join(ROOT, "ci", "asc_steps.txt")  # used when started by a push (no workflow inputs)
STEPS = set((os.environ.get("STEPS") or (open(_steps_file).read().strip() if os.path.exists(_steps_file) else "")
             or "meta,age,shots,iaps,availability,build").replace(" ", "").split(","))
ERRORS = []


def token():
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["APP_STORE_CONNECT_ISSUER_ID"], "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
        os.environ["APP_STORE_CONNECT_PRIVATE_KEY"], algorithm="ES256",
        headers={"kid": os.environ["APP_STORE_CONNECT_KEY_IDENTIFIER"], "typ": "JWT"})


S = requests.Session()
_tok_time = 0


def call(method, path, body=None, ok=(200, 201, 204), soft=False):
    global _tok_time
    if time.time() - _tok_time > 900:
        S.headers["Authorization"] = f"Bearer {token()}"
        _tok_time = time.time()
    url = path if path.startswith("http") else API + path
    r = S.request(method, url, json=body)
    if r.status_code not in ok:
        msg = f"!! {method} {path} -> {r.status_code}\n{r.text[:2500]}"
        print(msg)
        if soft:
            ERRORS.append(msg)
            return None
        r.raise_for_status()
    return r.json() if r.content else {}


def get_all(path):
    out, url = [], path
    while url:
        j = call("GET", url)
        out += j.get("data", [])
        url = j.get("links", {}).get("next")
    return out


def upload(asset_ops, path):
    data = open(path, "rb").read()
    for op in asset_ops:
        chunk = data[op["offset"]: op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders", [])}
        r = requests.request(op["method"], op["url"], data=chunk, headers=headers)
        if r.status_code >= 300:
            raise RuntimeError(f"upload {path}: {r.status_code} {r.text[:300]}")
    return hashlib.md5(data).hexdigest()


# ---------------------------------------------------------------- app info
app = call("GET", f"/v1/apps/{APP}")["data"]
print("App:", app["attributes"]["name"], app["attributes"]["bundleId"])
infos = call("GET", f"/v1/apps/{APP}/appInfos")["data"]
info = next((i for i in infos if i["attributes"].get("state") not in ("READY_FOR_DISTRIBUTION", "READY_FOR_SALE")), infos[0])
info_id = info["id"]
versions = call("GET", f"/v1/apps/{APP}/appStoreVersions?filter[platform]=IOS&limit=10")["data"]
ver = next(v for v in versions if v["attributes"]["appStoreState"] in
           ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED"))
vid = ver["id"]
print("Version", ver["attributes"]["versionString"], ver["attributes"]["appStoreState"])

# ---------------------------------------------------------------- version string = MARKETING_VERSION of the Xcode project
if "version" in STEPS:
    import re
    mv = re.search(r"MARKETING_VERSION = ([0-9.]+);", open(os.path.join(ROOT, "ios", "WeightKit.xcodeproj", "project.pbxproj")).read()).group(1)
    if ver["attributes"]["versionString"] != mv:
        r = call("PATCH", f"/v1/appStoreVersions/{vid}", {"data": {"type": "appStoreVersions", "id": vid,
                 "attributes": {"versionString": mv}}}, soft=True)
        if r is not None:
            ver = r["data"]
    print("Version string:", ver["attributes"]["versionString"])

if "meta" in STEPS:
    call("PATCH", f"/v1/apps/{APP}", {"data": {"type": "apps", "id": APP,
         "attributes": {"contentRightsDeclaration": "DOES_NOT_USE_THIRD_PARTY_CONTENT"}}}, soft=True)
    call("PATCH", f"/v1/appInfos/{info_id}", {"data": {"type": "appInfos", "id": info_id, "relationships": {
        "primaryCategory": {"data": {"type": "appCategories", "id": META["primaryCategory"]}},
        "secondaryCategory": {"data": {"type": "appCategories", "id": META["secondaryCategory"]}}}}}, soft=True)
    print("Categories + content rights set")

    # app name / subtitle / privacy URL per language
    have = {l["attributes"]["locale"]: l for l in get_all(f"/v1/appInfos/{info_id}/appInfoLocalizations")}
    for loc, m in META["locales"].items():
        attrs = {"name": m["name"], "subtitle": m["subtitle"], "privacyPolicyUrl": META["privacyPolicyUrl"]}
        if loc in have:
            r = call("PATCH", f"/v1/appInfoLocalizations/{have[loc]['id']}", {"data": {"type": "appInfoLocalizations",
                     "id": have[loc]["id"], "attributes": attrs}}, soft=True)
        else:
            r = call("POST", "/v1/appInfoLocalizations", {"data": {"type": "appInfoLocalizations",
                     "attributes": dict(attrs, locale=loc),
                     "relationships": {"appInfo": {"data": {"type": "appInfos", "id": info_id}}}}}, soft=True)
        print("App info", loc, "OK" if r is not None else "FAILED")

    # version fields
    call("PATCH", f"/v1/appStoreVersions/{vid}", {"data": {"type": "appStoreVersions", "id": vid,
         "attributes": {"copyright": META["copyright"]}}}, soft=True)
    have = {l["attributes"]["locale"]: l for l in get_all(f"/v1/appStoreVersions/{vid}/appStoreVersionLocalizations")}
    for loc, m in META["locales"].items():
        attrs = {"description": m["description"], "keywords": m["keywords"], "promotionalText": m["promotionalText"],
                 "supportUrl": META["supportUrl"]}
        if loc in have:
            r = call("PATCH", f"/v1/appStoreVersionLocalizations/{have[loc]['id']}", {"data": {
                     "type": "appStoreVersionLocalizations", "id": have[loc]["id"], "attributes": attrs}}, soft=True)
        else:
            r = call("POST", "/v1/appStoreVersionLocalizations", {"data": {"type": "appStoreVersionLocalizations",
                     "attributes": dict(attrs, locale=loc),
                     "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}}, soft=True)
        print("Version texts", loc, "OK" if r is not None else "FAILED")

# ---------------------------------------------------------------- age rating
if "age" in STEPS:
    decl = call("GET", f"/v1/appInfos/{info_id}/ageRatingDeclaration")["data"]
    print("Age rating fields:", json.dumps(decl["attributes"]))
    want = {}
    for k, v in decl["attributes"].items():
        if k in ("kidsAgeBand", "ageRatingOverride", "ageRatingOverrideV2", "koreaAgeRatingOverride", "developerAgeRatingInfoUrl",
                 "gracRatingClassificationNumber"):
            continue
        if k == "advertising":
            want[k] = True          # the free version shows ads
        elif isinstance(v, bool) or k in ("gambling", "unrestrictedWebAccess", "lootBox", "messagingAndChat",
                                          "parentalControls", "ageAssurance", "userGeneratedContent",
                                          "healthOrWellnessTopics", "seventeenPlus", "socialMedia",
                                          "socialMediaAgeRestricted"):
            want[k] = False
        elif v is None or isinstance(v, str):
            want[k] = "NONE"
    for attempt in range(6):
        r = S.patch(f"{API}/v1/ageRatingDeclarations/{decl['id']}", json={"data": {"type": "ageRatingDeclarations",
                    "id": decl["id"], "attributes": want}})
        if r.status_code == 200:
            print("Age rating set:", json.dumps(r.json()["data"]["attributes"]))
            break
        print("age rating try", attempt, r.status_code, r.text[:1500])
        # drop attributes the API rejects and retry
        bad = [e.get("source", {}).get("pointer", "").split("/")[-1] for e in r.json().get("errors", [])]
        bad = [b for b in bad if b in want]
        if not bad:
            ERRORS.append("age rating: " + r.text[:500]); break
        for b in bad:
            want.pop(b)

# ---------------------------------------------------------------- screenshots
if "shots" in STEPS:
    # display type -> folder: 6.9" (1320x2868, required) and 6.3" (1206x2622)
    SHOT_SETS = {"APP_IPHONE_67": "appstore-69", "APP_IPHONE_61": "appstore"}
    locs = {l["attributes"]["locale"]: l["id"] for l in get_all(f"/v1/appStoreVersions/{vid}/appStoreVersionLocalizations")}
    for dtype, folder in SHOT_SETS.items():
     for loc, lid in sorted(locs.items()):
        files = sorted(glob.glob(os.path.join(ROOT, "store-assets", folder, loc, "*.png")))
        if not files:
            print("no screenshots for", loc, dtype); continue
        sets = call("GET", f"/v1/appStoreVersionLocalizations/{lid}/appScreenshotSets")["data"]
        s = next((x for x in sets if x["attributes"]["screenshotDisplayType"] == dtype), None)
        if s is None:
            s = call("POST", "/v1/appScreenshotSets", {"data": {"type": "appScreenshotSets",
                     "attributes": {"screenshotDisplayType": dtype},
                     "relationships": {"appStoreVersionLocalization": {"data": {"type": "appStoreVersionLocalizations", "id": lid}}}}},
                     soft=True)
            if s is None:
                continue
            s = s["data"]
        for old in call("GET", f"/v1/appScreenshotSets/{s['id']}/appScreenshots")["data"]:
            call("DELETE", f"/v1/appScreenshots/{old['id']}", soft=True)
        for f in files:
            res = call("POST", "/v1/appScreenshots", {"data": {"type": "appScreenshots",
                       "attributes": {"fileName": os.path.basename(f), "fileSize": os.path.getsize(f)},
                       "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": s["id"]}}}}}, soft=True)
            if res is None:
                continue
            shot = res["data"]
            md5 = upload(shot["attributes"]["uploadOperations"], f)
            call("PATCH", f"/v1/appScreenshots/{shot['id']}", {"data": {"type": "appScreenshots", "id": shot["id"],
                 "attributes": {"uploaded": True, "sourceFileChecksum": md5}}}, soft=True)
        time.sleep(5)
        states = [x["attributes"].get("assetDeliveryState", {}) for x in
                  call("GET", f"/v1/appScreenshotSets/{s['id']}/appScreenshots")["data"]]
        print("Screenshots", loc, dtype, len(files), [st.get("state") for st in states],
              [e for st in states for e in (st.get("errors") or [])][:3])

# ---------------------------------------------------------------- in-app purchases
if "iaps" in STEPS:
    have = {p["attributes"]["productId"]: p for p in get_all(f"/v1/apps/{APP}/inAppPurchasesV2?limit=50")}
    territories = [t["id"] for t in get_all("/v1/territories?limit=200")]
    review_png = os.path.join(ROOT, "store-assets", "appstore", "review", "paywall.png")
    for pid, spec in META["iaps"].items():
        if pid in have:
            iap = have[pid]
        else:
            iap = call("POST", "/v2/inAppPurchases", {"data": {"type": "inAppPurchases", "attributes": {
                       "name": spec["ref"], "productId": pid, "inAppPurchaseType": "NON_CONSUMABLE",
                       "familySharable": False, "reviewNote": "Settings > WeightKit Pro. " + (
                           "Free (price tier 0): starts the 7-day trial of all Pro features (guideline 3.1.1)."
                           if "trial" in pid else "One-time unlock of all Pro features; removes ads.")},
                       "relationships": {"app": {"data": {"type": "apps", "id": APP}}}}}, soft=True)
            if iap is None:
                continue
            iap = iap["data"]
        iid = iap["id"]
        print("IAP", pid, iid, iap["attributes"].get("state"))
        call("PATCH", f"/v2/inAppPurchases/{iid}", {"data": {"type": "inAppPurchases", "id": iid, "attributes": {
             "reviewNote": "Settings > WeightKit Pro. " + ("Free (price tier 0): starts the 7-day trial of all Pro features (guideline 3.1.1)."
                           if "trial" in pid else "One-time unlock of all Pro features; removes ads.")}}}, soft=True)
        locs = {l["attributes"]["locale"]: l for l in get_all(f"/v2/inAppPurchases/{iid}/inAppPurchaseLocalizations")}
        for loc, t in spec["loc"].items():
            if loc in locs:
                call("PATCH", f"/v1/inAppPurchaseLocalizations/{locs[loc]['id']}", {"data": {"type": "inAppPurchaseLocalizations",
                     "id": locs[loc]["id"], "attributes": t}}, soft=True)
            else:
                call("POST", "/v1/inAppPurchaseLocalizations", {"data": {"type": "inAppPurchaseLocalizations",
                     "attributes": dict(t, locale=loc),
                     "relationships": {"inAppPurchaseV2": {"data": {"type": "inAppPurchases", "id": iid}}}}}, soft=True)
        print("  localizations:", ", ".join(spec["loc"]))
        # available in every country (the price is set in App Store Connect by the owner)
        call("POST", "/v1/inAppPurchaseAvailabilities", {"data": {"type": "inAppPurchaseAvailabilities",
             "attributes": {"availableInNewTerritories": True},
             "relationships": {"inAppPurchase": {"data": {"type": "inAppPurchases", "id": iid}},
                               "availableTerritories": {"data": [{"type": "territories", "id": t} for t in territories]}}}},
             ok=(200, 201, 409), soft=True)
        print("  availability: all", len(territories), "territories")
        if os.path.exists(review_png):
            cur = call("GET", f"/v2/inAppPurchases/{iid}/appStoreReviewScreenshot", ok=(200, 404), soft=True) or {}
            if cur.get("data"):
                call("DELETE", f"/v1/inAppPurchaseAppStoreReviewScreenshots/{cur['data']['id']}", soft=True)
            res = call("POST", "/v1/inAppPurchaseAppStoreReviewScreenshots", {"data": {
                       "type": "inAppPurchaseAppStoreReviewScreenshots",
                       "attributes": {"fileName": "paywall.png", "fileSize": os.path.getsize(review_png)},
                       "relationships": {"inAppPurchaseV2": {"data": {"type": "inAppPurchases", "id": iid}}}}}, soft=True)
            if res:
                sid = res["data"]["id"]
                md5 = upload(res["data"]["attributes"]["uploadOperations"], review_png)
                call("PATCH", f"/v1/inAppPurchaseAppStoreReviewScreenshots/{sid}", {"data": {
                     "type": "inAppPurchaseAppStoreReviewScreenshots", "id": sid,
                     "attributes": {"uploaded": True, "sourceFileChecksum": md5}}}, soft=True)
                print("  review screenshot uploaded")

# ---------------------------------------------------------------- build
if "build" in STEPS:
    builds = call("GET", f"/v1/builds?filter[app]={APP}&sort=-uploadedDate&limit=10&include=preReleaseVersion")
    for b in builds["data"]:
        a = b["attributes"]
        print("Build", a["version"], a["processingState"], "expired" if a.get("expired") else "", a.get("uploadedDate"))
    valid = [b for b in builds["data"] if b["attributes"]["processingState"] == "VALID" and not b["attributes"].get("expired")]
    if valid:
        b = valid[0]
        if b["attributes"].get("usesNonExemptEncryption") is None:
            call("PATCH", f"/v1/builds/{b['id']}", {"data": {"type": "builds", "id": b["id"],
                 "attributes": {"usesNonExemptEncryption": False}}}, soft=True)
        call("PATCH", f"/v1/appStoreVersions/{vid}/relationships/build", {"data": {"type": "builds", "id": b["id"]}}, soft=True)
        print("Attached build", b["attributes"]["version"], "to version", ver["attributes"]["versionString"])
    else:
        print("No VALID build yet")

# ---------------------------------------------------------------- app availability
if "availability" in STEPS:
    territories = [t["id"] for t in get_all("/v1/territories?limit=200")]
    r = call("POST", "/v2/appAvailabilities", {"data": {"type": "appAvailabilities",
             "attributes": {"availableInNewTerritories": True},
             "relationships": {"app": {"data": {"type": "apps", "id": APP}},
                               "territoryAvailabilities": {"data": [{"type": "territoryAvailabilities", "id": "${%s}" % t} for t in territories]}}},
             "included": [{"type": "territoryAvailabilities", "id": "${%s}" % t, "attributes": {"available": True},
                           "relationships": {"territory": {"data": {"type": "territories", "id": t}}}} for t in territories]},
             ok=(200, 201, 409), soft=True)
    print("App availability:", "all %d territories" % len(territories) if r is not None else "FAILED (set it in the website)")

print("\n==== %d problem(s)" % len(ERRORS))
for e in ERRORS:
    print(e[:600])
