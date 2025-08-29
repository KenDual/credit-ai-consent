"""
Generate synthetic multi-source credit data:
- Sources: sms, contacts, social, ecom, web, email (+ users master)
- Each record is tied by user_id (U0001...)
- Reproducible with --seed
- Windows-friendly paths; only needs numpy & pandas (already installed)
"""

import argparse
import os
from pathlib import Path
import random
from datetime import datetime, timedelta
import numpy as np
import pandas as pd

# -----------------------------
# Small helper pools (no extra deps)
# -----------------------------
FIRST_NAMES = ["An", "Bình", "Chi", "Dung", "Đạt", "Giang", "Hà", "Hải", "Hùng",
               "Khánh", "Lan", "Linh", "Minh", "Nam", "Ngân", "Ngọc", "Phương",
               "Quân", "Quang", "Sơn", "Thảo", "Thành", "Trang", "Trung", "Vy"]
LAST_NAMES  = ["Nguyễn", "Trần", "Lê", "Phạm", "Huỳnh", "Hoàng", "Phan", "Vũ",
               "Võ", "Đặng", "Bùi", "Đỗ", "Ngô", "Hồ", "Dương", "Đinh", "Trương"]
REGIONS     = ["HCM", "HN", "DN", "CT", "HP", "NT"]
EMP_TYPES   = ["formal", "contract", "gig", "student", "self"]
PLATFORMS   = ["facebook", "zalo", "tiktok", "instagram", "x"]
ECOM_CATS   = ["electronics", "fashion", "grocery", "beauty", "home", "gaming", "others"]
WEB_CATS    = ["news", "education", "banking", "shopping", "social", "entertainment",
               "gambling", "short_loan"]
EMAIL_SENDERS = ["bank.vn", "ecom.vn", "telco.vn", "utility.vn", "promo.vn", "work.vn"]

RISK_LEXICON = [
    "overdue", "late fee", "collection", "payday", "short loan", "nợ xấu",
    "vay nóng", "đòi nợ", "chậm đóng", "khoản vay", "tín dụng đen"
]

# -----------------------------
# Utility
# -----------------------------
def ensure_dirs(base: Path):
    for sub in ["sms", "contacts", "social", "ecom", "web", "email", "users"]:
        (base / sub).mkdir(parents=True, exist_ok=True)

def rand_name(rng: np.random.RandomState):
    return f"{rng.choice(LAST_NAMES)} {rng.choice(FIRST_NAMES)}"

def rand_phone(rng: np.random.RandomState):
    # VN mobile-ish
    return "0" + str(rng.randint(83,99)) + str(rng.randint(10000000, 99999999))

def rand_email(local_part, domain):
    return f"{local_part}@{domain}"

def daterange_utc(days:int):
    now = datetime.utcnow()
    start = now - timedelta(days=days)
    return start, now

def sample_time_between(rng, start: datetime, end: datetime):
    # uniform time between start & end
    delta = end - start
    seconds = rng.randint(0, int(delta.total_seconds()))
    return start + timedelta(seconds=int(seconds))

def sigmoid(x): return 1 / (1 + np.exp(-x))

# -----------------------------
# Core synthetic generator
# -----------------------------
def generate_users(n, seed):
    rng = np.random.RandomState(seed)
    users = []
    for i in range(n):
        uid = f"U{(i+1):04d}"
        age = rng.randint(20, 60)
        gender = rng.choice(["M","F"])
        emp = rng.choice(EMP_TYPES, p=[0.42, 0.15, 0.18, 0.1, 0.15])
        region = rng.choice(REGIONS)
        # income depends on emp type
        base_inc = {
            "formal": rng.normal(18e6, 4e6),
            "contract": rng.normal(14e6, 4e6),
            "gig": rng.normal(10e6, 3e6),
            "student": rng.normal(6e6, 2e6),
            "self": rng.normal(16e6, 5e6)
        }[emp]
        income = max(4e6, float(base_inc))
        # latent risk (0..1): lower with higher income/formal employment
        latent = sigmoid(
            0.4
            + (0.15 if emp in ["gig","student"] else -0.1 if emp == "formal" else 0.05)
            + (-(income - 12e6)/12e6)  # higher income -> lower risk
            + rng.normal(0, 0.35)
        )
        users.append({
            "user_id": uid,
            "full_name": rand_name(rng),
            "age": int(age),
            "gender": gender,
            "employment": emp,
            "region": region,
            "monthly_income_vnd": round(income),
            "latent_risk": float(np.clip(latent, 0, 1))
        })
    df = pd.DataFrame(users)
    return df

