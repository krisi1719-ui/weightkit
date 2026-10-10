"""App Store Connect helper: fills App Review details for the version being prepared,
queues ready in-app purchases and (optionally) submits the version for review.

Env: APP_STORE_CONNECT_ISSUER_ID, APP_STORE_CONNECT_KEY_IDENTIFIER, APP_STORE_CONNECT_PRIVATE_KEY,
     ASC_APP_ID, SUBMIT ("true" to submit), REVIEW_* fields below.
"""
import json, os, sys, time
import jwt, requests

API = "https://api.appstoreconnect.apple.com/v1"


def token():
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["APP_STORE_CONNECT_ISSUER_ID"], "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
        os.environ["APP_STORE_CONNECT_PRIVATE_KEY"], algorithm="ES256",
        headers={"kid": os.environ["APP_STORE_CONNECT_KEY_IDENTIFIER"], "typ": "JWT"})


S = requests.Session()
S.headers["Authorization"] = f"Bearer {token()}"


def call(method, path, body=None, ok=(200, 201, 204)):
    url = path if path.startswith("http") else API + path
    r = S.request(method, url, json=body)
    if r.status_code not in ok:
        print(f"!! {method} {path} -> {r.status_code}\n{r.text[:3000]}")
        r.raise_for_status()
    return r.json() if r.content else {}


app_id = os.environ["ASC_APP_ID"]
versions = call("GET", f"/apps/{app_id}/appStoreVersions?filter[platform]=IOS&limit=10")["data"]
editable = [v for v in versions if v["attributes"]["appStoreState"] in
            ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")]
if not editable:
    sys.exit("No editable iOS version: " + ", ".join(f'{v["attributes"]["versionString"]}={v["attributes"]["appStoreState"]}' for v in versions))
ver = editable[0]
vid = ver["id"]
print("Version", ver["attributes"]["versionString"], ver["attributes"]["appStoreState"], vid)

build = call("GET", f"/appStoreVersions/{vid}/build").get("data")
print("Build:", build and build["attributes"].get("version"), build and build["attributes"].get("processingState"))

attrs = {
    "contactFirstName": os.environ["REVIEW_FIRST_NAME"],
    "contactLastName": os.environ["REVIEW_LAST_NAME"],
    "contactPhone": os.environ["REVIEW_PHONE"],
    "contactEmail": os.environ["REVIEW_EMAIL"],
    "demoAccountRequired": False,
    "notes": os.environ["REVIEW_NOTES"],
}
detail = call("GET", f"/appStoreVersions/{vid}/appStoreReviewDetail", ok=(200, 404))
if detail.get("data"):
    did = detail["data"]["id"]
    call("PATCH", f"/appStoreReviewDetails/{did}", {"data": {"type": "appStoreReviewDetails", "id": did, "attributes": attrs}})
    print("Updated App Review details")
else:
    call("POST", "/appStoreReviewDetails", {"data": {"type": "appStoreReviewDetails", "attributes": attrs,
         "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}})
    print("Created App Review details")

iaps = call("GET", f"/apps/{app_id}/inAppPurchasesV2?limit=50")["data"]
for p in iaps:
    a = p["attributes"]
    print(f'IAP {a["productId"]}: {a["state"]}')

if os.environ.get("SUBMIT", "").lower() != "true":
    print("SUBMIT is not true - stopping before submission.")
    sys.exit(0)

if not build:
    sys.exit("No build attached to the version.")

for p in iaps:
    if p["attributes"]["state"] == "READY_TO_SUBMIT":
        call("POST", "/inAppPurchaseSubmissions", {"data": {"type": "inAppPurchaseSubmissions",
             "relationships": {"inAppPurchaseV2": {"data": {"type": "inAppPurchases", "id": p["id"]}}}}}, ok=(201, 409))
        print("Queued IAP for review:", p["attributes"]["productId"])

open_subs = call("GET", f"/reviewSubmissions?filter[app]={app_id}&filter[platform]=IOS&filter[state]=READY_FOR_REVIEW")["data"]
if open_subs:
    sub = open_subs[0]
else:
    sub = call("POST", "/reviewSubmissions", {"data": {"type": "reviewSubmissions", "attributes": {"platform": "IOS"},
               "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]
sid = sub["id"]
items = call("GET", f"/reviewSubmissions/{sid}/items")["data"]
if not items:
    call("POST", "/reviewSubmissionItems", {"data": {"type": "reviewSubmissionItems", "relationships": {
         "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sid}},
         "appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}})
call("PATCH", f"/reviewSubmissions/{sid}", {"data": {"type": "reviewSubmissions", "id": sid, "attributes": {"submitted": True}}})
print("Submitted for App Review:", sid)
for p in call("GET", f"/apps/{app_id}/inAppPurchasesV2?limit=50")["data"]:
    print(f'IAP {p["attributes"]["productId"]}: {p["attributes"]["state"]}')
