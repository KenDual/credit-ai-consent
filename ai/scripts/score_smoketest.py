import json, urllib.request, sys

HEALTH_URL = "http://127.0.0.1:8001/health"
SCORE_URL  = "http://127.0.0.1:8001/score"

print("GET /health =>")
h = urllib.request.urlopen(HEALTH_URL)
print("STATUS:", h.status)
print("CT:", h.headers.get("content-type"))
print(h.read().decode("utf-8"))

payload = {
  "features": {
    "sms_count": 120,
    "contacts_count": 180,
    "email_overdue_ratio": 0.05,
    "sms_fin_ratio": 0.2,
    "ecom_cat_fashion_ratio": 0.1,
    "social_posts_sum": 30,
    "social_likes_sum": 200,
    "emp_formal": 1,
    "region_HCM": 1,
    "monthly_income_vnd": 15000000
  }
}

print("\nPOST /score =>")
req = urllib.request.Request(
    SCORE_URL,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type":"application/json"}
)
res = urllib.request.urlopen(req)
print("STATUS:", res.status)
print("CT:", res.headers.get("content-type"))
print(res.read().decode("utf-8"))