def generate_sms(df_users, days, rng):
    rows = []
    t0, t1 = daterange_utc(days)
    for r in df_users.itertuples(index=False):
        # avg daily msgs (in+out) ~ 15-45, more finance msgs if higher risk
        daily = int(rng.normal(30, 8))
        n = max(50, daily * days)
        finance_prob = 0.05 + 0.35 * r.latent_risk
        keyword_prob = 0.01 + 0.35 * r.latent_risk
        for _ in range(n):
            txt = ""
            is_fin = rng.rand() < finance_prob
            if is_fin and rng.rand() < keyword_prob:
                txt = rng.choice(RISK_LEXICON)
            else:
                # short harmless snippets
                txt = rng.choice([
                    "ok", "mai gặp", "call mình", "cảm ơn", "deal này ổn", "ship chiều",
                    "đi cf không", "check mail", "họp 3h nhé", "km free ship"
                ])
            rows.append({
                "user_id": r.user_id,
                "ts": sample_time_between(rng, t0, t1).isoformat(),
                "direction": rng.choice(["in","out"], p=[0.6,0.4]),
                "peer_phone": rand_phone(rng),
                "is_finance": int(is_fin),
                "text": txt
            })
    return pd.DataFrame(rows)

def generate_contacts(df_users, rng):
    rows = []
    for r in df_users.itertuples(index=False):
        n_contacts = int(max(50, rng.normal(220, 60)))
        risky_contacts = int(n_contacts * (0.03 + 0.12*r.latent_risk))
        for i in range(n_contacts):
            relation = rng.choice(["family","coworker","friend","service","unknown"],
                                  p=[0.12,0.28,0.45,0.1,0.05])
            tag = "none"
            if i < risky_contacts and rng.rand() < 0.7:
                tag = rng.choice(["loan_agent","short_loan_ad","debt_collector"])
            rows.append({
                "user_id": r.user_id,
                "name": rand_name(rng),
                "phone": rand_phone(rng),
                "relation": relation,
                "risk_tag": tag
            })
    return pd.DataFrame(rows)

def generate_social(df_users, days, rng):
    rows = []
    t0, t1 = daterange_utc(days)
    for r in df_users.itertuples(index=False):
        friends = int(max(50, rng.normal(400, 120)))
        # engagement lower with higher risk (proxy instability)
        active_days = int(np.clip(24 - 10*r.latent_risk + rng.normal(0,2), 3, 28))
        violations = int(max(0, rng.poisson(0.2 + 1.5*r.latent_risk)))
        for d in range(active_days):
            posts = max(0, int(rng.normal(1.2, 1)))
            likes = int(max(0, rng.normal(30, 15) * (1 - 0.4*r.latent_risk)))
            rows.append({
                "user_id": r.user_id,
                "ts": sample_time_between(rng, t0, t1).isoformat(),
                "platform": rng.choice(PLATFORMS),
                "posts": posts,
                "likes": likes,
                "friends": friends,
                "violations": violations,
                "joined_days": int(rng.randint(120, 2500))
            })
    return pd.DataFrame(rows)

def generate_ecom(df_users, days, rng):
    rows = []
    t0, t1 = daterange_utc(days)
    for r in df_users.itertuples(index=False):
        # purchase rate ~ income (but risky users more COD, smaller basket)
        orders = int(max(3, rng.normal(12 + r.monthly_income_vnd/4e6, 5)))
        for _ in range(orders):
            cat = rng.choice(ECOM_CATS)
            base = {
                "electronics": rng.normal(2.2e6, 1.2e6),
                "fashion": rng.normal(4.5e5, 2.5e5),
                "grocery": rng.normal(3.0e5, 1.2e5),
                "beauty": rng.normal(4.0e5, 2.0e5),
                "home": rng.normal(1.2e6, 6.0e5),
                "gaming": rng.normal(8.0e5, 5.0e5),
                "others": rng.normal(4.0e5, 3.0e5)
            }[cat]
            # risk -> smaller baskets, higher COD and returns
            amount = max(5e4, base * (1 - 0.25*r.latent_risk + rng.normal(0,0.2)))
            cod = int(rng.rand() < (0.25 + 0.5*r.latent_risk))
            returned = int(rng.rand() < (0.05 + 0.18*r.latent_risk))
            rows.append({
                "user_id": r.user_id,
                "ts": sample_time_between(rng, t0, t1).isoformat(),
                "category": cat,
                "amount_vnd": round(float(amount)),
                "cod": cod,
                "returned": returned
            })
    return pd.DataFrame(rows)

def generate_web(df_users, days, rng):
    rows = []
    t0, t1 = daterange_utc(days)
    for r in df_users.itertuples(index=False):
        visits = int(max(40, rng.normal(260, 90)))
        gamble_ratio = 0.01 + 0.12*r.latent_risk
        shortloan_ratio = 0.01 + 0.15*r.latent_risk
        for _ in range(visits):
            cat = rng.choice(WEB_CATS, p=np.array([
                0.15, 0.1, 0.08, 0.25, 0.18, 0.18, gamble_ratio, shortloan_ratio
            ]) / (0.15+0.1+0.08+0.25+0.18+0.18+gamble_ratio+shortloan_ratio))
            domain = {
                "news":"vnexpress.net", "education":"classroom.google.com",
                "banking":"hdbank.com.vn", "shopping":"shopee.vn",
                "social":"facebook.com", "entertainment":"youtube.com",
                "gambling":"bet-fast.xyz", "short_loan":"vay-nhanh.click"
            }[cat]
            rows.append({
                "user_id": r.user_id,
                "ts": sample_time_between(rng, t0, t1).isoformat(),
                "domain": domain,
                "category": cat
            })
    return pd.DataFrame(rows)

def generate_email(df_users, days, rng):
    rows = []
    t0, t1 = daterange_utc(days)
    for r in df_users.itertuples(index=False):
        mails = int(max(10, rng.normal(80, 25)))
        overdue_prob = 0.02 + 0.25*r.latent_risk
        for i in range(mails):
            sender = rng.choice(EMAIL_SENDERS)
            typ = rng.choice(["promo","transaction","statement","job","utility","unknown"],
                             p=[0.35,0.25,0.2,0.08,0.1,0.02])
            subject = rng.choice([
                "Ưu đãi mùa tết", "Xác nhận đơn hàng", "Sao kê tài khoản",
                "Nhắc thanh toán", "Thông báo quá hạn", "Biên lai thanh toán",
                "Mời phỏng vấn", "Khuyến mãi độc quyền", "Thông báo dịch vụ",
                "Cập nhật chính sách"
            ])
            # Inject risk keywords into some transactional/statement/utility
            body_kw = ""
            if typ in ["transaction","statement","utility"] and rng.rand() < overdue_prob:
                body_kw = rng.choice(RISK_LEXICON)
                subject = rng.choice(["Nhắc thanh toán", "Thông báo quá hạn", "Cảnh báo công nợ"])
            rows.append({
                "user_id": r.user_id,
                "ts": sample_time_between(rng, t0, t1).isoformat(),
                "from_domain": sender,
                "type": typ,
                "subject": subject,
                "has_risk_kw": int(body_kw != ""),
                "risk_kw": body_kw
            })
    return pd.DataFrame(rows)

def derive_label(df_users, sms, contacts, social, ecom, web, email):
    """Create a default label per user using a transparent, rule+noise mechanism,
    then map to probability via sigmoid, and final binary y ~ Bernoulli(p).
    This gives ground-truth for training & evaluation."""
    rng = np.random.RandomState(20250102)

    # features per user
    sms_fin = sms.groupby("user_id")["is_finance"].mean().rename("sms_fin_ratio").fillna(0)
    loan_visits = (web["category"] == "short_loan").groupby(web["user_id"]).mean().rename("web_loan_ratio").fillna(0)
    gamble_visits = (web["category"] == "gambling").groupby(web["user_id"]).mean().rename("web_gamble_ratio").fillna(0)
    cod_ratio = ecom.groupby("user_id")["cod"].mean().rename("ecom_cod_ratio").fillna(0)
    returns = ecom.groupby("user_id")["returned"].mean().rename("ecom_return_ratio").fillna(0)
    email_overdue = email.groupby("user_id")["has_risk_kw"].mean().rename("email_overdue_ratio").fillna(0)
    risky_contacts = (contacts["risk_tag"] != "none").groupby(contacts["user_id"]).mean().rename("risky_contacts_ratio").fillna(0)

    feat = pd.DataFrame(index=df_users["user_id"].values)
    for s in [sms_fin, loan_visits, gamble_visits, cod_ratio, returns, email_overdue, risky_contacts]:
        feat = feat.join(s, how="left")
    feat = feat.fillna(0).reset_index().rename(columns={"index":"user_id"})

    # latent + behavior → risk score
    u = df_users[["user_id","latent_risk","monthly_income_vnd"]].merge(feat, on="user_id", how="left").fillna(0)
    # linear combination + noise
    x = (
        1.2*u["latent_risk"]
        + 1.4*u["web_loan_ratio"]
        + 1.0*u["web_gamble_ratio"]
        + 1.1*u["email_overdue_ratio"]
        + 0.9*u["ecom_cod_ratio"]
        + 0.6*u["ecom_return_ratio"]
        + 0.8*u["sms_fin_ratio"]
        + 0.5*u["risky_contacts_ratio"]
        - 0.00000002*u["monthly_income_vnd"]     # higher income reduces risk slightly
        + rng.normal(0, 0.35, len(u))
    )
    p = sigmoid(x)
    y = rng.binomial(1, np.clip(p, 0.01, 0.99))
    out = df_users.copy()
    out["pd_true"] = p
    out["default_90d"] = y.astype(int)
    return out

# -----------------------------
# Main
# -----------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n-users", type=int, default=500)
    ap.add_argument("--days", type=int, default=90)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", type=str, default="./data/raw")
    args = ap.parse_args()

    base = Path(args.out)
    ensure_dirs(base)

    rng = np.random.RandomState(args.seed)

    print(f"[i] Generating users (n={args.n_users})...")
    users = generate_users(args.n_users, args.seed)

    print("[i] Generating sms...")
    sms = generate_sms(users, args.days, rng)

    print("[i] Generating contacts...")
    contacts = generate_contacts(users, rng)

    print("[i] Generating social...")
    social = generate_social(users, args.days, rng)

    print("[i] Generating e-commerce...")
    ecom = generate_ecom(users, args.days, rng)

    print("[i] Generating web browsing...")
    web = generate_web(users, args.days, rng)

    print("[i] Generating email...")
    email = generate_email(users, args.days, rng)

    print("[i] Deriving labels...")
    users_labeled = derive_label(users, sms, contacts, social, ecom, web, email)

    # Save CSVs
    print("[i] Writing CSV files...")
    users_labeled.to_csv(base / "users" / "users_master.csv", index=False, encoding="utf-8")
    sms.to_csv(base / "sms" / "messages.csv", index=False, encoding="utf-8")
    contacts.to_csv(base / "contacts" / "contacts.csv", index=False, encoding="utf-8")
    social.to_csv(base / "social" / "activity.csv", index=False, encoding="utf-8")
    ecom.to_csv(base / "ecom" / "orders.csv", index=False, encoding="utf-8")
    web.to_csv(base / "web" / "browsing.csv", index=False, encoding="utf-8")
    email.to_csv(base / "email" / "emails.csv", index=False, encoding="utf-8")

    # Also drop a small lexicon for RAG (optional)
    rag_dir = Path("./rag")
    rag_dir.mkdir(parents=True, exist_ok=True)
    (rag_dir / "lexicon.txt").write_text("\n".join(sorted(set(RISK_LEXICON))), encoding="utf-8")

    print("[✓] Done.")
    print(f"    Users: {len(users_labeled)} → {base/'users'/'users_master.csv'}")
    print(f"    SMS rows: {len(sms)} | Contacts rows: {len(contacts)} | Social rows: {len(social)}")
    print(f"    E-com rows: {len(ecom)} | Web rows: {len(web)} | Email rows: {len(email)}")

if __name__ == "__main__":
    main()
